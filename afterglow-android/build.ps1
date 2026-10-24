$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$buildTools = Join-Path $sdkRoot 'build-tools/36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms/android-36/android.jar'
function Run-Tool([string]$tool, [string[]]$arguments) { & $tool @arguments; if ($LASTEXITCODE -ne 0) { throw "$tool failed with exit code $LASTEXITCODE" } }
foreach($generated in @('build/classes','build/gen','build/dex')) {
 $absolute=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot $generated))
 $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))+[IO.Path]::DirectorySeparatorChar
 if(-not $absolute.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Generated output path is outside build directory'}
 if(Test-Path -LiteralPath $absolute){Remove-Item -LiteralPath $absolute -Recurse -Force}
}
New-Item -ItemType Directory -Force build,build/gen,build/classes,build/dex,assets,.local | Out-Null
Run-Tool 'node' @('make-demo.cjs')
Run-Tool "$buildTools/aapt2.exe" @('compile','--dir','res','-o','build/resources.zip')
Run-Tool "$buildTools/aapt2.exe" @('link','-o','build/resources.apk','--manifest','AndroidManifest.xml','-I',$androidJar,'--java','build/gen','-A','assets','-0','wav','build/resources.zip')
$sourceFiles = @((Get-ChildItem src,build/gen -Recurse -Filter '*.java').FullName)
Run-Tool 'javac' (@('--release','8','-encoding','UTF-8','-classpath',$androidJar,'-d','build/classes') + $sourceFiles)
Run-Tool 'jar' @('cf','build/classes.jar','-C','build/classes','.')
Run-Tool "$buildTools/d8.bat" @('--lib',$androidJar,'--min-api','28','--output','build/dex','build/classes.jar')
Copy-Item -LiteralPath build/resources.apk -Destination build/unsigned.apk -Force
Run-Tool 'jar' @('uf','build/unsigned.apk','-C','build/dex','classes.dex')
if (-not (Test-Path 'native/lib/arm64-v8a/libpython.so')) { throw 'Run ./prepare-audio-engine.ps1 before building.' }
Run-Tool 'jar' @('uf','build/unsigned.apk','-C','native','lib')
Run-Tool "$buildTools/zipalign.exe" @('-f','4','build/unsigned.apk','build/aligned.apk')
if (-not (Test-Path .local/debug.keystore)) { Run-Tool 'keytool' @('-genkeypair','-keystore','.local/debug.keystore','-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Afterglow Debug,O=Development,C=US') }
Run-Tool "$buildTools/apksigner.bat" @('sign','--ks','.local/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out','build/afterglow-debug.apk','build/aligned.apk')
Run-Tool "$buildTools/apksigner.bat" @('verify','--verbose','build/afterglow-debug.apk')
Write-Output "APK: $PSScriptRoot/build/afterglow-debug.apk"
