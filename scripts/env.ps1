$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$brevoApiKeyFile = Join-Path $projectRoot 'data/brevo-api-key.dpapi'
if (!$env:BREVO_API_KEY -and (Test-Path -LiteralPath $brevoApiKeyFile)) {
    $brevoApiSecureKey = (Get-Content -LiteralPath $brevoApiKeyFile -Raw).Trim() | ConvertTo-SecureString
    $brevoApiCredential = [PSCredential]::new('brevo-api', $brevoApiSecureKey)
    $env:BREVO_API_KEY = $brevoApiCredential.GetNetworkCredential().Password
}
$smtpSettingsFile = Join-Path $projectRoot 'data/smtp-settings.json'
if (Test-Path -LiteralPath $smtpSettingsFile) {
    $smtpSettings = Get-Content -LiteralPath $smtpSettingsFile -Raw | ConvertFrom-Json
    foreach ($smtpSettingPair in @(@('SMTP_HOST',$smtpSettings.host),@('SMTP_PORT',[string]$smtpSettings.port),@('SMTP_USERNAME',$smtpSettings.username),@('MAIL_FROM',$smtpSettings.from),@('PUBLIC_BASE_URL',$smtpSettings.publicUrl))) {
        if (![Environment]::GetEnvironmentVariable($smtpSettingPair[0])) { [Environment]::SetEnvironmentVariable($smtpSettingPair[0],$smtpSettingPair[1],'Process') }
    }
    $smtpEncryptedKeyFile = Join-Path $projectRoot 'data/smtp-password.dpapi'
    if (!$env:SMTP_PASSWORD -and (Test-Path -LiteralPath $smtpEncryptedKeyFile)) {
        $smtpSecureValue = (Get-Content -LiteralPath $smtpEncryptedKeyFile -Raw).Trim() | ConvertTo-SecureString
        $smtpCredential = [PSCredential]::new('smtp',$smtpSecureValue)
        $env:SMTP_PASSWORD = $smtpCredential.GetNetworkCredential().Password
    }
}
$workspaceJdk = Get-ChildItem "$projectRoot/.tools" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($workspaceJdk) { $env:JAVA_HOME = $workspaceJdk.FullName; $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$workspaceMaven = Get-ChildItem "$projectRoot/.tools" -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue | Select-Object -First 1
$script:mavenCommand = if ($workspaceMaven) { "$($workspaceMaven.FullName)/bin/mvn.cmd" } else { 'mvn' }
Set-Location $projectRoot
$inviteCodeKeyFile = Join-Path $projectRoot 'data/invite-code-key.dpapi'
if (!$env:INVITE_CODE_ENCRYPTION_KEY) {
    if (!(Test-Path -LiteralPath $inviteCodeKeyFile)) {
        New-Item -ItemType Directory -Path (Join-Path $projectRoot 'data') -Force | Out-Null
        $inviteCodeKeyBytes = [byte[]]::new(32)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($inviteCodeKeyBytes)
        [Convert]::ToBase64String($inviteCodeKeyBytes) | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $inviteCodeKeyFile
    }
    $inviteCodeSecureKey = (Get-Content -LiteralPath $inviteCodeKeyFile -Raw).Trim() | ConvertTo-SecureString
    $inviteCodeCredential = [PSCredential]::new('invite-code', $inviteCodeSecureKey)
    $env:INVITE_CODE_ENCRYPTION_KEY = $inviteCodeCredential.GetNetworkCredential().Password
}
$kakaoKeyFile = Join-Path $projectRoot 'data/kakao-rest-api-key.dpapi'
foreach ($naverSearchSetting in @(@('NAVER_SEARCH_CLIENT_ID','naver-search-id.dpapi'),@('NAVER_SEARCH_CLIENT_SECRET','naver-search-secret.dpapi'))) {
    $naverSearchKeyFile = Join-Path $projectRoot ('data/' + $naverSearchSetting[1])
    if (![Environment]::GetEnvironmentVariable($naverSearchSetting[0]) -and (Test-Path -LiteralPath $naverSearchKeyFile)) {
        $naverSearchSecureValue = (Get-Content -LiteralPath $naverSearchKeyFile -Raw).Trim() | ConvertTo-SecureString
        $naverSearchCredential = [PSCredential]::new('naver-search', $naverSearchSecureValue)
        [Environment]::SetEnvironmentVariable($naverSearchSetting[0], $naverSearchCredential.GetNetworkCredential().Password, 'Process')
    }
}
if (!$env:KAKAO_REST_API_KEY -and (Test-Path -LiteralPath $kakaoKeyFile)) {
    $kakaoSecureKey = (Get-Content -LiteralPath $kakaoKeyFile -Raw).Trim() | ConvertTo-SecureString
    $kakaoCredential = [PSCredential]::new('kakao-local', $kakaoSecureKey)
    $env:KAKAO_REST_API_KEY = $kakaoCredential.GetNetworkCredential().Password
}
$pgConfiguration = Join-Path $projectRoot 'data/local-postgres.json'
if (Test-Path $pgConfiguration) {
    $pgSettings = Get-Content -LiteralPath $pgConfiguration -Raw | ConvertFrom-Json
    if (!$env:DATABASE_URL) {
        $env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:$($pgSettings.port)/cheongmo"
        $env:DATABASE_USERNAME = 'cheongmo'
        $env:DATABASE_PASSWORD = $pgSettings.appPassword
    }
    if (!$env:TEST_DATABASE_URL) {
        $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$($pgSettings.port)/cheongmo_test"
        $env:TEST_DATABASE_USERNAME = 'cheongmo_test'
        $env:TEST_DATABASE_PASSWORD = $pgSettings.testPassword
    }
}

$kakaoJavascriptKeyFile = Join-Path $projectRoot 'data/kakao-javascript-key.dpapi'
if (!$env:KAKAO_JAVASCRIPT_KEY -and (Test-Path -LiteralPath $kakaoJavascriptKeyFile)) {
    $kakaoJavascriptSecureValue = (Get-Content -LiteralPath $kakaoJavascriptKeyFile -Raw).Trim() | ConvertTo-SecureString
    $kakaoJavascriptCredential = [PSCredential]::new('kakao-javascript', $kakaoJavascriptSecureValue)
    $env:KAKAO_JAVASCRIPT_KEY = $kakaoJavascriptCredential.GetNetworkCredential().Password
}
