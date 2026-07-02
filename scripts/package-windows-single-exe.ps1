param(
    [Parameter(Mandatory = $true)]
    [string] $InputZip,

    [Parameter(Mandatory = $true)]
    [string] $OutputExe,

    [Parameter(Mandatory = $true)]
    [string] $SourceFile,

    [string] $IconFile,

    [string] $WatermarkId = ""
)

$ErrorActionPreference = 'Stop'

$inputZipPath = (Resolve-Path -LiteralPath $InputZip).Path
$sourcePath = (Resolve-Path -LiteralPath $SourceFile).Path
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputExe)
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$stubExe = Join-Path $outputDir 'bytescry-single-launcher-stub.exe'
$resourceFile = Join-Path $outputDir 'bytescry-single-launcher.rc'
$compiledResource = Join-Path $outputDir 'bytescry-single-launcher.res'
$objectFile = Join-Path $outputDir 'bytescry-single-launcher.obj'
$sourceExtension = [IO.Path]::GetExtension($sourcePath).ToLowerInvariant()
if ($sourceExtension -ne '.cpp') {
    throw "Windows single-file launcher source must be a native C++ file: $sourcePath"
}

function New-WatermarkBytes {
    param(
        [string] $Id
    )

    if ([string]::IsNullOrWhiteSpace($Id)) {
        return ,[byte[]]::new(0)
    }

    $watermark = [ordered]@{
        product = 'ByteScry'
        kind = 'windows-single-exe'
        id = $Id
        builtAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $payloadBytes = [Text.Encoding]::UTF8.GetBytes(($watermark | ConvertTo-Json -Compress))
    $markerBytes = [Text.Encoding]::ASCII.GetBytes('BYTE-SCRY-WATERMARK-V1')
    $lengthBytes = [BitConverter]::GetBytes([Int32] $payloadBytes.Length)

    $bytes = [byte[]]::new($markerBytes.Length + $lengthBytes.Length + $payloadBytes.Length)
    [Array]::Copy($markerBytes, 0, $bytes, 0, $markerBytes.Length)
    [Array]::Copy($lengthBytes, 0, $bytes, $markerBytes.Length, $lengthBytes.Length)
    [Array]::Copy($payloadBytes, 0, $bytes, $markerBytes.Length + $lengthBytes.Length, $payloadBytes.Length)
    return ,$bytes
}

$iconPath = $null
if ($IconFile) {
    $iconPath = (Resolve-Path -LiteralPath $IconFile).Path.Replace('\', '\\')
    Set-Content -LiteralPath $resourceFile -Encoding ASCII -Value "IDI_ICON1 ICON `"$iconPath`""
}

function Find-VcVars {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (Test-Path -LiteralPath $vswhere) {
        $installationPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        if ($LASTEXITCODE -eq 0 -and $installationPath) {
            $candidate = Join-Path $installationPath 'VC\Auxiliary\Build\vcvars64.bat'
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }
    $candidates = @(
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat",
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat",
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat",
        "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    return $null
}

$vcVars = Find-VcVars
if (-not $vcVars) {
    throw 'Could not find Visual Studio Build Tools with the MSVC C++ toolchain. Install "Desktop development with C++" to build bytescry.exe.'
}

$compileCommands = @()
if ($IconFile) {
    $compileCommands += "rc.exe /nologo /fo `"$compiledResource`" `"$resourceFile`""
}
$resourceArg = if ($IconFile) { "`"$compiledResource`"" } else { "" }
$compileCommands += "cl.exe /nologo /std:c++17 /EHsc /O2 /MT /DUNICODE /D_UNICODE /W3 /Fo`"$objectFile`" `"$sourcePath`" $resourceArg /Fe:`"$stubExe`" /link /SUBSYSTEM:WINDOWS shell32.lib ole32.lib oleaut32.lib uuid.lib user32.lib"

$cmd = "`"$vcVars`" >nul && " + ($compileCommands -join ' && ')
cmd.exe /d /s /c $cmd
if ($LASTEXITCODE -ne 0) {
    throw "MSVC compiler failed with exit code $LASTEXITCODE."
}

Copy-Item -LiteralPath $stubExe -Destination $outputPath -Force

$marker = [Text.Encoding]::ASCII.GetBytes('BYTE-SCRY-SFX-ZIP-V1')
$watermarkBytes = New-WatermarkBytes -Id $WatermarkId
$zipBytes = [IO.File]::ReadAllBytes($inputZipPath)
$lengthBytes = [BitConverter]::GetBytes([Int64] $zipBytes.Length)

$stream = [IO.File]::Open($outputPath, [IO.FileMode]::Append, [IO.FileAccess]::Write)
try {
    if ($watermarkBytes.Length -gt 0) {
        $stream.Write($watermarkBytes, 0, $watermarkBytes.Length)
    }
    $stream.Write($zipBytes, 0, $zipBytes.Length)
    $stream.Write($lengthBytes, 0, $lengthBytes.Length)
    $stream.Write($marker, 0, $marker.Length)
}
finally {
    $stream.Dispose()
}

Remove-Item -LiteralPath $stubExe -Force
if (Test-Path -LiteralPath $resourceFile) {
    Remove-Item -LiteralPath $resourceFile -Force
}
if (Test-Path -LiteralPath $compiledResource) {
    Remove-Item -LiteralPath $compiledResource -Force
}
if (Test-Path -LiteralPath $objectFile) {
    Remove-Item -LiteralPath $objectFile -Force
}
Write-Host "Built Windows single-file executable: $outputPath"
