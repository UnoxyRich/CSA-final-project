param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

& "$PSScriptRoot\start-windows.ps1" @GradleArgs
exit $LASTEXITCODE
