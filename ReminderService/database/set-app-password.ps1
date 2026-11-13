# Verify root first, then set and verify only reminder_app's password.
$ErrorActionPreference = 'Stop'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysqlPath)) {
    throw 'The configured mysql.exe was not found.'
}

$rootPassword = $null
$firstPassword = $null
$secondPassword = $null
$rootPlain = $null
$escapedRoot = $null
$firstPlain = $null
$secondPlain = $null
$resetSql = $null
$clientOptions = $null
$privateDir = $null
$rootOptionsPath = $null
$resetPath = $null
$optionsPath = $null
$appVerified = $false

try {
    $privateDir = Join-Path $env:LOCALAPPDATA ('Temp\reminder-app-password-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $privateDir | Out-Null
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $userGrant = '*' + $currentSid + ':(OI)(CI)F'
    & icacls.exe $privateDir /inheritance:r /grant:r $userGrant '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not protect the temporary credential folder. Nothing was changed.'
    }

    $encoding = [System.Text.UTF8Encoding]::new($false)
    $rootOptionsPath = Join-Path $privateDir 'root-client.cnf'
    $resetPath = Join-Path $privateDir 'set-password.sql'
    $optionsPath = Join-Path $privateDir 'verify-client.cnf'

    $rootPassword = Read-Host 'STEP 1 - EXISTING ROOT password ONLY (hidden; nothing changes yet)' -AsSecureString
    $rootPlain = [System.Net.NetworkCredential]::new('', $rootPassword).Password
    $escapedRoot = $rootPlain.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t')
    $clientOptions = "[client]`r`npassword=`"$escapedRoot`"`r`n"
    [System.IO.File]::WriteAllText($rootOptionsPath, $clientOptions, $encoding)
    $rootPlain = $null
    $escapedRoot = $null
    $clientOptions = $null

    $rootCheck = & $mysqlPath "--defaults-file=$rootOptionsPath" '--protocol=TCP' '-h' '127.0.0.1' '-P' '3306' '-u' 'root' '--batch' '--skip-column-names' '--execute=SELECT CURRENT_USER()'
    if ($LASTEXITCODE -ne 0) {
        throw 'ROOT LOGIN FAILED. No application password was changed. Verify the saved root password before trying again.'
    }
    if (($rootCheck -join "`n").Trim() -cne 'root@localhost') {
        throw 'Unexpected administrator identity. No application password was changed.'
    }
    Write-Host 'ROOT LOGIN VERIFIED. Now choose the application password.'

    $firstPassword = Read-Host 'STEP 2 - Choose NEW reminder_app password (12+ characters; hidden)' -AsSecureString
    $secondPassword = Read-Host 'STEP 3 - Repeat NEW reminder_app password (hidden)' -AsSecureString
    $firstPlain = [System.Net.NetworkCredential]::new('', $firstPassword).Password
    $secondPlain = [System.Net.NetworkCredential]::new('', $secondPassword).Password
    if ($firstPlain -cne $secondPlain) {
        throw 'The application passwords do not match. No application password was changed.'
    }
    if ($firstPlain -notmatch '^[A-Za-z0-9!@#$%^&*_=+.,:?-]{12,128}$') {
        throw 'Use 12-128 characters: letters, numbers, and these symbols only: ! @ # $ % ^ & * _ = + . , : ? - . No application password was changed.'
    }

    $resetSql = "ALTER USER 'reminder_app'@'localhost' IDENTIFIED BY '$firstPlain';"
    [System.IO.File]::WriteAllText($resetPath, $resetSql, $encoding)
    $clientOptions = "[client]`r`npassword=`"$firstPlain`"`r`n"
    [System.IO.File]::WriteAllText($optionsPath, $clientOptions, $encoding)

    $sourceCommand = 'SOURCE ' + $resetPath.Replace('\', '/')
    & $mysqlPath "--defaults-file=$rootOptionsPath" '--protocol=TCP' '-h' '127.0.0.1' '-P' '3306' '-u' 'root' "--execute=$sourceCommand"
    if ($LASTEXITCODE -ne 0) {
        throw 'The administrator command failed. Review the MySQL error above; application verification did not run.'
    }

    $verification = & $mysqlPath "--defaults-file=$optionsPath" '--protocol=TCP' '-h' '127.0.0.1' '-P' '3306' '-u' 'reminder_app' '-D' 'smartreminder' '--batch' '--skip-column-names' '--execute=SELECT CURRENT_USER(), DATABASE()'
    if ($LASTEXITCODE -ne 0) {
        throw 'The password update was attempted, but application login verification failed. Share only the error.'
    }
    if (($verification -join "`n").Trim() -cne "reminder_app@localhost`tsmartreminder") {
        throw 'The verification returned an unexpected account or database; do not treat this run as successful.'
    }
    $appVerified = $true
}
finally {
    $rootPlain = $null
    $escapedRoot = $null
    $firstPlain = $null
    $secondPlain = $null
    $resetSql = $null
    $clientOptions = $null
    foreach ($securePassword in @($rootPassword, $firstPassword, $secondPassword)) {
        if ($null -ne $securePassword) { $securePassword.Dispose() }
    }

    $cleanupFailed = $false
    foreach ($credentialPath in @($rootOptionsPath, $resetPath, $optionsPath)) {
        if ($credentialPath -and (Test-Path -LiteralPath $credentialPath)) {
            try { Remove-Item -LiteralPath $credentialPath -ErrorAction Stop }
            catch { $cleanupFailed = $true }
        }
    }
    if (-not $cleanupFailed -and $privateDir -and (Test-Path -LiteralPath $privateDir)) {
        try { Remove-Item -LiteralPath $privateDir -ErrorAction Stop }
        catch { $cleanupFailed = $true }
    }
    if ($cleanupFailed) {
        throw ('Temporary credential cleanup was incomplete. Keep the folder private and ask for help: ' + $privateDir)
    }
}

if ($appVerified) {
    Write-Host ''
    Write-Host 'APPLICATION LOGIN VERIFIED'
    Write-Host 'Account: reminder_app@localhost'
    Write-Host 'Database: smartreminder'
    Write-Host 'Temporary credential files removed.'
    Write-Host 'Save the NEW application password privately as Local MySQL - reminder_app.'
}
