param(
    [string]$SmtpHost = 'smtp-relay.brevo.com',
    [int]$SmtpPort = 587,
    [Parameter(Mandatory)][string]$SmtpUsername,
    [Parameter(Mandatory)][string]$FromEmail,
    [string]$PublicBaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'
$smtpRoot = Split-Path $PSScriptRoot -Parent
$smtpDataDirectory = Join-Path $smtpRoot 'data'
New-Item -ItemType Directory -Path $smtpDataDirectory -Force | Out-Null
$smtpSecureKey = Read-Host 'Brevo SMTP key (API key가 아닙니다)' -AsSecureString
$smtpSecureKey | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $smtpDataDirectory 'smtp-password.dpapi')
@{host=$SmtpHost;port=$SmtpPort;username=$SmtpUsername;from=$FromEmail;publicUrl=$PublicBaseUrl} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $smtpDataDirectory 'smtp-settings.json')
Write-Host 'SMTP 설정을 저장했습니다. 서비스를 재시작하면 적용됩니다.'
