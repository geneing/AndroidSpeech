param(
    [string]$Serial = "emulator-5554",
    [string]$ModelSource = "..\CrispASR",
    [switch]$PushModels,
    [switch]$IncludeFp16,
    [switch]$ForceModelCopy
)

$ErrorActionPreference = "Stop"

Push-Location "$PSScriptRoot\.."
try {
    .\gradlew.bat -PcrispAbi=x86_64 assembleDebug
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s $Serial install -r ".\app\build\outputs\apk\debug\app-debug.apk"
    if ($LASTEXITCODE -ne 0) {
        throw "adb install failed"
    }
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s $Serial shell cmd package resolve-activity com.crispasr.androidspeech
    if ($PushModels) {
        $pushArgs = @("-Serial", $Serial, "-Source", $ModelSource)
        if ($IncludeFp16) { $pushArgs += "-IncludeFp16" }
        if ($ForceModelCopy) { $pushArgs += "-Force" }
        & "$PSScriptRoot\push-models.ps1" @pushArgs
    }
} finally {
    Pop-Location
}
