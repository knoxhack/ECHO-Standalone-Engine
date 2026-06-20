$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) { $Python = Get-Command py -ErrorAction SilentlyContinue }
if (-not $Python) { throw "Python 3 is required to build the canonical package" }
if ($Python.Name -eq "py.exe") {
    & $Python.Source -3 (Join-Path $Root "scripts\build.py")
} else {
    & $Python.Source (Join-Path $Root "scripts\build.py")
}
exit $LASTEXITCODE
