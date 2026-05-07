param(
    [string]$Name = "Pixel_10",
    [string]$Package = "system-images;android-36.1;google_apis_playstore;x86_64",
    [string]$Device = "pixel_10",
    [string]$FallbackDevice = "pixel_9",
    [int]$RamMb = 4096,
    [int]$HeapMb = 512
)

$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
$avdmanager = "$sdk\cmdline-tools\latest\bin\avdmanager.bat"
$avdConfig = "$env:USERPROFILE\.android\avd\$Name.avd\config.ini"

& $sdkmanager $Package

$deviceList = & $avdmanager list device
$deviceToUse = $Device
if (-not ($deviceList | Select-String -SimpleMatch "`"$Device`"")) {
    Write-Warning "Device profile '$Device' is not available in this SDK. Falling back to '$FallbackDevice' while keeping AVD name '$Name'."
    $deviceToUse = $FallbackDevice
}

$existing = & $avdmanager list avd | Select-String "Name: $Name"
if (-not $existing) {
    "no" | & $avdmanager create avd --force --name $Name --package $Package --device $deviceToUse
}

if (-not (Test-Path $avdConfig)) {
    throw "AVD config was not created: $avdConfig"
}

$lines = Get-Content $avdConfig
$settings = @{
    "hw.gpu.enabled" = "yes"
    "hw.gpu.mode" = "host"
    "hw.ramSize" = "$RamMb"
    "vm.heapSize" = "$HeapMb"
    "Vulkan" = "on"
    "abi.type" = "x86_64"
    "tag.id" = "google_apis_playstore"
    "tag.ids" = "google_apis_playstore"
    "PlayStore.enabled" = "true"
    "image.sysdir.1" = "system-images\android-36.1\google_apis_playstore\x86_64\"
    "hw.device.manufacturer" = "Google"
    "hw.device.name" = "$deviceToUse"
}

foreach ($key in $settings.Keys) {
    $value = $settings[$key]
    if ($lines -match "^$([regex]::Escape($key))=") {
        $lines = $lines | ForEach-Object {
            if ($_ -match "^$([regex]::Escape($key))=") { "$key=$value" } else { $_ }
        }
    } else {
        $lines += "$key=$value"
    }
}

Set-Content -Path $avdConfig -Value $lines -Encoding ASCII
Write-Host "Created/updated AVD '$Name' with Android 36.1 x86_64 and host GPU/Vulkan settings."
Write-Host "Config: $avdConfig"
