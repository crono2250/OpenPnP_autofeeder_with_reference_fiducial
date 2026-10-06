param(
    [Parameter(Mandatory = $true)][string]$OpenPnPCheckout
)

$ErrorActionPreference = 'Stop'
$checkout = (Resolve-Path -LiteralPath $OpenPnPCheckout).Path
if (-not (Test-Path -LiteralPath (Join-Path $checkout 'pom.xml'))) {
    throw 'The path does not contain an OpenPnP source checkout.'
}
Push-Location $checkout
try {
    git apply --check (Join-Path $PSScriptRoot 'register-feeder.patch')
    if ($LASTEXITCODE -ne 0) { throw 'The registration patch does not apply to this checkout.' }
    git apply (Join-Path $PSScriptRoot 'register-feeder.patch')
    if ($LASTEXITCODE -ne 0) { throw 'Could not apply the registration patch.' }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'overlay\src\main\java\org\openpnp\machine\reference\feeder\ThreePointAffine.java') `
        -Destination 'src\main\java\org\openpnp\machine\reference\feeder\ThreePointAffine.java'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'overlay\src\main\java\org\openpnp\machine\reference\feeder\FiducialCatalog.java') `
        -Destination 'src\main\java\org\openpnp\machine\reference\feeder\FiducialCatalog.java'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'overlay\src\main\java\org\openpnp\machine\reference\feeder\ReferenceFiducialAutoFeeder.java') `
        -Destination 'src\main\java\org\openpnp\machine\reference\feeder\ReferenceFiducialAutoFeeder.java'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'overlay\src\main\java\org\openpnp\machine\reference\feeder\wizards\ReferenceFiducialAutoFeederConfigurationWizard.java') `
        -Destination 'src\main\java\org\openpnp\machine\reference\feeder\wizards\ReferenceFiducialAutoFeederConfigurationWizard.java'
    Write-Host 'Applied the feeder source and registration patch. Run: mvn -DskipTests package'
} finally {
    Pop-Location
}
