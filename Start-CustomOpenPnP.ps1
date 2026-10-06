param(
    [string]$InstallDir = 'C:\Program Files\OpenPnP'
)

$ErrorActionPreference = 'Stop'
$baseJar = Join-Path $InstallDir 'openpnp-gui-0.0.1-alpha-SNAPSHOT.jar'
$overlayJar = Join-Path $PSScriptRoot 'openpnp-fiducial-auto-feeder-50dcdce.jar'
$java = Join-Path $InstallDir 'jre\bin\java.exe'
foreach ($path in @($baseJar, $overlayJar, $java)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing file: $path" }
}

Add-Type -AssemblyName System.IO.Compression
$zip = [System.IO.Compression.ZipFile]::OpenRead($baseJar)
try {
    $entry = $zip.GetEntry('META-INF/MANIFEST.MF')
    if ($null -eq $entry) { throw 'OpenPnP JAR has no manifest.' }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $zip.Dispose() }
if ($manifest -notmatch 'Implementation-Version: [^\r\n]*\.50dcdce') {
    throw 'This overlay was compiled for OpenPnP 2.7 build 50dcdce. Build an overlay for this installation before launching.'
}

$classpath = "$overlayJar;$baseJar;$(Join-Path $InstallDir 'lib\*')"
Push-Location $InstallDir
try {
    & $java `
        '--add-opens=java.base/java.lang=ALL-UNNAMED' `
        '--add-opens=java.desktop/java.awt=ALL-UNNAMED' `
        '--add-opens=java.desktop/java.awt.color=ALL-UNNAMED' `
        '-cp' $classpath 'org.openpnp.Main'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
