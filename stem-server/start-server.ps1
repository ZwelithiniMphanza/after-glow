param([string]$Device='083172524K003610')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$python=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.venv/Scripts/python.exe'))
if(-not(Test-Path $python)){throw 'Run ./setup.ps1 first'}
$running=$false
if(Test-Path server.pid){$serverProcessId=[int](Get-Content server.pid);$existing=Get-CimInstance Win32_Process -Filter "ProcessId=$serverProcessId" -ErrorAction SilentlyContinue;$running=$existing -and $existing.CommandLine.Contains((Join-Path $PSScriptRoot 'server.py'))}
if(-not $running){$process=Start-Process -FilePath $python -ArgumentList @('-u',('"'+(Join-Path $PSScriptRoot 'server.py')+'"')) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput server.log -RedirectStandardError server-error.log -PassThru;$process.Id | Set-Content server.pid;Start-Sleep -Milliseconds 1500;if($process.HasExited){throw 'Server failed to start. See server-error.log.'}}
if(-not(Test-Path .token)){throw 'Server token was not created. See server-error.log.'}
& adb -s $Device reverse tcp:8765 tcp:8765
if($LASTEXITCODE -ne 0){throw 'Connect and authorize your Android device first'}
$config=@{url='http://127.0.0.1:8765';token=([IO.File]::ReadAllText((Join-Path $PSScriptRoot '.token')).Trim())} | ConvertTo-Json
$configPath=Join-Path $PSScriptRoot 'phone-config.json'
try {
 [IO.File]::WriteAllText($configPath,$config,[Text.UTF8Encoding]::new($false))
 & adb -s $Device push $configPath /data/local/tmp/afterglow-server.json | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Could not provision phone configuration'}
 & adb -s $Device shell run-as com.afterglow.player mkdir -p files
 & adb -s $Device shell run-as com.afterglow.player cp /data/local/tmp/afterglow-server.json files/stem-server.json
 if($LASTEXITCODE -ne 0){throw 'Install the development APK before connecting the server'}
} finally {
 if(Test-Path -LiteralPath $configPath){Remove-Item -LiteralPath $configPath -Force}
 & adb -s $Device shell rm -f /data/local/tmp/afterglow-server.json
}
Write-Output 'Stem server running. USB connection configured; open Studio and test the connection.'
