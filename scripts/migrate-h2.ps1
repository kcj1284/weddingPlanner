$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
if (!(Test-Path 'data/cheongmo.mv.db')) { throw '기존 H2 데이터 파일이 없습니다.' }
if (!$env:DATABASE_URL -or !$env:DATABASE_PASSWORD) { throw '대상 PostgreSQL 환경 변수를 설정해 주세요.' }
New-Item -ItemType Directory -Force 'data/backups' | Out-Null
$backup = "data/backups/cheongmo-$(Get-Date -Format yyyyMMdd-HHmmss).mv.db"
Copy-Item -LiteralPath 'data/cheongmo.mv.db' -Destination $backup
$repository = Join-Path $env:USERPROFILE '.m2/repository'
$legacyJar = Join-Path $repository 'com/h2database/h2/2.4.240/h2-2.4.240.jar'
if (!(Test-Path $legacyJar)) {
    & $mavenCommand -q dependency:get '-Dartifact=com.h2database:h2:2.4.240'
    if ($LASTEXITCODE -ne 0) { throw '이관용 H2 드라이버 준비에 실패했습니다.' }
}
$postgresJar = Get-ChildItem (Join-Path $repository 'org/postgresql/postgresql') -Recurse -Filter 'postgresql-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$postgresJar) { throw '먼저 프로젝트를 빌드해 PostgreSQL JDBC 드라이버를 준비해 주세요.' }
& java --class-path "$legacyJar;$($postgresJar.FullName)" "$PSScriptRoot/MigrateLegacyH2.java"
if ($LASTEXITCODE -ne 0) { throw '이관에 실패했습니다. 대상 변경은 롤백되고 원본과 백업은 유지됩니다.' }
Write-Output "H2 백업 보존: $backup"
