$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Engine = Get-ChildItem -Path $Root -Filter "echo-standalone-engine-*.jar" | Sort-Object Name | Select-Object -First 1
if (-not $Engine) { throw "ECHO engine JAR was not found in $Root" }
& java -Dfile.encoding=UTF-8 -jar $Engine.FullName --pack-root $Root --manifest pack.json --save-root (Join-Path $Root "saves") @args
exit $LASTEXITCODE
