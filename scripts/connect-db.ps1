param(
    [ValidateSet('local', 'test')][string]$Database = 'local',
    [string]$Query,
    [string]$SqlFile
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$psqlPath = Join-Path $projectRoot '.tools/postgresql-17/bin/psql.exe'
$settingsPath = Join-Path $projectRoot 'data/local-postgres.json'
if (!(Test-Path -LiteralPath $psqlPath) -or !(Test-Path -LiteralPath $settingsPath)) {
    throw '로컬 DB를 먼저 scripts/start-postgres.ps1로 준비해 주세요.'
}
if ($Query -and $SqlFile) { throw 'Query와 SqlFile 중 하나만 지정해 주세요.' }
$databaseSettings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$databaseName = if ($Database -eq 'test') { 'cheongmo_test' } else { 'cheongmo' }
$previousPassword = $env:PGPASSWORD
$previousOptions = $env:PGOPTIONS
$previousEncoding = $env:PGCLIENTENCODING
try {
    $env:PGPASSWORD = if ($Database -eq 'test') { $databaseSettings.testPassword } else { $databaseSettings.appPassword }
    $env:PGOPTIONS = '-c default_transaction_read_only=on'
    $env:PGCLIENTENCODING = 'UTF8'
    $psqlArguments = @('-X', '-h', '127.0.0.1', '-p', [string]$databaseSettings.port, '-U', $databaseName, '-d', $databaseName, '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off')
    if ($Query) { $psqlArguments += @('-c', $Query) }
    if ($SqlFile) { $psqlArguments += @('-f', (Resolve-Path -LiteralPath $SqlFile).Path) }
    Write-Host "접속: 127.0.0.1:$($databaseSettings.port) / $databaseName (기본 읽기 전용)"
    & $psqlPath @psqlArguments
    if ($LASTEXITCODE -ne 0) { throw "DB 조회 실패 (종료 코드 $LASTEXITCODE)" }
} finally {
    $env:PGPASSWORD = $previousPassword
    $env:PGOPTIONS = $previousOptions
    $env:PGCLIENTENCODING = $previousEncoding
}
