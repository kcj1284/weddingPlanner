$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$secretDirectory = Join-Path $projectDirectory 'data'
New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
$brevoSecureKey = Read-Host 'Brevo API Keys에서 생성한 API 키를 입력하세요 (SMTP 키와 다릅니다)' -AsSecureString
if ($brevoSecureKey.Length -eq 0) { throw 'API 키가 비어 있습니다.' }
$brevoSecureKey | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $secretDirectory 'brevo-api-key.dpapi')
Write-Host 'API 키를 현재 Windows 사용자 전용으로 암호화 저장했습니다. 채팅이나 Git에 키를 올리지 마세요.'
