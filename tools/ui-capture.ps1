#requires -Version 5.1
<#
  Arnés de captura de UI vía adb (solo lectura: screenshot + uiautomator dump).

  Uso:
    . .\tools\ui-capture.ps1
    Ensure-Poweramp
    Cap "player-main"
    Tap -Desc "Lyrics"
    Cap "player-lyrics"
    Back

  Por cada Cap genera:
    docs/ui-capture/<name>.png   captura de pantalla
    docs/ui-capture/<name>.xml   jerarquía uiautomator cruda
    docs/ui-capture/<name>.txt   resumen legible (id / desc / text / bounds / clickable)
    docs/ui-capture/actions.log  bitácora de acciones con timestamp

  Nota: adb se invoca SIEMPRE con arrays de argumentos (Adb @("shell","screencap","-p",...)),
  porque pasar "-p" suelto hace que PowerShell lo interprete como parámetro del script.
#>

$ErrorActionPreference = "Continue"

$script:AdbExe = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
if (-not (Test-Path $script:AdbExe)) {
    throw "adb no encontrado en $script:AdbExe (define ANDROID_HOME)"
}

$script:OutDir = Join-Path $PSScriptRoot "..\docs\ui-capture"
New-Item -ItemType Directory -Force -Path $script:OutDir | Out-Null
$script:OutDir = (Resolve-Path $script:OutDir).Path
$script:LogFile = Join-Path $script:OutDir "actions.log"

$script:LastNodes = @()
$script:LastCap  = $null

function Adb {
    param([string[]]$AdbArgs)
    return (& $script:AdbExe @AdbArgs 2>$null)
}

function Write-Log([string]$msg) {
    $line = "{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $msg
    Add-Content -Path $script:LogFile -Value $line -Encoding utf8
}

function Read-Nodes([string]$xmlPath) {    [xml]$x = Get-Content -Raw -LiteralPath $xmlPath
    $list = @()
    foreach ($n in $x.SelectNodes("//node")) {
        $b = $n.bounds
        $m = [regex]::Match($b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $m.Success) { continue }
        $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
        $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
        $list += [pscustomobject]@{
            Id        = $n.'resource-id'
            Desc      = $n.'content-desc'
            Text      = $n.text
            Class     = $n.class
            Package   = $n.package
            Clickable = $n.clickable -eq "true"
            LongClick = $n.'long-clickable' -eq "true"
            Selected  = $n.selected -eq "true"
            Checked   = $n.checked -eq "true"
            X1        = $x1; Y1 = $y1; X2 = $x2; Y2 = $y2
            Cx        = [int](($x1 + $x2) / 2)
            Cy        = [int](($y1 + $y2) / 2)
            W         = $x2 - $x1
            H         = $y2 - $y1
        }
    }
    return $list
}

function Save-Screencap {
    param([Parameter(Mandatory)][string]$Path, [int]$Tries = 3)

    for ($i = 1; $i -le $Tries; $i++) {
        if (Test-Path $Path) { Remove-Item $Path -Force -ErrorAction SilentlyContinue }

        # exec-out evita escribir en /sdcard y evita que PowerShell corrompa el PNG.
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $script:AdbExe
        $psi.Arguments = "exec-out screencap -p"
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true

        $proc = [System.Diagnostics.Process]::Start($psi)
        $fs = [System.IO.File]::Create($Path)
        try {
            $proc.StandardOutput.BaseStream.CopyTo($fs)
        } finally {
            $fs.Dispose()
            $proc.WaitForExit()
        }

        if ((Test-Path $Path) -and (Get-Item $Path).Length -gt 4096) {
            $head = [System.IO.File]::ReadAllBytes($Path)[0..7]
            if ($head[0] -eq 0x89 -and $head[1] -eq 0x50 -and $head[2] -eq 0x4E -and $head[3] -eq 0x47) {
                return
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "No se pudo capturar la pantalla ($Path)"
}

function Cap {
    param([Parameter(Mandatory)][string]$Name)

    $png = Join-Path $script:OutDir "$Name.png"
    $xml = Join-Path $script:OutDir "$Name.xml"
    $txt = Join-Path $script:OutDir "$Name.txt"

    Save-Screencap $png
    Adb @("shell", "rm", "-f", "/sdcard/_cap.xml") | Out-Null
    Adb @("shell", "uiautomator", "dump", "/sdcard/_cap.xml") | Out-Null
    Adb @("pull", "/sdcard/_cap.xml", $xml) | Out-Null

    if (-not (Test-Path $xml) -or (Get-Item $xml).Length -lt 200) { throw "No se pudo traer el dump $Name" }

    $script:LastNodes = Read-Nodes $xml
    $script:LastCap = $Name
    $lines = foreach ($n in $script:LastNodes) {
        $flags = @()
        if ($n.Clickable) { $flags += "click" }
        if ($n.LongClick) { $flags += "long" }
        if ($n.Selected)  { $flags += "sel" }
        if ($n.Checked)   { $flags += "chk" }
        $id = $n.Id -replace '^\S*:id/', ''
        "id=$id | desc=$($n.Desc) | text=$($n.Text) | pkg=$($n.Package) | cls=$($n.Class) | $($n.W)x$($n.H) @ ($($n.Cx),$($n.Cy)) [$($flags -join ',')]"
    }
    Set-Content -Path $txt -Value $lines -Encoding utf8
    Write-Log "CAP $Name ($($script:LastNodes.Count) nodes)"
    Write-Host "capturado: $Name"
}

function Find-Node {
    param([string]$Id, [string]$Desc, [string]$Text, [switch]$Contains)
    if ($script:LastNodes.Count -eq 0) {
        # Sin dump en memoria (proceso nuevo): refrescar jerarquía automáticamente.
        $tmp = Join-Path $env:TEMP "opencode\_auto_ui.xml"
        Adb @("shell", "rm", "-f", "/sdcard/_auto.xml") | Out-Null
        Adb @("shell", "uiautomator", "dump", "/sdcard/_auto.xml") | Out-Null
        Adb @("pull", "/sdcard/_auto.xml", $tmp) | Out-Null
        if (Test-Path $tmp) { $script:LastNodes = Read-Nodes $tmp }
    }
    if ($script:LastNodes.Count -eq 0) { throw "No se pudo leer la jerarquía de UI." }
    $res = $script:LastNodes
    if ($Id)   { $res = $res | Where-Object { $_.Id -match "/$([regex]::Escape($Id))$" } }
    if ($Desc) {
        if ($Contains) { $res = $res | Where-Object { $_.Desc -like "*$Desc*" } }
        else           { $res = $res | Where-Object { $_.Desc -eq $Desc } }
    }
    if ($Text) {
        if ($Contains) { $res = $res | Where-Object { $_.Text -like "*$Text*" } }
        else           { $res = $res | Where-Object { $_.Text -eq $Text } }
    }
    return $res | Select-Object -First 1
}

function Show-Nodes {
    param([string]$Filter)
    $script:LastNodes | Where-Object { -not $Filter -or $_.Id -like "*$Filter*" -or $_.Desc -like "*$Filter*" -or $_.Text -like "*$Filter*" } |
        Select-Object @{n='id';e={$_.Id -replace '^\S*:id/',''}}, Desc, Text, Cx, Cy, W, H, Clickable, Checked, Selected |
        Format-Table -AutoSize -Wrap
}

function Tap-At([int]$X, [int]$Y) {
    Adb @("shell", "input", "tap", "$X", "$Y") | Out-Null
    Write-Log "TAP ($X,$Y)"
    Start-Sleep -Milliseconds 700
}

function Tap {
    param([string]$Id, [string]$Desc, [string]$Text, [switch]$Contains)
    $n = Find-Node -Id $Id -Desc $Desc -Text $Text -Contains:$Contains
    if (-not $n) { throw "Nodo no encontrado (id=$Id desc=$Desc text=$Text)" }
    Tap-At $n.Cx $n.Cy
    Write-Log "TAP -> id=$($n.Id) desc=$($n.Desc) text=$($n.Text) @ ($($n.Cx),$($n.Cy))"
}

function LongPress {
    param([string]$Id, [string]$Desc, [string]$Text)
    $n = Find-Node -Id $Id -Desc $Desc -Text $Text
    if (-not $n) { throw "Nodo no encontrado (id=$Id desc=$Desc text=$Text)" }
    Adb @("shell", "input", "swipe", "$($n.Cx)", "$($n.Cy)", "$($n.Cx)", "$($n.Cy)", "900") | Out-Null
    Write-Log "LONGPRESS -> id=$($n.Id) desc=$($n.Desc) @ ($($n.Cx),$($n.Cy))"
    Start-Sleep -Milliseconds 800
}

function Back {
    Adb @("shell", "input", "keyevent", "4") | Out-Null
    Write-Log "BACK"
    Start-Sleep -Milliseconds 700
}

function Swipe-At([int]$X1, [int]$Y1, [int]$X2, [int]$Y2, [int]$Ms = 300) {
    Adb @("shell", "input", "swipe", "$X1", "$Y1", "$X2", "$Y2", "$Ms") | Out-Null
    Write-Log "SWIPE ($X1,$Y1)->($X2,$Y2) ${Ms}ms"
    Start-Sleep -Milliseconds 600
}

function Swipe-Up([int]$Times = 1) {
    for ($i = 0; $i -lt $Times; $i++) { Swipe-At 540 1900 540 900 350 }
}

function Swipe-Down([int]$Times = 1) {
    for ($i = 0; $i -lt $Times; $i++) { Swipe-At 540 900 540 1900 350 }
}

function Launch-Poweramp {
    Adb @("shell", "am", "start", "-n", "com.maxmpz.audioplayer/.MainActivity") | Out-Null
    Start-Sleep -Seconds 2
    Write-Log "LAUNCH poweramp"
}

function Focus {
    $f = (Adb @("shell", "dumpsys", "window")) -join "`n"
    $m = [regex]::Match($f, 'mCurrentFocus=Window\{[^}]*\}?')
    return $m.Value
}

function Ensure-Poweramp {
    $focus = Focus
    if ($focus -notmatch "com\.maxmpz\.audioplayer") {
        Launch-Poweramp
        $focus = Focus
    }
    Write-Log "FOCUS $focus"
    return $focus
}

Write-Host "ui-capture listo. Salida: $script:OutDir"

