param(
  [string]$OutPath = "src/main/resources/assets/grappling-armor/icon.png"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$resolvedOut = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutPath))
$outDir = Split-Path -Parent $resolvedOut
if (-not (Test-Path $outDir)) {
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

$bitmap = New-Object System.Drawing.Bitmap 128, 128, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

function New-Brush([int]$a, [int]$r, [int]$g, [int]$b) {
  return New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($a, $r, $g, $b))
}

function New-Pen([int]$a, [int]$r, [int]$g, [int]$b, [float]$w) {
  $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a, $r, $g, $b)), $w
  $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
  $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
  $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
  return $pen
}

try {
  $backgroundRect = New-Object System.Drawing.Rectangle 0, 0, 128, 128
  $background = New-Object System.Drawing.Drawing2D.LinearGradientBrush `
    $backgroundRect,
    ([System.Drawing.Color]::FromArgb(255, 8, 13, 24)),
    ([System.Drawing.Color]::FromArgb(255, 13, 58, 78)),
    45
  $graphics.FillRectangle($background, $backgroundRect)

  $rimPen = New-Pen 255 25 210 255 3
  $shadowPen = New-Pen 150 0 0 0 8
  $metalPen = New-Pen 255 42 54 66 8
  $metalLightPen = New-Pen 255 132 150 164 3
  $goldPen = New-Pen 255 210 155 58 5
  $bluePen = New-Pen 255 66 220 255 4

  $graphics.DrawLine($shadowPen, 17, 101, 101, 17)
  $graphics.DrawLine($metalPen, 18, 99, 98, 19)
  $graphics.DrawLine($metalLightPen, 24, 91, 90, 25)

  $linkPen = New-Pen 255 78 92 108 5
  $linkLight = New-Pen 255 165 190 205 2
  foreach ($i in 0..4) {
    $x = 18 + ($i * 15)
    $y = 92 - ($i * 15)
    $rect = New-Object System.Drawing.Rectangle ($x - 5), ($y - 10), 20, 12
    $graphics.TranslateTransform($x + 5, $y - 4)
    $graphics.RotateTransform(-45)
    $graphics.TranslateTransform(-($x + 5), -($y - 4))
    $graphics.DrawEllipse($linkPen, $rect)
    $graphics.DrawEllipse($linkLight, $rect)
    $graphics.ResetTransform()
  }

  $hookPath = New-Object System.Drawing.Drawing2D.GraphicsPath
  $hookPath.AddBezier(82, 25, 113, 21, 115, 54, 93, 62)
  $hookPath.AddBezier(93, 62, 80, 67, 82, 83, 99, 83)
  $graphics.DrawPath($shadowPen, $hookPath)
  $graphics.DrawPath($metalPen, $hookPath)
  $graphics.DrawPath($rimPen, $hookPath)
  $graphics.DrawLine($goldPen, 78, 30, 91, 43)

  $coreBrush = New-Brush 255 19 112 158
  $coreGlow = New-Brush 120 33 219 255
  $coreRect = New-Object System.Drawing.Rectangle 39, 40, 48, 48
  $graphics.FillEllipse($coreGlow, 28, 29, 70, 70)
  $graphics.FillRectangle($coreBrush, $coreRect)
  $graphics.DrawRectangle((New-Pen 255 4 11 20 4), $coreRect)
  $graphics.DrawLine($bluePen, 49, 50, 77, 78)
  $graphics.DrawLine($bluePen, 77, 50, 49, 78)

  $highlight = New-Pen 210 255 255 255 2
  $graphics.DrawLine($highlight, 43, 42, 68, 42)
  $graphics.DrawLine($highlight, 41, 45, 41, 70)

  $tempOut = Join-Path $env:TEMP "grappling-armor-icon.png"
  if (Test-Path $tempOut) {
    Remove-Item -LiteralPath $tempOut -Force
  }
  $bitmap.Save($tempOut, [System.Drawing.Imaging.ImageFormat]::Png)
  $graphics.Dispose()
  $graphics = $null
  $bitmap.Dispose()
  $bitmap = $null
  Copy-Item -LiteralPath $tempOut -Destination $resolvedOut -Force
  Remove-Item -LiteralPath $tempOut -Force
  Write-Host "Generated icon: $resolvedOut"
}
finally {
  if ($background) { $background.Dispose() }
  if ($graphics) { $graphics.Dispose() }
  if ($bitmap) { $bitmap.Dispose() }
}
