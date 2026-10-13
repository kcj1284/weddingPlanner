$ErrorActionPreference = 'Stop'
$naverSearchDataPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'data'
New-Item -ItemType Directory -Path $naverSearchDataPath -Force | Out-Null
foreach ($naverSearchKey in @(@('Client ID','naver-search-id.dpapi'),@('Client Secret','naver-search-secret.dpapi'))) {
    $naverSearchValue = Read-Host ("Naver Search " + $naverSearchKey[0]) -AsSecureString
    if($naverSearchValue.Length -eq 0){throw '빈 값은 저장할 수 없습니다.'}
    $naverSearchValue | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $naverSearchDataPath $naverSearchKey[1])
}
Write-Output '네이버 검색 설정을 암호화 저장했습니다. 서버 재시작 시 적용됩니다.'
