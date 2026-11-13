# Run in your own PowerShell window. Never paste the App Password into chat.
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$privateEnvPath = Join-Path $projectPath '.env'
if (-not (Test-Path -LiteralPath $privateEnvPath)) {
    throw 'Create the private .env first using tools/init-env.ps1.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is not available.' }

Write-Host 'Configure real Gmail SMTP. Existing database and JWT secrets will be preserved.'
Write-Host 'Use a Google App Password, not your normal Google password.'
$gmailAddress = (Read-Host 'Your Gmail sender address').Trim()
if ($gmailAddress -notmatch '^[a-zA-Z0-9._%+-]+@(gmail\.com|googlemail\.com)$') {
    throw 'Enter your personal Gmail address. Workspace/custom-domain setup requires its administrator settings.'
}
$appPasswordSecure = Read-Host 'Google App Password (hidden; spaces are allowed)' -AsSecureString
$passwordPointer = [IntPtr]::Zero
$appPasswordPlain = $null
$privateEnvText = $null
try {
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($appPasswordSecure)
    $appPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) -replace '\s', ''
    if ($appPasswordPlain -notmatch '^[a-zA-Z0-9]{16}$') {
        throw 'Expected a 16-character Google App Password. No configuration was changed.'
    }

    # Restrict access BEFORE writing the new credential. Never print file contents.
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $privateEnvPath /inheritance:r /grant:r ('*' + $currentSid + ':F') '*S-1-5-18:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict .env permissions. No credential was saved.' }

    $privateEnvText = [IO.File]::ReadAllText($privateEnvPath)
    $smtpValues = [ordered]@{
        SMTP_HOST = 'smtp.gmail.com'
        SMTP_PORT = '587'
        SMTP_USERNAME = $gmailAddress
        SMTP_PASSWORD = $appPasswordPlain
        SMTP_AUTH = 'true'
        SMTP_STARTTLS = 'true'
        MAIL_FROM = $gmailAddress
    }
    foreach ($settingName in $smtpValues.Keys) {
        # Compose shell variables take precedence over .env; refuse a silent mismatch.
        if (Test-Path -LiteralPath ('Env:' + $settingName)) {
            throw ('Remove the existing PowerShell environment override for ' + $settingName + ' and run again. No credential was saved.')
        }
        $settingPattern = '(?m)^' + [regex]::Escape($settingName) + '=.*$'
        $settingLine = $settingName + '=' + $smtpValues[$settingName]
        if ([regex]::IsMatch($privateEnvText, $settingPattern)) {
            $privateEnvText = [regex]::Replace($privateEnvText, $settingPattern, $settingLine)
        } else {
            $privateEnvText = $privateEnvText.TrimEnd() + "`r`n" + $settingLine + "`r`n"
        }
    }
    [IO.File]::WriteAllText($privateEnvPath, $privateEnvText, [Text.UTF8Encoding]::new($false))
    Write-Host 'Private Gmail configuration saved. Recreating the backend with the new settings...'
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    if ($null -ne $appPasswordSecure) { $appPasswordSecure.Dispose() }
    $appPasswordPlain = $null
    $privateEnvText = $null
    $smtpValues = $null
}

Push-Location -LiteralPath $projectPath
try {
    & docker compose up -d --no-deps --force-recreate --wait --wait-timeout 180 backend
    if ($LASTEXITCODE -ne 0) { throw 'Backend restart failed. Gmail settings remain saved; share the error with secrets removed.' }
} finally { Pop-Location }

Write-Host 'Backend is healthy and configured for Gmail. SMTP authentication/delivery is not yet verified.'
Write-Host 'Open http://localhost:8088 and create a NEW reminder to your own real email for 2 minutes from now.'
Write-Host 'Check Email History, then your Inbox and Spam. Previously captured messages are not resent.'
Write-Host 'Do not run the Mailpit fixture verification utilities against this real-email configuration.'
