<#
  Record a GoalMaker Windows release on this PC, in Kiln (local CI). Kiln's "Install on this PC" (ask mode,
  project "goalmaker") then offers to install it. Modelled on Kiln's own tools/release.ps1.

  Why a host script: Kiln jobs run in Linux containers only, and the Inno Setup installer needs Windows.

  Steps: read the version from version.properties, check it is a plain X.Y.Z, check the working tree is clean,
  run the gate (repository validation, format check, Windows tests), build the production installer with
  windows/installer/build-installer.ps1, then record it with `kiln release add --project goalmaker`, which copies
  it to %LOCALAPPDATA%\Kiln\releases\goalmaker\<version>\ and writes SHA256SUMS and release.json.

  Nothing leaves this PC: no tag, no push, no GitHub release. The real release stays on GitHub
  (.github/workflows/release.yml, docs/setup/signing-and-releases.md). A tag later on is still needed there.

  The release folder holds exactly one file, GoalMaker-<version>-setup.exe, because Kiln's install pattern
  GoalMaker-*-setup.exe must match one file. The script stages that one file, so a "GoalMaker-<version>-dev-setup.exe"
  from a -Dev build in the same dist folder can never get in.

  The notes are the commit subjects since the latest earlier v* tag (the repository has no CHANGELOG.md; the
  GitHub release uses generated notes in the same way).

  A release build needs the cloud project: GOALMAKER_SUPABASE_URL and GOALMAKER_SUPABASE_KEY, or
  windows/GoalMaker.local.props (see docs/setup/local-development.md).
#>
[CmdletBinding()]
param(
    # Where Kiln keeps its data (default %LOCALAPPDATA%\Kiln). Pass another folder to try the flow without
    # touching the real Kiln data or offering an install.
    [string]$DataDir,
    # Skip the gate (repository validation, format check, Windows tests).
    [switch]$SkipChecks,
    # Allow uncommitted changes (a trial run; the release then records a commit that does not hold every change).
    [switch]$AllowDirty,
    [string]$IsccPath
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'release-backend.lib.ps1')
# A relative -DataDir is relative to where the script was started, not to the staging folder used later.
if ($DataDir) { $DataDir = [System.IO.Path]::GetFullPath($DataDir) }

function Invoke-Step {
    param([string]$Name, [scriptblock]$Command)
    Write-Host "==> $Name" -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE." }
}

Push-Location $root
try {
    $versionLine = Get-Content -LiteralPath (Join-Path $root 'version.properties') |
        Where-Object { $_ -match '^versionName=(\d+\.\d+\.\d+)\s*$' } | Select-Object -First 1
    if (-not $versionLine) { throw 'versionName=X.Y.Z (no suffix) not found in version.properties' }
    $version = ([regex]::Match($versionLine, '\d+\.\d+\.\d+')).Value
    Write-Host "Releasing GoalMaker $version on this PC" -ForegroundColor Cyan

    # The library sets strict mode for this whole script, so never read a property of a possibly missing value.
    $kiln = $null
    $kilnCommand = Get-Command kiln -ErrorAction SilentlyContinue
    if ($kilnCommand) { $kiln = $kilnCommand.Source }
    if (-not $kiln) {
        $installed = Join-Path $env:LOCALAPPDATA 'Programs\Kiln\kiln.exe'
        if (Test-Path -LiteralPath $installed -PathType Leaf) { $kiln = $installed }
    }
    if (-not $kiln) { throw 'kiln.exe was not found. Install Kiln first (it is the release recorder).' }

    $dirty = & git status --porcelain
    if ($dirty -and -not $AllowDirty) {
        throw 'The working tree is not clean. Commit first, or pass -AllowDirty for a trial run (the release then records a commit that does not hold every change).'
    }
    $commit = (& git rev-parse HEAD).Trim()

    # A release made from a commit that is not on main is only offered by Kiln, and the real release on GitHub
    # needs main anyway, so say so early.
    # The fetch is best effort: a failure (offline, no credentials) must not stop the release, and it must not prompt.
    $savedPreference = $ErrorActionPreference
    $savedPrompt = $env:GIT_TERMINAL_PROMPT
    $ErrorActionPreference = 'Continue'
    $env:GIT_TERMINAL_PROMPT = '0'
    try {
        & git fetch --quiet origin main 2>$null
        & git merge-base --is-ancestor $commit origin/main 2>$null
        $onMain = ($LASTEXITCODE -eq 0)
    }
    finally {
        $ErrorActionPreference = $savedPreference
        $env:GIT_TERMINAL_PROMPT = $savedPrompt
    }
    $global:LASTEXITCODE = 0
    if (-not $onMain) {
        Write-Warning 'HEAD is not on origin/main (or origin could not be read). Kiln will only offer this release, and the GitHub release must be made from main.'
    }

    # Notes: the commit subjects since the newest earlier release tag that is an ancestor of HEAD.
    $previous = Get-PreviousReleaseTag -RepositoryRoot $root
    $range = if ($previous) { "$previous..$commit" } else { $commit }
    $subjects = @(& git log --no-merges --format='- %s' $range)
    $notes = "GoalMaker $version (built on this PC)`n`n" + ($subjects -join "`n")
    if (-not $subjects) { $notes = "GoalMaker $version (built on this PC)" }

    if (-not $SkipChecks) {
        Invoke-Step 'Repository validation' { & python (Join-Path $root 'tools/validate_repository.py') --root $root }
        Invoke-Step 'Windows format check' { & dotnet format whitespace (Join-Path $root 'windows/GoalMaker.slnx') --verify-no-changes --no-restore }
        # Run from windows/ so its global.json applies: it selects Microsoft.Testing.Platform, which
        # `--solution` needs. From the repo root, dotnet test falls back to VSTest and rejects the switch.
        Invoke-Step 'Windows tests' {
            Push-Location (Join-Path $root 'windows')
            try { & dotnet test --solution GoalMaker.slnx }
            finally { Pop-Location }
        }
    }

    $buildArguments = @()
    if ($IsccPath) { $buildArguments += @('-IsccPath', $IsccPath) }
    Invoke-Step 'Build the installer' { & (Join-Path $root 'windows/installer/build-installer.ps1') @buildArguments }

    $setupName = "GoalMaker-$version-setup.exe"
    $setup = Join-Path $root "windows/installer/dist/$setupName"
    if (-not (Test-Path -LiteralPath $setup -PathType Leaf)) { throw "The installer was not built: $setup" }

    # Stage the one production installer, so nothing else from dist (a dev installer, checksum files) is recorded.
    $stage = Join-Path ([System.IO.Path]::GetTempPath()) ('goalmaker-release-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    [System.IO.Directory]::CreateDirectory($stage) | Out-Null
    $notesFile = Join-Path $stage 'notes.md'
    try {
        Copy-Item -LiteralPath $setup -Destination (Join-Path $stage $setupName)
        [System.IO.File]::WriteAllText($notesFile, $notes, [System.Text.UTF8Encoding]::new($false))
        Push-Location $stage
        try {
            $arguments = @('release', 'add', '--project', 'goalmaker', '--version', $version, '--notes-file', 'notes.md', '--commit', $commit, $setupName)
            if ($DataDir) { $arguments += @('--data-dir', $DataDir) }
            Invoke-Step 'Record the release (kiln release add)' { & $kiln @arguments }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "GoalMaker $version is recorded in Kiln on this PC. Nothing was tagged, pushed or published." -ForegroundColor Green
    $showData = if ($DataDir) { " --data-dir `"$DataDir`"" } else { '' }
    Write-Host "See it in the Kiln dashboard (Releases) or with: kiln release show goalmaker $version$showData"
}
finally {
    Pop-Location
}
