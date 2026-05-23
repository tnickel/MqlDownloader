# deploy.ps1 - MqlDownloader Automated Deployment Script
$ErrorActionPreference = "Stop"

$pomPath = "pom.xml"
Write-Host "==> Reading $pomPath ..."
$pomContent = Get-Content $pomPath -Raw

# Match version specifically following the MqlDownloader artifactId
$matchRegex = '(?s)(<artifactId>MqlDownloader</artifactId>\s*<version>)([^<]+)(</version>)'

if ($pomContent -match $matchRegex) {
    # Capture outer match values immediately to avoid nested match overwriting
    $prefix = $Matches[1]
    $currentVersion = $Matches[2]
    $suffixTag = $Matches[3]
    
    Write-Host "Found current version: $currentVersion"
    
    # Parse and increment version components (e.g. X.Y.Z-SNAPSHOT)
    if ($currentVersion -match '^(\d+)\.(\d+)\.(\d+)(.*)$') {
        $major = [int]$Matches[1]
        $minor = [int]$Matches[2]
        $patch = [int]$Matches[3]
        $suffix = $Matches[4]
        
        $newPatch = $patch + 1
        $newVersion = "$major.$minor.$newPatch$suffix"
        Write-Host "Incremented version: $currentVersion ==> $newVersion"
        
        # Build the exact string replacement
        $oldString = $prefix + $currentVersion + $suffixTag
        $newString = $prefix + $newVersion + $suffixTag
        
        $pomContent = $pomContent.Replace($oldString, $newString)
        Set-Content -Path $pomPath -Value $pomContent -Encoding utf8
        Write-Host "Successfully updated $pomPath to version $newVersion"
    } else {
        Write-Error "Could not parse version format: $currentVersion"
        exit 1
    }
} else {
    Write-Error "Could not find project version following artifactId 'MqlDownloader' in $pomPath"
    exit 1
}

# Run Maven build to compile and generate fat JAR
Write-Host "==> Building executable package with Maven..."
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven compilation/packaging failed!"
    exit 1
}

# Locate generated fat JAR (with dependencies)
$sourceJarPattern = "target/MqlDownloader-*-jar-with-dependencies.jar"
$sourceJars = Get-ChildItem -Path $sourceJarPattern
if (-not $sourceJars) {
    Write-Error "Could not locate the compiled fat JAR under target/!"
    exit 1
}

$sourceJar = $sourceJars[0]
$targetPath = "\\ds918\Forex\tmp\MqlDownloaderApp.jar"

Write-Host "==> Deploying $($sourceJar.Name) to $targetPath ..."
try {
    # Check if target share is reachable and writeable
    $targetDir = Split-Path $targetPath
    if (-not (Test-Path $targetDir)) {
        Write-Warning "Directory $targetDir is not directly accessible or does not exist. Attempting copy anyway..."
    }
    
    Copy-Item -Path $sourceJar.FullName -Destination $targetPath -Force
    Write-Host "Deployment successful! JAR copied to $targetPath"
} catch {
    Write-Error "Deployment failed: $_"
    exit 1
}
