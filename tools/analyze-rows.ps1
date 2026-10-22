param([string]$XmlPath = "$env:TEMP\paf7c.xml", [string]$PngPath = "$env:TEMP\paf_lrc.png")
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$xml = Get-Content $XmlPath -Raw
$rows = [regex]::Matches($xml, 'text="([^"]{10,60})"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') |
    Where-Object { [int]$_.Groups[3].Value -gt 140 -and [int]$_.Groups[5].Value -lt 2200 } |
    Select-Object -First 12
if (-not (Test-Path $PngPath)) { throw "png missing" }
$img = [System.Drawing.Bitmap]::FromFile($PngPath)
try {
    $i = 0
    foreach ($r in $rows) {
        $y0 = [int]$r.Groups[3].Value
        $y1 = [int]$r.Groups[5].Value
        $max = 0.0
        $bright = 0
        for ($yy = $y0 + 4; $yy -lt $y1 - 2; $yy += 3) {
            for ($xx = 40; $xx -lt 1000; $xx += 4) {
                $p = $img.GetPixel($xx, $yy)
                $g = $p.R * 0.3 + $p.G * 0.59 + $p.B * 0.11
                if ($g -gt $max) { $max = $g }
                if ($g -gt 200) { $bright++ }
            }
        }
        $txt = $r.Groups[1].Value.Trim()
        if ($txt.Length -gt 44) { $txt = $txt.Substring(0, 44) }
        "{0:d2} max={1,3:n0} bright={2,3} :: {3}" -f $i, $max, $bright, $txt
        $i++
    }
} finally { $img.Dispose() }
