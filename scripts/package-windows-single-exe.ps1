param(
    [Parameter(Mandatory = $true)]
    [string] $InputZip,

    [Parameter(Mandatory = $true)]
    [string] $OutputExe,

    [Parameter(Mandatory = $true)]
    [string] $SourceFile,

    [string] $IconFile
)

$ErrorActionPreference = 'Stop'

$inputZipPath = (Resolve-Path -LiteralPath $InputZip).Path
$sourcePath = (Resolve-Path -LiteralPath $SourceFile).Path
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputExe)
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $csc)) {
    $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (-not (Test-Path -LiteralPath $csc)) {
    throw 'Could not find the .NET Framework C# compiler used to build the Windows single-file launcher.'
}

$frameworkDir = Split-Path -Parent $csc
$stubExe = Join-Path $outputDir 'bytescry-single-launcher-stub.exe'
$cscOptions = @('/nologo', '/target:winexe', '/optimize+', '/platform:anycpu', "/out:$stubExe")
if ($IconFile) {
    $iconPath = (Resolve-Path -LiteralPath $IconFile).Path
    $cscOptions += "/win32icon:$iconPath"
}
$references = @(
    (Join-Path $frameworkDir 'System.dll'),
    (Join-Path $frameworkDir 'System.Core.dll'),
    (Join-Path $frameworkDir 'System.Windows.Forms.dll'),
    (Join-Path $frameworkDir 'System.IO.Compression.dll'),
    (Join-Path $frameworkDir 'System.IO.Compression.FileSystem.dll')
)

& $csc $cscOptions ($references | ForEach-Object { "/reference:$_" }) $sourcePath
if ($LASTEXITCODE -ne 0) {
    throw "C# compiler failed with exit code $LASTEXITCODE."
}

Copy-Item -LiteralPath $stubExe -Destination $outputPath -Force

$marker = [Text.Encoding]::ASCII.GetBytes('BYTE-SCRY-SFX-ZIP-V1')
$zipBytes = [IO.File]::ReadAllBytes($inputZipPath)
$lengthBytes = [BitConverter]::GetBytes([Int64] $zipBytes.Length)

$stream = [IO.File]::Open($outputPath, [IO.FileMode]::Append, [IO.FileAccess]::Write)
try {
    $stream.Write($zipBytes, 0, $zipBytes.Length)
    $stream.Write($lengthBytes, 0, $lengthBytes.Length)
    $stream.Write($marker, 0, $marker.Length)
}
finally {
    $stream.Dispose()
}

Remove-Item -LiteralPath $stubExe -Force
Write-Host "Built Windows single-file executable: $outputPath"
