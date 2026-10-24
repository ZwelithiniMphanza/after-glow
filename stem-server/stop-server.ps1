$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(Test-Path server.pid){$serverProcessId=[int](Get-Content server.pid);$python=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.venv/Scripts/python.exe'));$p=Get-CimInstance Win32_Process -Filter "ProcessId=$serverProcessId" -ErrorAction SilentlyContinue;if($p -and $p.CommandLine.Contains((Join-Path $PSScriptRoot 'server.py'))){Get-CimInstance Win32_Process -Filter "ParentProcessId=$serverProcessId" | Where-Object CommandLine -Like '*worker.py*' | ForEach-Object {Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue};Stop-Process -Id $serverProcessId};Remove-Item -LiteralPath (Join-Path $PSScriptRoot 'server.pid') -Force}
Write-Output 'Stem server stopped.'
