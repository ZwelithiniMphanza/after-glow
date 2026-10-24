param([string]$Python='')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(-not $Python){$bundled=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';$Python=if(Test-Path $bundled){$bundled}else{'python'}}
if(-not(Test-Path .venv/Scripts/python.exe)){& $Python -m venv .venv;if($LASTEXITCODE -ne 0){throw 'Python 3.12 is required'}}
& ./.venv/Scripts/python.exe -m pip install torch==2.5.1 torchaudio==2.5.1 --index-url https://download.pytorch.org/whl/cpu
if($LASTEXITCODE -ne 0){throw 'PyTorch installation failed'}
& ./.venv/Scripts/python.exe -m pip install demucs==4.0.1 soundfile==0.13.1
if($LASTEXITCODE -ne 0){throw 'Demucs installation failed'}
