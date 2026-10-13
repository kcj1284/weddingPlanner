$workspacePgConfig = Join-Path (Split-Path $PSScriptRoot -Parent) 'data/local-postgres.json'
$useWorkspacePg = !$env:TEST_DATABASE_URL
if (Test-Path $workspacePgConfig) {
    $workspacePg = Get-Content -LiteralPath $workspacePgConfig -Raw | ConvertFrom-Json
    $useWorkspacePg = $useWorkspacePg -or ($env:TEST_DATABASE_URL -eq "jdbc:postgresql://127.0.0.1:$($workspacePg.port)/cheongmo_test")
}
if ($useWorkspacePg) { & "$PSScriptRoot/start-postgres.ps1" }
. "$PSScriptRoot/env.ps1"
& $mavenCommand test
exit $LASTEXITCODE
