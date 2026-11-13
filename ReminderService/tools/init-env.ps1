$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectPath '.env'
if (Test-Path -LiteralPath $envPath) { throw '.env already exists. It was not changed.' }
function New-Secret {
    $bytes = New-Object byte[] 48
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes); [Convert]::ToBase64String($bytes) } finally { $generator.Dispose() }
}
$content = Get-Content -LiteralPath (Join-Path $projectPath '.env.example') -Raw
$content = $content.Replace('replace-with-generated-root-password', (New-Secret))
$content = $content.Replace('replace-with-generated-app-password', (New-Secret))
$content = $content.Replace('replace-with-generated-signing-secret', (New-Secret))
Set-Content -LiteralPath $envPath -Value $content -Encoding ASCII
$currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $envPath /inheritance:r /grant:r ('*' + $currentSid + ':F') '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '.env was created, but its permissions could not be restricted. Check permissions before continuing.' }
Write-Host 'Created private .env with random database and JWT secrets. Values were not displayed.'
Write-Host 'Default email goes to local Mailpit. Edit SMTP settings privately for Gmail.'
