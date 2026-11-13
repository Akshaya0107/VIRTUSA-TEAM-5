# Prepares a private MySQL init file. Does not stop MySQL or change any password.
$ErrorActionPreference = 'Stop'

$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [System.Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Open Windows PowerShell using Run as administrator, then run this script again.'
}

$recoveryDir = Join-Path $env:LOCALAPPDATA 'Temp\mysql-root-recovery'
$resetPath = Join-Path $recoveryDir 'reset.sql'
if (Test-Path -LiteralPath $resetPath) {
    throw 'A reset.sql recovery file already exists. Stop and ask for help before replacing it.'
}

$firstPassword = Read-Host 'Choose a NEW root password (12+ characters; input hidden)' -AsSecureString
$secondPassword = Read-Host 'Enter the NEW root password again (input hidden)' -AsSecureString
$firstPlain = $null
$secondPlain = $null
$resetSql = $null

try {
    $firstPlain = [System.Net.NetworkCredential]::new('', $firstPassword).Password
    $secondPlain = [System.Net.NetworkCredential]::new('', $secondPassword).Password
    if ($firstPlain -cne $secondPlain) {
        throw 'The two passwords do not match. No reset file was created.'
    }
    if ($firstPlain.Length -lt 12 -or $firstPlain -notmatch '^[\x21-\x7E]+$' -or
        $firstPlain.Contains("'") -or $firstPlain.Contains('\')) {
        throw 'For this recovery helper use 12+ printable ASCII characters without spaces, single quotes, or backslashes. No reset file was created.'
    }

    New-Item -ItemType Directory -Path $recoveryDir -Force | Out-Null
    $userGrant = '*' + $identity.User.Value + ':(OI)(CI)F'
    & icacls.exe $recoveryDir /inheritance:r /grant:r $userGrant '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not restrict the recovery folder permissions. No reset file was created.'
    }

    $resetSql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$firstPlain';"
    [System.IO.File]::WriteAllText($resetPath, $resetSql, [System.Text.UTF8Encoding]::new($false))
    Write-Host 'Reset file prepared. The MySQL password has NOT been changed yet.'
    Write-Host ('Recovery file: ' + $resetPath)
    Write-Host 'Keep the new root password in your password manager, separately from reminder_app.'
}
finally {
    $firstPlain = $null
    $secondPlain = $null
    $resetSql = $null
    $firstPassword.Dispose()
    $secondPassword.Dispose()
}
