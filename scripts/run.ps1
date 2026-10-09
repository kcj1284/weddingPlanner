param([string]$Profile = 'local')
. "$PSScriptRoot/env.ps1"
& $mavenCommand spring-boot:run "-Dspring-boot.run.profiles=$Profile"
exit $LASTEXITCODE
