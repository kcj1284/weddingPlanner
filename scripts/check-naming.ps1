$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$namingIssues = [Collections.Generic.List[string]]::new()
foreach ($javaFile in Get-ChildItem (Join-Path $projectRoot 'src') -Recurse -Filter '*.java') {
    $javaSource = Get-Content -LiteralPath $javaFile.FullName -Raw
    $publicType = [regex]::Match($javaSource, 'public\s+(?:(?:final|abstract)\s+)?(?:class|interface|enum|record)\s+(\w+)')
    if ($publicType.Success -and $publicType.Groups[1].Value -ne $javaFile.BaseName) {
        $namingIssues.Add("Java public 타입/파일 불일치: $($javaFile.Name)")
    }
    if ($javaFile.BaseName -cnotmatch '^[A-Z][A-Za-z0-9]*$') {
        $namingIssues.Add("Java 파일 PascalCase: $($javaFile.Name)")
    }
}
foreach ($webFile in Get-ChildItem (Join-Path $projectRoot 'src/main/resources/static') -Recurse -File) {
    if ($webFile.Extension -in '.js', '.css', '.html' -and $webFile.BaseName -cnotmatch '^[a-z0-9]+(?:-[a-z0-9]+)*$') {
        $namingIssues.Add("웹 파일 kebab-case: $($webFile.Name)")
    }
}
foreach ($pythonFile in Get-ChildItem (Join-Path $projectRoot 'scripts') -Filter '*.py') {
    if ($pythonFile.BaseName -cnotmatch '^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$') {
        $namingIssues.Add("Python 파일 snake_case: $($pythonFile.Name)")
    }
}
foreach ($sqlFile in Get-ChildItem (Join-Path $projectRoot 'scripts') -Filter '*.sql') {
    if ($sqlFile.BaseName -cnotmatch '^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$') {
        $namingIssues.Add("SQL 파일 snake_case: $($sqlFile.Name)")
    }
}
if ($namingIssues.Count) { throw ($namingIssues -join [Environment]::NewLine) }
Write-Output 'Naming 검사 통과: Java 타입/파일, 웹 파일, Python/SQL 파일. 변수 의미·외부 계약은 코드 리뷰로 확인합니다.'
