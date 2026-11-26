param(
    [int]$N = 5,
    [int]$Seconds = 0
)

$root = $PSScriptRoot
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Force $logs | Out-Null

foreach ($module in "monitor-service", "philosophe") {
    Push-Location (Join-Path $root $module)
    & .\mvnw.cmd -q package -DskipTests
    Pop-Location
}

$monitorJar = (Get-ChildItem "$root\monitor-service\target\*.jar" | Select-Object -First 1).FullName
$philosopherJar = (Get-ChildItem "$root\philosophe\target\*.jar" | Select-Object -First 1).FullName

$processes = @()
try {
    $processes += Start-Process java -ArgumentList "-jar", $monitorJar -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$logs\monitor.out" -RedirectStandardError "$logs\monitor.err"
    Start-Sleep -Seconds 8

    foreach ($i in 1..$N) {
        $port = 8080 + $i
        $processes += Start-Process java -ArgumentList "-jar", $philosopherJar, "--server.port=$port" -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$logs\philosophe$i.out" -RedirectStandardError "$logs\philosophe$i.err"
    }

    Write-Host "Moniteur + $N philosophes lances. Tableau de bord : http://localhost:8080/"
    if ($Seconds -gt 0) {
        Start-Sleep -Seconds $Seconds
    } else {
        Write-Host "Ctrl+C pour tout arreter."
        Wait-Process -Id $processes[0].Id
    }
}
finally {
    $processes | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    Write-Host "Tout est arrete."
}
