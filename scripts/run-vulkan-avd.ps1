param(
    [string]$Name = "Pixel_10",
    [switch]$NoSnapshot
)

$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$emulator = "$sdk\emulator\emulator.exe"

$args = @("-avd", $Name, "-gpu", "host", "-feature", "Vulkan")
if ($NoSnapshot) {
    $args += @("-no-snapshot-load", "-no-snapshot-save")
}

Start-Process -FilePath $emulator -ArgumentList $args -WindowStyle Hidden
Write-Host "Started AVD '$Name' with host GPU/Vulkan flags."
