$ErrorActionPreference = 'Stop'

$previousDbPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process')
$previousJwtSecret = [Environment]::GetEnvironmentVariable('JWT_SECRET', 'Process')
$privatePassword = Read-Host 'MySQL password for reminder_app (input hidden)' -AsSecureString

try {
    if ($privatePassword.Length -eq 0) {
        throw 'Enter the nonempty application account password.'
    }

    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
    if (-not $env:JWT_SECRET) {
        $keyBytes = New-Object byte[] 48
        $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($keyBytes); $env:JWT_SECRET = [Convert]::ToBase64String($keyBytes) }
        finally { $generator.Dispose() }
        Write-Host 'Using an ephemeral development JWT signing key. Tokens from this run expire on restart.'
    }

    Push-Location -LiteralPath $PSScriptRoot
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw 'The backend stopped with an error. Review the startup messages above.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable('DB_PASSWORD', $previousDbPassword, 'Process')
    [Environment]::SetEnvironmentVariable('JWT_SECRET', $previousJwtSecret, 'Process')
    $privatePassword.Dispose()
}
