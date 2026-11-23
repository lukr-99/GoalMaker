<#
.SYNOPSIS
  Brings the cloud Supabase project up to date with the repository, or says what is behind.

.DESCRIPTION
  A release builds the apps but never touched the cloud, so a release could ship while the cloud
  lacked a migration or an Edge Function (GM-110). This script is the one step that does it, from
  this PC or from the release workflow (.github/workflows/release.yml, the "backend" job).

  -Check  Read only. Compares the cloud's migrations with supabase/migrations and finds Edge Functions
          deployed before their code last changed. Exit 0 when nothing is behind, 1 when something
          is, 2 when it refused or something failed.
  -Apply  Runs `supabase db push` for the pending migrations (after you confirm, or with -Yes),
          deploys the Edge Functions that are behind, then runs -Check again. Safe to run again: with
          nothing behind it does little (see below). It refuses to run unless HEAD is on origin/main
          (a release tag counts when its commit is), unless you pass -AllowBranch.

  Every Edge Function is checked. One is behind when it is not deployed, or was deployed before the
  last commit that changed it or _shared or deno.json. -Apply deploys the ones that are behind and
  also the ones whose code changed since the last release tag by git diff (all of them when there
  is no tag), because commit times alone can be wrong across merges. So a run right after a
  release deploys those again, which does no harm.

  It only ever works on the production project from docs/setup/cloud-supabase.md. It needs the
  Supabase CLI logged in (`npx supabase login`) or SUPABASE_ACCESS_TOKEN set, and the database
  password in SUPABASE_DB_PASSWORD or in the CLI's own prompt. It never prints a secret.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\release-backend.ps1 -Check

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\release-backend.ps1 -Apply
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [switch]$Apply,
    # Do not ask before `supabase db push`. The release workflow uses this after its approval gate.
    [switch]$Yes,
    # Name the release tag (or any git ref) to list changed functions against. Default: the newest
    # v* tag that is not the commit being released.
    [string]$Since,
    # Let -Apply run from a commit that is not on origin/main and not a release tag.
    [switch]$AllowBranch
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-backend.lib.ps1')

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectRef = Get-ProductionProjectRef

# Any error the script did not expect is a failure (exit 2), never "behind" (exit 1).
trap {
    Write-Host (Hide-Secrets "Failed: $($_.Exception.Message)") -ForegroundColor Red
    exit 2
}

function Write-Line {
    param([string]$Text, [string]$Color)
    $safe = Hide-Secrets $Text
    if ($Color) { Write-Host $safe -ForegroundColor $Color } else { Write-Host $safe }
}

function Stop-Refused {
    param([string]$Message)
    Write-Line "Refused: $Message" 'Red'
    exit 2
}

# After a failed step: say what failed, show where the cloud stands now, and exit 2.
function Stop-AfterFailure {
    param([string]$Message)
    Write-Line "FAILED: $Message" 'Red'
    Write-Line 'The cloud now:'
    Show-BackendState (Get-BackendState) | Out-Null
    exit 2
}

# Runs the Supabase CLI (the installed one, or npx supabase) and returns its exit code and text.
function Invoke-Supabase {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $command = Get-Command supabase -ErrorAction SilentlyContinue
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($command) {
            $lines = & $command.Source @Arguments 2>&1 | ForEach-Object { "$_" }
        } else {
            $lines = & npx --yes supabase @Arguments 2>&1 | ForEach-Object { "$_" }
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return [pscustomobject]@{ Code = $code; Text = (($lines | Out-String).TrimEnd()) }
}

function Invoke-Git {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = & git -C $repositoryRoot @Arguments 2>$null | ForEach-Object { "$_" }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return [pscustomobject]@{ Code = $code; Lines = @($lines | Where-Object { $_ -ne '' }) }
}

# Makes sure this folder is linked to the production project and to nothing else.
function Confirm-ProductionLink {
    $refFile = Join-Path $repositoryRoot 'supabase/.temp/project-ref'
    if (-not (Test-Path -LiteralPath $refFile)) {
        Write-Line "Linking this folder to the production project $projectRef ..."
        $link = Invoke-Supabase @('link', '--project-ref', $projectRef)
        if ($link.Code -ne 0) {
            Write-Line $link.Text
            Stop-Refused 'could not link the Supabase project. Log in with `npx supabase login` or set SUPABASE_ACCESS_TOKEN.'
        }
    }
    $linked = (Get-Content -LiteralPath $refFile -Raw).Trim()
    if (-not (Test-LinkedProjectRef -Linked $linked -Expected $projectRef)) {
        Stop-Refused "this folder is linked to project '$linked', not the production project '$projectRef'."
    }
}

function Get-ReleaseBaseline {
    if ($Since) { return $Since }
    $head = (Invoke-Git @('rev-parse', 'HEAD')).Lines | Select-Object -First 1
    $tags = (Invoke-Git @('tag', '--merged', 'HEAD', '--sort=-v:refname', '--list', 'v*')).Lines
    foreach ($tag in $tags) {
        $commit = (Invoke-Git @('rev-parse', "$tag^{commit}")).Lines | Select-Object -First 1
        if ($commit -and $commit -ne $head) { return $tag }
    }
    return $null
}

# Reads the cloud and the repository and returns what is behind. Prints nothing.
function Get-BackendState {
    $shallow = (Invoke-Git @('rev-parse', '--is-shallow-repository')).Lines | Select-Object -First 1
    if ($shallow -eq 'true') {
        Stop-Refused 'this is a shallow git clone, so the time of the last change to each function is unknown. Fetch the full history (fetch-depth: 0).'
    }

    $list = Invoke-Supabase @('migration', 'list', '--linked', '--output-format', 'json')
    if ($list.Code -ne 0) {
        Write-Line $list.Text
        Stop-Refused 'could not read the cloud migrations (supabase migration list). Check the login and SUPABASE_DB_PASSWORD.'
    }
    try {
        $rows = ConvertFrom-MigrationListOutput $list.Text
    } catch {
        Write-Line $list.Text
        Stop-Refused "could not read the output of supabase migration list: $($_.Exception.Message)"
    }
    $repositoryVersions = Get-LocalMigrationVersions (Join-Path $repositoryRoot 'supabase/migrations')
    $migrations = Compare-Migrations -Rows $rows -RepositoryVersions $repositoryVersions

    $functionsDir = Join-Path $repositoryRoot 'supabase/functions'
    $names = Get-FunctionNames $functionsDir
    $functionList = Invoke-Supabase @('functions', 'list', '--project-ref', $projectRef, '-o', 'json')
    if ($functionList.Code -ne 0) {
        Write-Line $functionList.Text
        Stop-Refused 'could not read the deployed Edge Functions (supabase functions list).'
    }
    try {
        $deployed = ConvertFrom-FunctionListOutput $functionList.Text
    } catch {
        Write-Line $functionList.Text
        Stop-Refused "could not read the output of supabase functions list: $($_.Exception.Message)"
    }

    # Every function is checked. The git diff since the last release tag only adds functions to the
    # deploy set; it never limits the check.
    $baseline = Get-ReleaseBaseline
    $changedSince = @()
    if ($baseline) {
        $diff = Invoke-Git @('diff', '--name-only', $baseline, 'HEAD', '--', 'supabase/functions')
        if ($diff.Code -ne 0) { Stop-Refused "git diff against $baseline failed." }
        $changedSince = @(Get-FunctionsForPaths -Paths $diff.Lines -AllNames $names)
    } else {
        $changedSince = @($names)
    }

    $sourceTimes = @{}
    foreach ($name in $names) {
        $when = (Invoke-Git @('log', '-1', '--format=%cI', '--', "supabase/functions/$name", 'supabase/functions/_shared', 'supabase/functions/deno.json')).Lines |
            Select-Object -First 1
        if ($when) { $sourceTimes[$name] = $when }
    }
    $behind = @(Get-FunctionsBehind -Names $names -Deployed $deployed -SourceTimes $sourceTimes)
    $deploySet = Get-FunctionDeploySet -Behind $behind -ChangedSince $changedSince -Baseline $baseline

    return [pscustomobject]@{
        Migrations   = $migrations
        FunctionsBehind = @($behind)
        DeploySet    = @($deploySet)
        ChangedSince = @($changedSince)
        Baseline     = $baseline
    }
}

# Prints the state and returns $true when nothing is behind.
function Show-BackendState {
    param([Parameter(Mandatory)]$State)
    $m = $State.Migrations
    Write-Line "Project:    $projectRef (production)"
    Write-Line "Migrations: repository latest $($m.LatestRepository), cloud latest $($m.LatestRemote)"
    $ok = $true
    if ($m.Pending.Count) {
        $ok = $false
        Write-Line "  BEHIND: not applied in the cloud: $($m.Pending -join ', ')" 'Yellow'
    }
    if ($m.RemoteOnly.Count) {
        $ok = $false
        Write-Line "  PROBLEM: in the cloud but not in the repository: $($m.RemoteOnly -join ', ')" 'Red'
    }
    if ($m.UpToDate) { Write-Line '  Migrations are up to date.' 'Green' }

    if ($State.Baseline) {
        $changed = if ($State.ChangedSince.Count) { $State.ChangedSince -join ', ' } else { 'none' }
        Write-Line "Functions changed since $($State.Baseline): $changed"
    } else {
        Write-Line 'Functions changed since the last release tag: no release tag found.'
    }
    if ($State.FunctionsBehind.Count) {
        $ok = $false
        foreach ($item in $State.FunctionsBehind) {
            Write-Line "  BEHIND: function $($item.Name) ($($item.Reason))" 'Yellow'
        }
    } else {
        Write-Line '  Edge Functions are up to date.' 'Green'
    }
    return $ok
}

function Invoke-Check {
    $state = Get-BackendState
    $ok = Show-BackendState $state
    if ($ok) {
        Write-Line 'The cloud backend matches the repository.' 'Green'
        return 0
    }
    Write-Line 'The cloud backend is BEHIND the repository. Run: tools\release-backend.ps1 -Apply' 'Red'
    return 1
}

if ($Check -eq $Apply) {
    Stop-Refused 'pass exactly one of -Check or -Apply.'
}

Confirm-ProductionLink

if ($Check) {
    exit (Invoke-Check)
}

# -Apply
$onMain = ((Invoke-Git @('merge-base', '--is-ancestor', 'HEAD', 'origin/main')).Code -eq 0)
if (-not (Test-ApplyAllowed -HeadOnMain $onMain -AllowBranch $AllowBranch.IsPresent)) {
    Stop-Refused 'HEAD is not on origin/main (a release tag counts only when its commit is), so -Apply would deploy unreleased code. Check out main, or pass -AllowBranch on purpose.'
}
$state = Get-BackendState
Show-BackendState $state | Out-Null
if ($state.Migrations.RemoteOnly.Count) {
    Stop-Refused 'the cloud has migrations the repository does not. Fix that by hand first; nothing was changed.'
}

if ($state.Migrations.Pending.Count) {
    Write-Line ''
    Write-Line "About to apply $($state.Migrations.Pending.Count) migration(s) to PRODUCTION: $($state.Migrations.Pending -join ', ')" 'Yellow'
    if (-not $Yes) {
        if (-not [Environment]::UserInteractive -or [Console]::IsInputRedirected) {
            Stop-Refused 'asking needs a terminal. Pass -Yes to apply without asking.'
        }
        $answer = Read-Host 'Type yes to apply them'
        if ($answer -ne 'yes') {
            Write-Line 'Stopped. Nothing was changed.'
            exit 2
        }
    }
    $push = Invoke-Supabase @('db', 'push', '--linked', '--yes')
    Write-Line $push.Text
    if ($push.Code -ne 0) { Stop-AfterFailure 'supabase db push failed. Nothing else was deployed.' }
}

foreach ($item in $state.DeploySet) {
    Write-Line "Deploying function $($item.Name) ..."
    $deploy = Invoke-Supabase @('functions', 'deploy', $item.Name, '--project-ref', $projectRef, '--use-api')
    Write-Line $deploy.Text
    if ($deploy.Code -ne 0) { Stop-AfterFailure "deploying function $($item.Name) failed." }
}

if (-not $state.Migrations.Pending.Count -and -not $state.DeploySet.Count) {
    Write-Line 'Nothing to apply.'
}

Write-Line ''
Write-Line 'Checking again:'
exit (Invoke-Check)
