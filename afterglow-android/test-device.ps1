param([string]$Runner='DeviceChecks')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$sdkRoot=Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$bt=Join-Path $sdkRoot 'build-tools/36.0.0'
$androidJar=Join-Path $sdkRoot 'platforms/android-36/android.jar'
function Run-Tool([string]$tool,[string[]]$arguments){ & $tool @arguments; if($LASTEXITCODE -ne 0){throw "$tool failed"} }
New-Item -ItemType Directory -Force build/test,build/test/classes,build/test/dex | Out-Null
Run-Tool "$bt/aapt2.exe" @('link','-o','build/test/base.apk','--manifest','tests/AndroidManifest.xml','-I',$androidJar)
$testSources=@((Get-ChildItem tests/src -Recurse -Filter '*.java').FullName)
Run-Tool 'javac' (@('--release','8','-encoding','UTF-8','-classpath',"$androidJar;build/classes",'-d','build/test/classes')+$testSources)
Run-Tool 'jar' @('cf','build/test/classes.jar','-C','build/test/classes','.')
Run-Tool "$bt/d8.bat" @('--lib',$androidJar,'--classpath','build/classes.jar','--min-api','28','--output','build/test/dex','build/test/classes.jar')
Run-Tool 'jar' @('uf','build/test/base.apk','-C','build/test/dex','classes.dex')
Run-Tool "$bt/zipalign.exe" @('-f','4','build/test/base.apk','build/test/aligned.apk')
Run-Tool "$bt/apksigner.bat" @('sign','--ks','.local/debug.keystore','--ks-pass','pass:android','--out','build/test/test.apk','build/test/aligned.apk')
Run-Tool 'adb' @('-s','083172524K003610','install','-r','build/test/test.apk')
Run-Tool 'adb' @('-s','083172524K003610','shell','run-as','com.afterglow.player','mkdir','-p','files')
# Durable backup stays on the device if the host or USB connection is interrupted.
Run-Tool 'adb' @('-s','083172524K003610','shell','am','force-stop','com.afterglow.player')
Run-Tool 'adb' @('-s','083172524K003610','shell','run-as','com.afterglow.player','sh','-c',"'test ! -e files/pre-test-player.xml && cp shared_prefs/player.xml files/pre-test-player.xml'")
try {
$result=& adb -s 083172524K003610 shell am instrument -w "com.afterglow.player.test/com.afterglow.player.$Runner"
$result | Tee-Object -FilePath "build/test/$Runner-result.txt" | Write-Output
if($LASTEXITCODE -ne 0 -or -not (($result -join "`n") -match 'INSTRUMENTATION_CODE: -1')){throw 'Device instrumentation failed; see build/test result file.'}

} finally {
    Run-Tool 'adb' @('-s','083172524K003610','shell','am','force-stop','com.afterglow.player')
    Run-Tool 'adb' @('-s','083172524K003610','shell','run-as','com.afterglow.player','cp','files/pre-test-player.xml','shared_prefs/player.xml')
    Run-Tool 'adb' @('-s','083172524K003610','shell','run-as','com.afterglow.player','rm','-f','shared_prefs/player.xml.bak')
    Run-Tool 'adb' @('-s','083172524K003610','shell','run-as','com.afterglow.player','rm','files/pre-test-player.xml')
    Run-Tool 'adb' @('-s','083172524K003610','shell','am','start','-n','com.afterglow.player/.MainActivity')
}
