param(
    [Parameter(Mandatory = $true)]
    [string] $OutputIco,

    [string] $OutputPng
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

function New-RoundedRectanglePath {
    param(
        [float] $X,
        [float] $Y,
        [float] $Width,
        [float] $Height,
        [float] $Radius
    )

    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Convert-BitmapToIconDib {
    param(
        [Drawing.Bitmap] $Bitmap,
        [int] $Size
    )

    $pixelBytes = $Size * $Size * 4
    $maskStride = [Math]::Ceiling($Size / 32.0) * 4
    $maskBytes = [int]($maskStride * $Size)
    $stream = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($stream)

    try {
        $writer.Write([UInt32]40)
        $writer.Write([Int32]$Size)
        $writer.Write([Int32]($Size * 2))
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]0)
        $writer.Write([UInt32]($pixelBytes + $maskBytes))
        $writer.Write([Int32]0)
        $writer.Write([Int32]0)
        $writer.Write([UInt32]0)
        $writer.Write([UInt32]0)

        for ($y = $Size - 1; $y -ge 0; $y--) {
            for ($x = 0; $x -lt $Size; $x++) {
                $color = $Bitmap.GetPixel($x, $y)
                $writer.Write([byte]$color.B)
                $writer.Write([byte]$color.G)
                $writer.Write([byte]$color.R)
                $writer.Write([byte]$color.A)
            }
        }

        for ($i = 0; $i -lt $maskBytes; $i++) {
            $writer.Write([byte]0)
        }

        return ,$stream.ToArray()
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function New-IconDib {
    param([int] $Size)

    $bitmap = [Drawing.Bitmap]::new($Size, $Size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.Clear([Drawing.Color]::Transparent)

    $scale = $Size / 256.0
    $bgPath = New-RoundedRectanglePath (16 * $scale) (16 * $scale) (224 * $scale) (224 * $scale) (44 * $scale)
    $bgBrush = [Drawing.Drawing2D.LinearGradientBrush]::new(
        [Drawing.PointF]::new(28 * $scale, 20 * $scale),
        [Drawing.PointF]::new(228 * $scale, 236 * $scale),
        [Drawing.Color]::FromArgb(255, 24, 36, 51),
        [Drawing.Color]::FromArgb(255, 11, 17, 24))
    $graphics.FillPath($bgBrush, $bgPath)

    $shadowBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(66, 0, 0, 0))
    $graphics.FillEllipse($shadowBrush, 74 * $scale, 191 * $scale, 108 * $scale, 30 * $scale)

    $outer = [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 42 * $scale),
        [Drawing.PointF]::new(188 * $scale, 77 * $scale),
        [Drawing.PointF]::new(188 * $scale, 155 * $scale),
        [Drawing.PointF]::new(128 * $scale, 214 * $scale),
        [Drawing.PointF]::new(68 * $scale, 155 * $scale),
        [Drawing.PointF]::new(68 * $scale, 77 * $scale))
    $outerPath = [Drawing.Drawing2D.GraphicsPath]::new()
    $outerPath.AddPolygon($outer)
    $baseBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 23, 36, 51))
    $graphics.FillPath($baseBrush, $outerPath)

    $topBrush = [Drawing.Drawing2D.LinearGradientBrush]::new(
        [Drawing.PointF]::new(70 * $scale, 44 * $scale),
        [Drawing.PointF]::new(188 * $scale, 214 * $scale),
        [Drawing.Color]::FromArgb(255, 94, 234, 212),
        [Drawing.Color]::FromArgb(255, 79, 156, 255))
    $graphics.FillPolygon($topBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 42 * $scale),
        [Drawing.PointF]::new(188 * $scale, 77 * $scale),
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(68 * $scale, 77 * $scale)))

    $leftBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(184, 62, 208, 189))
    $graphics.FillPolygon($leftBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(68 * $scale, 77 * $scale),
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(128 * $scale, 214 * $scale),
        [Drawing.PointF]::new(68 * $scale, 155 * $scale)))

    $rightBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(220, 55, 125, 176))
    $graphics.FillPolygon($rightBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(188 * $scale, 77 * $scale),
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(128 * $scale, 214 * $scale),
        [Drawing.PointF]::new(188 * $scale, 155 * $scale)))

    $rightTopBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(210, 49, 103, 151))
    $graphics.FillPolygon($rightTopBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(188 * $scale, 77 * $scale),
        [Drawing.PointF]::new(188 * $scale, 155 * $scale)))

    $leftTopBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(168, 101, 234, 213))
    $graphics.FillPolygon($leftTopBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(68 * $scale, 77 * $scale),
        [Drawing.PointF]::new(68 * $scale, 155 * $scale)))

    $innerDark = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(214, 13, 21, 32))
    $graphics.FillPolygon($innerDark, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(159 * $scale, 145 * $scale),
        [Drawing.PointF]::new(128 * $scale, 176 * $scale),
        [Drawing.PointF]::new(97 * $scale, 145 * $scale)))

    $coreBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 101, 234, 213))
    $graphics.FillPolygon($coreBrush, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(128 * $scale, 127 * $scale),
        [Drawing.PointF]::new(146 * $scale, 145 * $scale),
        [Drawing.PointF]::new(128 * $scale, 163 * $scale),
        [Drawing.PointF]::new(110 * $scale, 145 * $scale)))

    $nodeAmber = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 240, 199, 94))
    $nodeLight = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 217, 228, 239))
    $nodeBlue = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 90, 156, 255))
    $graphics.FillEllipse($nodeAmber, 85 * $scale, 88 * $scale, 14 * $scale, 14 * $scale)
    $graphics.FillEllipse($nodeLight, 157 * $scale, 88 * $scale, 14 * $scale, 14 * $scale)
    $graphics.FillEllipse($nodeBlue, 121 * $scale, 183 * $scale, 14 * $scale, 14 * $scale)

    $linkPen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(70, 216, 226, 236), [Math]::Max(1.0, 5 * $scale))
    $linkPen.StartCap = [Drawing.Drawing2D.LineCap]::Round
    $linkPen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $linkPen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawLines($linkPen, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(92 * $scale, 95 * $scale),
        [Drawing.PointF]::new(128 * $scale, 115 * $scale),
        [Drawing.PointF]::new(164 * $scale, 95 * $scale)))
    $graphics.DrawLine($linkPen, 128 * $scale, 115 * $scale, 128 * $scale, 190 * $scale)

    $shinePen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(46, 255, 255, 255), [Math]::Max(1.0, 7 * $scale))
    $shinePen.StartCap = [Drawing.Drawing2D.LineCap]::Round
    $shinePen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $shinePen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawLines($shinePen, [Drawing.PointF[]]@(
        [Drawing.PointF]::new(87 * $scale, 80 * $scale),
        [Drawing.PointF]::new(128 * $scale, 56 * $scale),
        [Drawing.PointF]::new(169 * $scale, 80 * $scale)))

    $outlinePen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(255, 49, 66, 86), [Math]::Max(1.0, 6 * $scale))
    $outlinePen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawPath($outlinePen, $outerPath)

    $bytes = Convert-BitmapToIconDib $bitmap $Size

    $outlinePen.Dispose()
    $shinePen.Dispose()
    $linkPen.Dispose()
    $nodeBlue.Dispose()
    $nodeLight.Dispose()
    $nodeAmber.Dispose()
    $coreBrush.Dispose()
    $innerDark.Dispose()
    $leftTopBrush.Dispose()
    $rightTopBrush.Dispose()
    $rightBrush.Dispose()
    $leftBrush.Dispose()
    $topBrush.Dispose()
    $baseBrush.Dispose()
    $outerPath.Dispose()
    $shadowBrush.Dispose()
    $bgBrush.Dispose()
    $bgPath.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()

    return ,$bytes
}

$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputIco)
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$images = @($sizes | ForEach-Object { [pscustomobject]@{ Size = $_; Bytes = [byte[]](New-IconDib $_) } })
$headerSize = 6 + (16 * $images.Count)
$offset = $headerSize

$stream = [IO.File]::Open($outputPath, [IO.FileMode]::Create, [IO.FileAccess]::Write)
$writer = [IO.BinaryWriter]::new($stream)
try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]$images.Count)

    foreach ($image in $images) {
        $writer.Write([byte]($(if ($image.Size -eq 256) { 0 } else { $image.Size })))
        $writer.Write([byte]($(if ($image.Size -eq 256) { 0 } else { $image.Size })))
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$image.Bytes.Length)
        $writer.Write([UInt32]$offset)
        $offset += $image.Bytes.Length
    }

    foreach ($image in $images) {
        $writer.Write([byte[]]$image.Bytes)
    }
}
finally {
    $writer.Dispose()
    $stream.Dispose()
}

Write-Host "Generated Windows icon: $outputPath"

if ($OutputPng) {
    $pngPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPng)
    $pngDir = Split-Path -Parent $pngPath
    New-Item -ItemType Directory -Force -Path $pngDir | Out-Null
    $pngBitmap = [Drawing.Bitmap]::new(256, 256, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $pngBytes = [byte[]](New-IconDib 256)
    $dibOffset = 40
    for ($y = 255; $y -ge 0; $y--) {
        for ($x = 0; $x -lt 256; $x++) {
            $index = $dibOffset + ((255 - $y) * 256 + $x) * 4
            $color = [Drawing.Color]::FromArgb($pngBytes[$index + 3], $pngBytes[$index + 2], $pngBytes[$index + 1], $pngBytes[$index])
            $pngBitmap.SetPixel($x, $y, $color)
        }
    }
    $pngBitmap.Save($pngPath, [Drawing.Imaging.ImageFormat]::Png)
    $pngBitmap.Dispose()
    Write-Host "Generated JavaFX icon: $pngPath"
}
