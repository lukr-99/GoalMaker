<#
  Publish GoalMaker for Windows (framework-dependent; the installer checks for the .NET 10 Desktop
  Runtime) and compile the per-user Inno Setup installer, with a SHA-256 checksum beside it. From the
  CodePrint .NET profile.

  Release (default): -p:GoalMakerReleaseBuild=true, so the version has no -dev suffix and the build
  must know the cloud project (GOALMAKER_SUPABASE_URL / GOALMAKER_SUPABASE_KEY or
  windows/GoalMaker.local.props). -Dev builds a side-by-side "GoalMaker Dev" installer for the
  local stack instead.

  Output: windows/installer/dist/GoalMaker-<version>-setup.exe (+ .sha256). Ignored by Git.
#>
[CmdletBinding()]
param(
    [switch]$Dev,
    [string]$IsccPath,
    [switch]$RequireSigned
)

$ErrorActionPreference = 'Stop'
$installerRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$windowsRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$project = Join-Path $windowsRoot 'src\GoalMaker.App\GoalMaker.App.csproj'

$versionLine = Get-Content -LiteralPath (Join-Path $repositoryRoot 'version.properties') |
    Where-Object { $_ -match '^versionName=(\d+\.\d+\.\d+)\s*$' } | Select-Object -First 1
if (-not $versionLine) { throw 'versionName=X.Y.Z not found in version.properties' }
$baseVersion = ([regex]::Match($versionLine, '\d+\.\d+\.\d+')).Value
$version = if ($Dev) { "$baseVersion-dev" } else { $baseVersion }
$versionInfoVersion = "$baseVersion.0"

if (-not $IsccPath) {
    $IsccPath = @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
}
if (-not $IsccPath -or -not (Test-Path -LiteralPath $IsccPath -PathType Leaf)) {
    throw 'Inno Setup 6 ISCC.exe was not found. Install it or pass -IsccPath.'
}

$publish = Join-Path $installerRoot 'publish'
$dist = Join-Path $installerRoot 'dist'
if (Test-Path -LiteralPath $publish) { Remove-Item -LiteralPath $publish -Recurse -Force }
[System.IO.Directory]::CreateDirectory($publish) | Out-Null
[System.IO.Directory]::CreateDirectory($dist) | Out-Null

$releaseFlag = if ($Dev) { 'false' } else { 'true' }
Write-Host "Publishing GoalMaker $version..." -ForegroundColor Cyan
# Framework-dependent: about 9 MB with the Windows SDK projection for toasts (ADR 0009), instead of
# 50 MB and more self-contained, far below the update channel's 50 MB limit (ADR 0004).
& dotnet publish $project -c Release -r win-x64 --self-contained false `
    -p:PublishSingleFile=false -p:GoalMakerReleaseBuild=$releaseFlag -p:DebugType=none -o $publish --nologo
if ($LASTEXITCODE -ne 0) { throw 'dotnet publish failed.' }

$definition = Join-Path $installerRoot 'GoalMaker.iss'
$defines = @("/DMyAppVersion=$version", "/DMyVersionInfoVersion=$versionInfoVersion", "/DPublishDir=$publish")
if ($Dev) { $defines += '/DDevBuild' }
& $IsccPath /Q @defines $definition
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }
# The publish folder is only the installer's input; the installer is the artifact.
Remove-Item -LiteralPath $publish -Recurse -Force

$setup = Join-Path $dist "GoalMaker-$version-setup.exe"
if (-not (Test-Path -LiteralPath $setup -PathType Leaf)) { throw "Expected installer was not produced: $setup" }

$signature = Get-AuthenticodeSignature -LiteralPath $setup
if ($RequireSigned -and $signature.Status -ne 'Valid') { throw "Installer signature is not valid: $($signature.Status)" }
if ($signature.Status -ne 'Valid') {
    Write-Warning 'The installer is not Authenticode-signed; the update channel trusts it through the signed manifest instead (ADR 0004).'
}

$size = (Get-Item -LiteralPath $setup).Length
$limit = 50MB
if ($size -gt $limit) {
    Write-Warning "The installer is $([math]::Round($size / 1MB, 1)) MB, over the 50 MB per-file limit of the free Supabase plan's update channel."
}

$hash = (Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText("$setup.sha256", "$hash  $([System.IO.Path]::GetFileName($setup))`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Built $setup ($([math]::Round($size / 1MB, 1)) MB) and its SHA-256 checksum." -ForegroundColor Green
