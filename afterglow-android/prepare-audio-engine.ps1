$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
Set-Location $PSScriptRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force .deps,native/lib/arm64-v8a,assets | Out-Null
$version='0.18.1'
foreach($module in @('library','ffmpeg')) {
 $archive=".deps/$module-$version.aar"
 if(-not (Test-Path $archive)){Invoke-WebRequest "https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/$module/$version/$module-$version.aar" -OutFile $archive}
 $zip=[System.IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot $archive))
 try { foreach($entry in $zip.Entries) {
  if($entry.FullName -match '^jni/arm64-v8a/([^/]+\.so)$') { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $PSScriptRoot "native/lib/arm64-v8a/$($Matches[1])"),$true) }
  if($entry.FullName -match '^res/raw/ytdlp') { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $PSScriptRoot 'assets/yt-dlp'),$true) }
 }} finally {$zip.Dispose()}
}
$linkRows=@()
foreach($runtime in @('python','ffmpeg')) {
 $archive=[System.IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot "native/lib/arm64-v8a/lib$runtime.zip.so"))
 try {foreach($entry in $archive.Entries){
  if((($entry.ExternalAttributes -shr 16) -band 61440) -eq 40960){
   $reader=[IO.StreamReader]::new($entry.Open())
   try {$target=$reader.ReadToEnd()} finally {$reader.Dispose()}
   $linkRows+="$runtime`t$($entry.FullName)`t$target"
  }
 }} finally {$archive.Dispose()}
}
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'assets/runtime-links.tsv'),$linkRows,[Text.UTF8Encoding]::new($false))
$extractorVersion='2026.10.21'
$extractorHash='b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14'
if(-not (Test-Path '.deps/yt-dlp-current')){Invoke-WebRequest "https://github.com/yt-dlp/yt-dlp/releases/download/$extractorVersion/yt-dlp" -OutFile '.deps/yt-dlp-current'}
if((Get-FileHash '.deps/yt-dlp-current' -Algorithm SHA256).Hash.ToLowerInvariant() -ne $extractorHash){throw 'yt-dlp checksum mismatch'}
Copy-Item -LiteralPath '.deps/yt-dlp-current' -Destination 'assets/yt-dlp' -Force
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'assets/yt-dlp.version'),$extractorVersion,[Text.UTF8Encoding]::new($false))
Get-ChildItem native/lib/arm64-v8a | Select-Object Name,Length
Get-FileHash .deps/*.aar -Algorithm SHA256
