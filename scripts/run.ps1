param([string]$Profile = 'local')
$workspacePgConfig = Join-Path (Split-Path $PSScriptRoot -Parent) 'data/local-postgres.json'
$useWorkspacePg = !$env:DATABASE_URL
if (Test-Path $workspacePgConfig) {
    $workspacePg = Get-Content -LiteralPath $workspacePgConfig -Raw | ConvertFrom-Json
    $useWorkspacePg = $useWorkspacePg -or ($env:DATABASE_URL -eq "jdbc:postgresql://127.0.0.1:$($workspacePg.port)/cheongmo")
}
if ($Profile -eq 'local' -and $useWorkspacePg) { & "$PSScriptRoot/start-postgres.ps1" }
. "$PSScriptRoot/env.ps1"
& $mavenCommand spring-boot:run "-Dspring-boot.run.profiles=$Profile"
exit $LASTEXITCODE
