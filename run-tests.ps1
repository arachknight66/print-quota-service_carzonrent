$mavenVersion = "3.9.6"
$mavenDir = Join-Path $pwd ".maven"
$mavenBin = Join-Path $mavenDir "apache-maven-$mavenVersion\bin\mvn.cmd"

if (-not (Test-Path $mavenBin)) {
    Write-Host "Maven not found. Downloading Apache Maven $mavenVersion..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $mavenDir | Out-Null
    $url = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
    $zipPath = Join-Path $mavenDir "maven.zip"
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    Write-Host "Extracting Maven..." -ForegroundColor Cyan
    Expand-Archive -Path $zipPath -DestinationPath $mavenDir -Force
    Remove-Item $zipPath
}

Write-Host "Running Maven tests..." -ForegroundColor Green
& $mavenBin test @args
