$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Engine = Join-Path $Root "dist\echo-standalone-engine-2.0.0-beta.2.jar"
if (!(Test-Path -LiteralPath $Engine)) {
  throw "Missing engine JAR: $Engine. Run scripts\build.ps1 first."
}
& java -Dfile.encoding=UTF-8 -jar $Engine --pack-root (Join-Path $Root "dist") --manifest pack.json --save-root (Join-Path $Root "saves") @args
exit $LASTEXITCODE
