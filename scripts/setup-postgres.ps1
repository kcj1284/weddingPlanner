$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $projectRoot '.tools/postgresql-17'
if (Test-Path "$runtimeRoot/bin/pg_ctl.exe") { Write-Output 'PostgreSQL 실행 파일이 이미 준비되어 있습니다.'; return }
$archive = Join-Path $projectRoot '.tools/postgresql-17-windows.zip'
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.tools') | Out-Null
if (!(Test-Path $archive)) {
    Write-Output '공식 EDB PostgreSQL 17.11 Windows 바이너리를 다운로드합니다.'
    Invoke-WebRequest 'https://sbp.enterprisedb.com/getfile.jsp?fileid=1260616' -OutFile $archive -UseBasicParsing
}
$zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -notmatch '^pgsql/(bin|lib|share)/') { continue }
        $relative = $entry.FullName.Substring('pgsql/'.Length)
        $destination = [IO.Path]::GetFullPath((Join-Path $runtimeRoot $relative))
        if (!$destination.StartsWith([IO.Path]::GetFullPath($runtimeRoot)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw '안전하지 않은 압축 경로입니다.' }
        if ($entry.FullName.EndsWith('/')) { New-Item -ItemType Directory -Force $destination | Out-Null }
        else {
            New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$destination,$true)
        }
    }
} finally { $zip.Dispose() }
& "$runtimeRoot/bin/postgres.exe" --version
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 실행 파일 확인에 실패했습니다.' }
