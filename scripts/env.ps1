$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$workspaceJdk = Get-ChildItem "$projectRoot/.tools" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($workspaceJdk) { $env:JAVA_HOME = $workspaceJdk.FullName; $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$workspaceMaven = Get-ChildItem "$projectRoot/.tools" -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue | Select-Object -First 1
$script:mavenCommand = if ($workspaceMaven) { "$($workspaceMaven.FullName)/bin/mvn.cmd" } else { 'mvn' }
Set-Location $projectRoot
