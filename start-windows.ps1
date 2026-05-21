param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$NoInstall = $env:NO_INSTALL -eq "1"
$AssumeYes = $env:ASSUME_YES -eq "1"

function Confirm-Install {
    param([string] $Message)

    if ($AssumeYes) {
        return $true
    }

    $answer = Read-Host "$Message [Y/n]"
    return [string]::IsNullOrWhiteSpace($answer) -or $answer -match "^[Yy]"
}

function Get-JavaMajorVersion {
    param([string] $JavaExe)

    if (-not (Test-Path -LiteralPath $JavaExe)) {
        return 0
    }

    $versionLine = & $JavaExe -version 2>&1 | Select-Object -First 1
    if ($versionLine -match '"(?<major>\d+)') {
        return [int] $Matches.major
    }

    return 0
}

function Find-Java17Home {
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if ((Get-JavaMajorVersion $javaExe) -ge 17) {
            return $env:JAVA_HOME
        }
    }

    $javaFromPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaFromPath -and (Get-JavaMajorVersion $javaFromPath.Source) -ge 17) {
        return (Resolve-Path (Join-Path (Split-Path $javaFromPath.Source -Parent) "..")).Path
    }

    $candidateRoots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "${env:ProgramFiles(x86)}\Java"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    foreach ($root in $candidateRoots) {
        $candidate = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "(jdk|temurin).*(17|1[8-9]|[2-9]\d)" } |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($candidate) {
            $javaExe = Join-Path $candidate.FullName "bin\java.exe"
            if ((Get-JavaMajorVersion $javaExe) -ge 17) {
                return $candidate.FullName
            }
        }
    }

    return $null
}

function Refresh-Path {
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = @($machinePath, $userPath, $env:Path) -join ";"
}

function Ensure-Winget {
    if (Get-Command winget.exe -ErrorAction SilentlyContinue) {
        return
    }

    throw "winget is required for automatic installs. Install prerequisites manually or run with NO_INSTALL=1 to only check."
}

function Install-WingetPackage {
    param(
        [string] $Id,
        [string] $Name
    )

    if ($NoInstall) {
        throw "$Name is missing and NO_INSTALL=1."
    }

    Ensure-Winget
    if (-not (Confirm-Install "Install $Name with winget now?")) {
        throw "aborted."
    }

    winget install --id $Id --exact --source winget --accept-source-agreements --accept-package-agreements
    Refresh-Path
}

function Ensure-Java17 {
    $javaHome = Find-Java17Home
    if ($javaHome) {
        $env:JAVA_HOME = $javaHome
        $env:Path = "$(Join-Path $javaHome "bin");$env:Path"
        return
    }

    Install-WingetPackage -Id "EclipseAdoptium.Temurin.17.JDK" -Name "JDK 17"

    $javaHome = Find-Java17Home
    if (-not $javaHome) {
        throw "JDK 17 was installed, but this shell could not locate it. Open a new terminal and run start.bat again."
    }

    $env:JAVA_HOME = $javaHome
    $env:Path = "$(Join-Path $javaHome "bin");$env:Path"
}

function Find-Gradle {
    $wrapper = Join-Path $PSScriptRoot "gradlew.bat"
    if (Test-Path -LiteralPath $wrapper) {
        return $wrapper
    }

    $gradleFromPath = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($gradleFromPath) {
        return $gradleFromPath.Source
    }

    $gradleExeFromPath = Get-Command gradle.exe -ErrorAction SilentlyContinue
    if ($gradleExeFromPath) {
        return $gradleExeFromPath.Source
    }

    $roots = @(
        "$env:ProgramFiles\Gradle",
        "${env:ProgramFiles(x86)}\Gradle"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    foreach ($root in $roots) {
        $gradleBat = Get-ChildItem -LiteralPath $root -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1

        if ($gradleBat) {
            return $gradleBat.FullName
        }
    }

    return $null
}

function Ensure-Gradle {
    $gradle = Find-Gradle
    if ($gradle) {
        return $gradle
    }

    Install-WingetPackage -Id "Gradle.Gradle" -Name "Gradle"

    $gradle = Find-Gradle
    if (-not $gradle) {
        throw "Gradle was installed, but this shell could not locate it. Open a new terminal and run start.bat again."
    }

    return $gradle
}

Ensure-Java17
$gradleCommand = Ensure-Gradle
$javaVersion = & (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1 | Select-Object -First 1

Write-Host "-> JAVA_HOME: $env:JAVA_HOME"
Write-Host "-> Java:      $javaVersion"
Write-Host "-> Gradle:    $gradleCommand"
Write-Host "-> Launching Minecraft (Ctrl-C to quit) ..."
Write-Host ""

& $gradleCommand --console=plain run @GradleArgs
exit $LASTEXITCODE
