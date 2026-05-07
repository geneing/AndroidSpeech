param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$serialProp = ""
if ($Serial) { $serialProp = "-Pandroid.testInstrumentationRunnerArguments.serial=$Serial" }

Push-Location "$PSScriptRoot\.."
try {
    .\gradlew.bat connectedDebugAndroidTest $serialProp
} finally {
    Pop-Location
}
