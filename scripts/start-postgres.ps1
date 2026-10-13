param([int]$Port = 5432)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$pgBin = Join-Path $projectRoot '.tools/postgresql-17/bin'
$cluster = Join-Path $projectRoot 'data/postgresql'
$configurationFile = Join-Path $projectRoot 'data/local-postgres.json'
$logFile = Join-Path $projectRoot 'artifacts/postgresql.log'
if (!(Test-Path "$pgBin/pg_ctl.exe")) { & "$PSScriptRoot/setup-postgres.ps1" }
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'data'),(Join-Path $projectRoot 'artifacts') | Out-Null
function Protect-LocalFile([string]$Path) {
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true,$false)
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl.SetAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','Allow'))
    Set-Acl -LiteralPath $Path -AclObject $acl
}
if (Test-Path $configurationFile) {
    $settings = Get-Content -LiteralPath $configurationFile -Raw | ConvertFrom-Json
    $Port = [int]$settings.port
} else {
    if (Test-Path "$cluster/PG_VERSION") { throw '기존 DB의 local-postgres.json이 없습니다. 기존 인증 설정을 복구해 주세요.' }
    $settings = [pscustomobject]@{port=$Port;adminPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32));appPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32));testPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))}
    $settings | ConvertTo-Json | Set-Content -LiteralPath $configurationFile -Encoding utf8
    Protect-LocalFile $configurationFile
}
if (!(Test-Path "$cluster/PG_VERSION")) {
    $passwordFile = Join-Path $projectRoot 'data/postgresql-init.password'
    $settings.adminPassword | Set-Content -LiteralPath $passwordFile -Encoding utf8
    Protect-LocalFile $passwordFile
    try {
        & "$pgBin/initdb.exe" -D $cluster -U postgres --pwfile=$passwordFile --auth=scram-sha-256 --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 초기화에 실패했습니다.' }
    } finally { Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue }
}
& "$pgBin/pg_ctl.exe" status -D $cluster *> $null
if ($LASTEXITCODE -ne 0) {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "포트 $Port 가 사용 중입니다. 별도 포트로 최초 초기화하거나 기존 DB 연결 정보를 설정해 주세요." }
    $process = Start-Process -FilePath "$pgBin/pg_ctl.exe" -ArgumentList @('start','-D',"`"$cluster`"",'-l',"`"$logFile`"",'-o',"`"-h 127.0.0.1 -p $Port`"",'-w') -WindowStyle Hidden -PassThru
    if (!$process.WaitForExit(60000)) { throw 'PostgreSQL 시작 응답이 지연되고 있습니다. 로그를 확인해 주세요.' }
    if ($process.ExitCode -ne 0) { throw 'PostgreSQL 시작에 실패했습니다. artifacts/postgresql.log를 확인해 주세요.' }
}
$previousPassword = $env:PGPASSWORD
$env:PGPASSWORD = $settings.adminPassword
try {
    foreach ($name in @('cheongmo','cheongmo_test')) {
        $exists = & "$pgBin/psql.exe" -X -h 127.0.0.1 -p $Port -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='$name'"
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 관리자 인증에 실패했습니다.' }
        if (!$exists) {
            $password = if ($name -eq 'cheongmo') {$settings.appPassword} else {$settings.testPassword}
            $sqlFile = Join-Path $projectRoot 'data/postgresql-role.sql'
            "CREATE ROLE $name LOGIN PASSWORD '$password';" | Set-Content -LiteralPath $sqlFile -Encoding utf8
            Protect-LocalFile $sqlFile
            try { & "$pgBin/psql.exe" -X -h 127.0.0.1 -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -f $sqlFile | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'DB 사용자 생성에 실패했습니다.' } }
            finally { Remove-Item -LiteralPath $sqlFile -ErrorAction SilentlyContinue }
        }
        $exists = & "$pgBin/psql.exe" -X -h 127.0.0.1 -p $Port -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$name'"
        if ($LASTEXITCODE -ne 0) { throw 'DB 확인에 실패했습니다.' }
        if (!$exists) { & "$pgBin/createdb.exe" -h 127.0.0.1 -p $Port -U postgres -O $name $name; if ($LASTEXITCODE -ne 0) { throw 'DB 생성에 실패했습니다.' } }
    }
} finally { $env:PGPASSWORD = $previousPassword }
Write-Output "PostgreSQL 준비 완료: 127.0.0.1:$Port (앱 DB와 테스트 DB 분리)"
