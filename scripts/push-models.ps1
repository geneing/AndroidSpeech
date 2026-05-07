param(
    [string]$Serial = "",
    [string]$Source = "..\CrispASR",
    [string]$Package = "com.crispasr.androidspeech",
    [ValidateSet("SharedMedia", "AppExternal")]
    [string]$TargetKind = "SharedMedia",
    [switch]$ResetModelDirectory,
    [switch]$IncludeFp16,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serialArgs = @()
if ($Serial) { $serialArgs = @("-s", $Serial) }

$target = "/sdcard/Android/media/$Package/CrispASR"
if ($TargetKind -eq "AppExternal") {
    $target = "/sdcard/Android/data/$Package/files/CrispASR"
}

if ($ResetModelDirectory) {
    Write-Host "Resetting $target"
    & $adb @serialArgs shell am force-stop $Package
    & $adb @serialArgs shell rm -rf $target
}

if ($TargetKind -eq "AppExternal") {
    & $adb @serialArgs shell am start -n "$Package/.ui.MainActivity" | Out-Host
    Start-Sleep -Seconds 2
} else {
    & $adb @serialArgs shell mkdir -p $target
}

$dirInfo = & $adb @serialArgs shell ls -ld $target 2>$null
if (-not $dirInfo) {
    throw "Could not create or access $target."
}
Write-Host $dirInfo

Get-ChildItem -Path $Source -Filter *.gguf -File | ForEach-Object {
    $name = $_.Name
    $lower = $name.ToLowerInvariant()
    if (-not $IncludeFp16 -and ($lower.Contains("f16") -or $lower.Contains("fp16"))) {
        Write-Host "Skipping FP16 model $name (pass -IncludeFp16 to copy it)"
    } else {
        $skip = $false
        if (-not $Force) {
            $remotePath = "$target/$name"
            $remoteSizeOutput = & $adb @serialArgs shell "if [ -f '$remotePath' ]; then stat -c '%s' '$remotePath'; fi"
            $remoteSize = $remoteSizeOutput | Select-Object -First 1
            if ($remoteSize -and $remoteSize.Trim() -eq $_.Length.ToString()) {
                $skip = $true
            }
        }
        if ($skip) {
            Write-Host "Already present $name"
        } else {
            Write-Host "Pushing $($_.Name)"
            & $adb @serialArgs push $_.FullName "$target/$($_.Name)"
            if ($LASTEXITCODE -ne 0) {
                throw "adb push failed for $($_.FullName)"
            }
        }
    }
}
