param(
    [Parameter(Mandatory = $true)]
    [string]$StudentNames
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$zipName = "${StudentNames}_project1.zip"
$zipPath = Join-Path $root $zipName
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

$stage = Join-Path $env:TEMP ("chat-submit-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
    $exclude = @("target", ".git", ".idea")
    Get-ChildItem -Path $root -Force | Where-Object { $exclude -notcontains $_.Name -and $_.Name -ne $zipName } | ForEach-Object {
        Copy-Item $_.FullName -Destination (Join-Path $stage $_.Name) -Recurse -Force
    }
    Get-ChildItem -Path $stage -Recurse -Directory -Filter "target" | Remove-Item -Recurse -Force
    Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zipPath
    Write-Output $zipPath
}
finally {
    Remove-Item $stage -Recurse -Force
}
