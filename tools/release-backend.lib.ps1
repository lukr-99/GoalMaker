# Pure helpers for tools/release-backend.ps1. Nothing here runs a command or touches the network, so
# tools/test-release-backend.ps1 can test every function on fixtures. Works in Windows PowerShell 5.1
# and in PowerShell 7.

Set-StrictMode -Version Latest

# The one project a release may change (docs/setup/cloud-supabase.md).
$script:ProductionProjectRef = 'wkjnauxqwlqhsqrqnkhg'

function Get-ProductionProjectRef {
    return $script:ProductionProjectRef
}

# True when the folder is linked to exactly the production project.
function Test-LinkedProjectRef {
    param([AllowEmptyString()][string]$Linked, [Parameter(Mandatory)][string]$Expected)
    return ($Linked.Trim() -ceq $Expected)
}

# -Apply changes production, so it only runs from code that is released or on its way: HEAD is on
# origin/main, or it is exactly a release tag (v*). -AllowBranch overrides that on purpose.
function Test-ApplyAllowed {
    param([bool]$HeadOnMain, [bool]$ExactReleaseTag, [bool]$AllowBranch)
    return ($HeadOnMain -or $ExactReleaseTag -or $AllowBranch)
}

# Version numbers of the migrations in the repository: 0029_focus_board.sql gives 0029.
function Get-LocalMigrationVersions {
    param([Parameter(Mandatory)][string]$Directory)
    $versions = @()
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Filter '*.sql' -File) {
        if ($file.Name -match '^(\d+)_') { $versions += $Matches[1] }
    }
    return @($versions | Sort-Object -Unique)
}

# `supabase migration list --linked -o json` prints notices around the JSON, so find the JSON line.
# Each row has `local` and `remote`; a missing side is empty or null.
function ConvertFrom-MigrationListOutput {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $jsonLine = $Text -split "`r?`n" | Where-Object { $_.TrimStart().StartsWith('{') } | Select-Object -First 1
    if (-not $jsonLine) { throw 'The migration list had no JSON in it.' }
    $parsed = $jsonLine | ConvertFrom-Json
    $rows = @()
    foreach ($row in @($parsed.migrations)) {
        $localValue = $row.PSObject.Properties['local']
        $remoteValue = $row.PSObject.Properties['remote']
        $local = if ($localValue -and $localValue.Value) { [string]$localValue.Value } else { '' }
        $remote = if ($remoteValue -and $remoteValue.Value) { [string]$remoteValue.Value } else { '' }
        $rows += [pscustomobject]@{ Local = $local; Remote = $remote }
    }
    return $rows
}

# Compares the CLI's rows with the repository. Behind means: a migration not applied in the cloud,
# a migration in the cloud that the repository does not have, or a newest version that differs.
function Compare-Migrations {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Rows,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$RepositoryVersions
    )
    $remoteVersions = @($Rows | Where-Object { $_.Remote } | ForEach-Object { $_.Remote })
    $pending = @($RepositoryVersions | Where-Object { $remoteVersions -notcontains $_ })
    $remoteOnly = @($remoteVersions | Where-Object { $RepositoryVersions -notcontains $_ })
    $latestRepository = if ($RepositoryVersions.Count) { ($RepositoryVersions | Sort-Object | Select-Object -Last 1) } else { '' }
    $latestRemote = if ($remoteVersions.Count) { ($remoteVersions | Sort-Object | Select-Object -Last 1) } else { '' }
    return [pscustomobject]@{
        Pending         = $pending
        RemoteOnly      = $remoteOnly
        LatestRepository = $latestRepository
        LatestRemote    = $latestRemote
        UpToDate        = ($pending.Count -eq 0 -and $remoteOnly.Count -eq 0 -and $latestRepository -eq $latestRemote)
    }
}

# The Edge Functions in a folder: every subfolder with an index.ts, except the shared ones (_shared).
function Get-FunctionNames {
    param([Parameter(Mandatory)][string]$Directory)
    $names = @()
    foreach ($dir in Get-ChildItem -LiteralPath $Directory -Directory) {
        if ($dir.Name.StartsWith('_')) { continue }
        if (Test-Path -LiteralPath (Join-Path $dir.FullName 'index.ts')) { $names += $dir.Name }
    }
    return @($names | Sort-Object)
}

# Which functions a list of changed paths (from git diff --name-only, relative to the repository)
# touches. A change in _shared or in deno.json can reach every function, so it names all of them.
function Get-FunctionsForPaths {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Paths,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$AllNames
    )
    $hit = @()
    foreach ($raw in $Paths) {
        $path = $raw.Replace('\', '/')
        if ($path -notmatch '^supabase/functions/(.+)$') { continue }
        $rest = $Matches[1]
        $first = $rest.Split('/')[0]
        if ($first.StartsWith('_') -or $first -eq 'deno.json' -or $first -eq 'deno.lock' -or $first -eq 'import_map.json') {
            $hit += $AllNames
        } elseif ($AllNames -contains $first) {
            $hit += $first
        }
    }
    return @($hit | Sort-Object -Unique)
}

# A function is behind when it is not deployed, or was deployed before the last commit that changed
# its code (or the shared code). Deployed holds updated_at in milliseconds since 1970 by name;
# SourceTimes holds the time of that last commit by name.
function Get-FunctionsBehind {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Names,
        [Parameter(Mandatory)][hashtable]$Deployed,
        [Parameter(Mandatory)][hashtable]$SourceTimes
    )
    $behind = @()
    foreach ($name in $Names) {
        if (-not $Deployed.ContainsKey($name)) {
            $behind += [pscustomobject]@{ Name = $name; Reason = 'not deployed' }
            continue
        }
        if (-not $SourceTimes.ContainsKey($name)) { continue }
        $deployedAt = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Deployed[$name])
        $changedAt = [DateTimeOffset]$SourceTimes[$name]
        if ($deployedAt -lt $changedAt) {
            $behind += [pscustomobject]@{
                Name   = $name
                Reason = "deployed $($deployedAt.UtcDateTime.ToString('yyyy-MM-dd HH:mm')) UTC, code changed $($changedAt.UtcDateTime.ToString('yyyy-MM-dd HH:mm')) UTC"
            }
        }
    }
    return $behind
}

# `supabase functions list -o json` gives a JSON array with slug and updated_at (milliseconds).
function ConvertFrom-FunctionListOutput {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $start = $Text.IndexOf('[')
    $end = $Text.LastIndexOf(']')
    $deployed = @{}
    if ($start -lt 0 -or $end -lt $start) { return $deployed }
    $items = $Text.Substring($start, $end - $start + 1) | ConvertFrom-Json
    foreach ($item in @($items)) {
        if ($item.slug) { $deployed[[string]$item.slug] = [int64]$item.updated_at }
    }
    return $deployed
}

# Hides anything that looks like a Supabase token or a password in a connection string, so output
# that is printed or logged never carries one.
function Hide-Secrets {
    param([AllowEmptyString()][string]$Text)
    $text = [regex]::Replace($Text, 'sbp_[A-Za-z0-9]+', 'sbp_***')
    $text = [regex]::Replace($text, '(postgres(?:ql)?://[^:/\s]+:)[^@\s]+@', '$1***@')
    $text = [regex]::Replace($text, 'eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+', 'eyJ***')
    return $text
}
