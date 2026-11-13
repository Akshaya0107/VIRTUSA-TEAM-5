$ErrorActionPreference = 'Stop'
$backendPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
Push-Location -LiteralPath $backendPath
try {
    & .\mvnw.cmd '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.profiles=demo' '-Dspring-boot.run.arguments=--spring.config.additional-location=file:./src/test/resources/application-demo.properties' 'spring-boot:run'
    if ($LASTEXITCODE -ne 0) { throw 'Demo backend stopped with an error.' }
} finally { Pop-Location }
