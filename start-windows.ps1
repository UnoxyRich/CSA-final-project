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

    $versionLine = Get-JavaVersionLine $JavaExe
    if ($versionLine -match '"(?<major>\d+)') {
        return [int] $Matches.major
    }

    return 0
}

function Get-JavaVersionLine {
    param([string] $JavaExe)

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $JavaExe
    $psi.Arguments = "-version"
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $lines = @($stderr -split "`r?`n") + @($stdout -split "`r?`n")
    return $lines | Where-Object { $_ } | Select-Object -First 1
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
    $knownUserPaths = @(
        "$env:LOCALAPPDATA\Microsoft\WindowsApps",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links"
    )
    $env:Path = @($machinePath, $userPath, $knownUserPaths, $env:Path) -join ";"
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

    $gradleFromWhere = cmd /c "where gradle.bat 2>nul"
    if ($LASTEXITCODE -eq 0 -and $gradleFromWhere) {
        return ($gradleFromWhere | Select-Object -First 1).Trim()
    }

    $gradleExeFromWhere = cmd /c "where gradle.exe 2>nul"
    if ($LASTEXITCODE -eq 0 -and $gradleExeFromWhere) {
        return ($gradleExeFromWhere | Select-Object -First 1).Trim()
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
        "${env:ProgramFiles(x86)}\Gradle",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Packages",
        "$env:LOCALAPPDATA\Programs",
        "$env:LOCALAPPDATA\Gradle",
        "$env:USERPROFILE\.gradle\wrapper\dists"
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

function Wait-ForGradle {
    $deadline = (Get-Date).AddSeconds(20)
    do {
        Refresh-Path
        $gradle = Find-Gradle
        if ($gradle) {
            return $gradle
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    return $null
}

function Ensure-Gradle {
    $gradle = Find-Gradle
    if ($gradle) {
        return $gradle
    }

    Install-WingetPackage -Id "Gradle.Gradle" -Name "Gradle"

    $gradle = Wait-ForGradle
    if (-not $gradle) {
        throw "Gradle was installed, but this shell could not locate it. Try opening a new Command Prompt and running start.bat again."
    }

    return $gradle
}

Ensure-Java17
$gradleCommand = Ensure-Gradle
$javaVersion = Get-JavaVersionLine (Join-Path $env:JAVA_HOME "bin\java.exe")

Write-Host "-> JAVA_HOME: $env:JAVA_HOME"
Write-Host "-> Java:      $javaVersion"
Write-Host "-> Gradle:    $gradleCommand"
Write-Host "-> Building Minecraft app distribution ..."
Write-Host ""

$previousDebug = $env:DEBUG
try {
    Remove-Item Env:DEBUG -ErrorAction SilentlyContinue
    & $gradleCommand --console=plain installDist @GradleArgs
    $buildExit = $LASTEXITCODE
    if ($buildExit -ne 0) {
        exit $buildExit
    }

    if ($GradleArgs -contains "--dry-run") {
        exit 0
    }

    $appBat = Join-Path $PSScriptRoot "build\install\minecraft\bin\minecraft.bat"
    if (-not (Test-Path -LiteralPath $appBat)) {
        throw "Built app launcher not found: $appBat"
    }

    Write-Host ""
    Write-Host "-> Launching built Minecraft (Ctrl-C to quit) ..."
    Write-Host "-> App:       $appBat"
    Write-Host ""

    & $appBat
    exit $LASTEXITCODE
} finally {
    if ($null -ne $previousDebug) {
        $env:DEBUG = $previousDebug
    }
}
