# Self-test for tools/release-backend.lib.ps1: the version compare, the changed-functions logic and
# the secret hiding, on fixtures. Needs no Supabase CLI, no network and no Pester. Exits 1 on a failure.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-backend.lib.ps1')

$script:failures = 0
$script:count = 0

function Assert-Equal {
    param([string]$Name, $Expected, $Actual)
    $script:count++
    $e = (@($Expected) | ForEach-Object { "$_" }) -join '|'
    $a = (@($Actual) | ForEach-Object { "$_" }) -join '|'
    if ($e -ne $a) {
        $script:failures++
        Write-Host "FAIL $Name`n  expected: $e`n  actual:   $a" -ForegroundColor Red
    }
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ("release-backend-test-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
try {
    # Migrations on disk.
    $migrations = Join-Path $work 'migrations'
    New-Item -ItemType Directory -Path $migrations | Out-Null
    foreach ($name in '0001_start.sql', '0002_more.sql', '0010_ten.sql', 'README.md') {
        Set-Content -LiteralPath (Join-Path $migrations $name) -Value '-- x'
    }
    Assert-Equal 'local versions' @('0001', '0002', '0010') (Get-LocalMigrationVersions $migrations)

    # The CLI output, with notices around the JSON line.
    $listText = @(
        'Initialising login role...',
        'Connecting to remote database...',
        '{"migrations":[{"local":"0001","remote":"0001","time":"0001"},{"local":"0002","remote":"","time":""},{"local":"0010","time":""}]}',
        'A new version of Supabase CLI is available'
    ) -join "`r`n"
    $rows = ConvertFrom-MigrationListOutput $listText
    Assert-Equal 'rows parsed' 3 $rows.Count
    $cmp = Compare-Migrations -Rows $rows -RepositoryVersions @('0001', '0002', '0010')
    Assert-Equal 'pending' @('0002', '0010') $cmp.Pending
    Assert-Equal 'latest cloud' '0001' $cmp.LatestRemote
    Assert-Equal 'latest repository' '0010' $cmp.LatestRepository
    Assert-Equal 'behind is not up to date' $false $cmp.UpToDate

    $same = ConvertFrom-MigrationListOutput '{"migrations":[{"local":"0001","remote":"0001"},{"local":"0002","remote":"0002"}]}'
    $cmpSame = Compare-Migrations -Rows $same -RepositoryVersions @('0001', '0002')
    Assert-Equal 'up to date' $true $cmpSame.UpToDate
    Assert-Equal 'nothing pending' 0 @($cmpSame.Pending).Count

    $ahead = ConvertFrom-MigrationListOutput '{"migrations":[{"local":"0001","remote":"0001"},{"local":"","remote":"0099"}]}'
    $cmpAhead = Compare-Migrations -Rows $ahead -RepositoryVersions @('0001')
    Assert-Equal 'cloud has a migration the repository lacks' @('0099') $cmpAhead.RemoteOnly
    Assert-Equal 'remote only is not up to date' $false $cmpAhead.UpToDate

    $empty = Compare-Migrations -Rows @() -RepositoryVersions @('0001')
    Assert-Equal 'empty cloud' @('0001') $empty.Pending

    $threw = $false
    try { ConvertFrom-MigrationListOutput 'no json here' | Out-Null } catch { $threw = $true }
    Assert-Equal 'text without JSON throws' $true $threw

    # Function names: folders with an index.ts, never _shared.
    $functions = Join-Path $work 'functions'
    foreach ($dir in 'assistant', 'connector', 'pc-sign-in', '_shared', 'notes-only') {
        New-Item -ItemType Directory -Path (Join-Path $functions $dir) | Out-Null
    }
    foreach ($dir in 'assistant', 'connector', 'pc-sign-in', '_shared') {
        Set-Content -LiteralPath (Join-Path $functions "$dir/index.ts") -Value '//'
    }
    $names = Get-FunctionNames $functions
    Assert-Equal 'function names' @('assistant', 'connector', 'pc-sign-in') $names

    # Changed paths to functions.
    Assert-Equal 'one function' @('connector') (Get-FunctionsForPaths -Paths @('supabase/functions/connector/index.ts', 'README.md') -AllNames $names)
    Assert-Equal 'shared reaches all' $names (Get-FunctionsForPaths -Paths @('supabase/functions/_shared/owner.ts') -AllNames $names)
    Assert-Equal 'deno.json reaches all' $names (Get-FunctionsForPaths -Paths @('supabase/functions/deno.json') -AllNames $names)
    Assert-Equal 'backslashes' @('assistant') (Get-FunctionsForPaths -Paths @('supabase\functions\assistant\index.ts') -AllNames $names)
    Assert-Equal 'unrelated paths' 0 @(Get-FunctionsForPaths -Paths @('android/app/x.kt', 'supabase/migrations/0001_a.sql') -AllNames $names).Count
    Assert-Equal 'unknown folder' 0 @(Get-FunctionsForPaths -Paths @('supabase/functions/gone/index.ts') -AllNames $names).Count

    # Functions behind: deployed times are milliseconds since 1970.
    $changed = [DateTimeOffset]'2026-11-10T12:00:00+00:00'
    $before = $changed.AddHours(-1).ToUnixTimeMilliseconds()
    $after = $changed.AddHours(1).ToUnixTimeMilliseconds()
    $deployed = @{ 'assistant' = $before; 'connector' = $after }
    $sourceTimes = @{ 'assistant' = '2026-11-10T13:00:00+01:00'; 'connector' = '2026-11-10T12:00:00+00:00'; 'pc-sign-in' = '2026-11-01T00:00:00+00:00' }
    $behind = Get-FunctionsBehind -Names $names -Deployed $deployed -SourceTimes $sourceTimes
    # assistant: code changed 12:00 UTC, deployed 11:00 UTC: behind. connector: deployed later: fine.
    # pc-sign-in: never deployed: behind.
    Assert-Equal 'behind names' @('assistant', 'pc-sign-in') ($behind | ForEach-Object { $_.Name })
    Assert-Equal 'never deployed reason' 'not deployed' (($behind | Where-Object { $_.Name -eq 'pc-sign-in' }).Reason)
    $fine = Get-FunctionsBehind -Names @('connector') -Deployed $deployed -SourceTimes $sourceTimes
    Assert-Equal 'deployed after the change' 0 @($fine).Count

    # Changed before the last release tag and never deployed: the check still flags it, and the
    # tag diff (which does not name it) leaves it in the deploy set.
    $oldDeployed = @{ 'assistant' = $after; 'connector' = $after }
    $oldTimes = @{ 'assistant' = '2026-01-01T00:00:00+00:00'; 'connector' = '2026-01-01T00:00:00+00:00'; 'pc-sign-in' = '2026-01-01T00:00:00+00:00' }
    $oldBehind = @(Get-FunctionsBehind -Names $names -Deployed $oldDeployed -SourceTimes $oldTimes)
    Assert-Equal 'never deployed is flagged although unchanged since the tag' @('pc-sign-in') ($oldBehind | ForEach-Object { $_.Name })
    $set = @(Get-FunctionDeploySet -Behind $oldBehind -ChangedSince @('connector') -Baseline 'v1.17.0')
    Assert-Equal 'deploy set is behind plus tag diff' @('connector', 'pc-sign-in') ($set | ForEach-Object { $_.Name })
    Assert-Equal 'deploy set keeps the behind reason' 'not deployed' (($set | Where-Object { $_.Name -eq 'pc-sign-in' }).Reason)
    Assert-Equal 'deploy set names the tag' 'changed since v1.17.0' (($set | Where-Object { $_.Name -eq 'connector' }).Reason)
    Assert-Equal 'deploy set without a tag takes the diff list' @('assistant') (@(Get-FunctionDeploySet -Behind @() -ChangedSince @('assistant') -Baseline '') | ForEach-Object { $_.Name })

    # The functions list.
    $functionList = "notice`n[{`"slug`":`"assistant`",`"updated_at`":1795389077416,`"version`":14},{`"slug`":`"connector`",`"updated_at`":1795389071946}]`ntrailing"
    $parsedFunctions = ConvertFrom-FunctionListOutput $functionList
    Assert-Equal 'function list count' 2 $parsedFunctions.Count
    Assert-Equal 'function list time' 1795389077416 $parsedFunctions['assistant']
    Assert-Equal 'empty function list' 0 (ConvertFrom-FunctionListOutput 'nothing').Count

    # The previous release tag in the checkout of a tag push: a detached HEAD on the tag being
    # released, with the older tags next to it (the v1.17.1 run found none, GM-115).
    $git = { param($dir, [string[]]$gitArguments) & git -C $dir -c user.name=t -c user.email=t@example.com @gitArguments 2>&1 | Out-Null }
    $repo = Join-Path $work 'repo'
    New-Item -ItemType Directory -Path $repo | Out-Null
    & $git $repo @('init', '-q', '-b', 'main')
    $n = 0
    foreach ($step in 'a', 'b') { $n++; Set-Content -LiteralPath (Join-Path $repo "f$n.txt") -Value $step; & $git $repo @('add', '-A'); & $git $repo @('commit', '-q', '-m', "c$n") }
    & $git $repo @('tag', '-a', '-m', 'one', 'v1.9.0')
    $n++; Set-Content -LiteralPath (Join-Path $repo "f$n.txt") -Value 'c'; & $git $repo @('add', '-A'); & $git $repo @('commit', '-q', '-m', 'c3')
    & $git $repo @('tag', 'v1.17.0')
    Assert-Equal 'the tag on HEAD is skipped, the older one is found' 'v1.9.0' (Get-PreviousReleaseTag $repo)
    $n++; Set-Content -LiteralPath (Join-Path $repo "f$n.txt") -Value 'd'; & $git $repo @('add', '-A'); & $git $repo @('commit', '-q', '-m', 'c4')
    & $git $repo @('tag', 'v1.17.1')
    & $git $repo @('tag', 'not-a-release')
    $clone = Join-Path $work 'clone'
    & git clone -q --no-tags $repo $clone 2>&1 | Out-Null
    & git -C $clone fetch -q --no-tags origin '+refs/tags/*:refs/tags/*' 2>&1 | Out-Null
    & git -C $clone checkout -q --detach refs/tags/v1.17.1 2>&1 | Out-Null
    Assert-Equal 'previous tag in a tag-push checkout (detached HEAD on the new tag)' 'v1.17.0' (Get-PreviousReleaseTag $clone)
    & git -C $clone checkout -q --detach HEAD~1 2>&1 | Out-Null
    Assert-Equal 'previous tag skips a tag that points at HEAD' 'v1.9.0' (Get-PreviousReleaseTag $clone)
    & git -C $clone tag -d v1.17.0 v1.9.0 v1.17.1 2>&1 | Out-Null
    Assert-Equal 'no release tag at all gives null (deploy all)' $null (Get-PreviousReleaseTag $clone)
    Assert-Equal 'not a repository gives null' $null (Get-PreviousReleaseTag (Join-Path $work 'migrations'))

    # Secrets never get printed.
    Assert-Equal 'token hidden' 'token sbp_***' (Hide-Secrets 'token sbp_0123456789abcdef')
    Assert-Equal 'password hidden' 'postgresql://postgres:***@db.x.supabase.co/postgres' (Hide-Secrets 'postgresql://postgres:hunter2@db.x.supabase.co/postgres')
    Assert-Equal 'jwt hidden' 'key eyJ***' (Hide-Secrets 'key eyJhbGciOiJIUzI1NiJ9.abc_def.ghi-jkl')
    Assert-Equal 'plain text kept' 'Migrations are up to date.' (Hide-Secrets 'Migrations are up to date.')

    # Real output of the installed CLI, read only, from the production project (tools/fixtures).
    $fixtures = Join-Path $PSScriptRoot 'fixtures/release-backend'
    $realRows = ConvertFrom-MigrationListOutput (Get-Content -LiteralPath (Join-Path $fixtures 'migration-list.txt') -Raw)
    Assert-Equal 'real migration list has rows' $true ($realRows.Count -ge 30)
    $realCompare = Compare-Migrations -Rows $realRows -RepositoryVersions @($realRows | ForEach-Object { $_.Local })
    Assert-Equal 'real migration list matches itself' $true $realCompare.UpToDate
    $realFunctions = ConvertFrom-FunctionListOutput (Get-Content -LiteralPath (Join-Path $fixtures 'functions-list.txt') -Raw)
    Assert-Equal 'real function list names' @('assistant', 'connector', 'pc-sign-in') ($realFunctions.Keys | Sort-Object)

    # The project guard and the branch guard.
    Assert-Equal 'ref guard accepts production' $true (Test-LinkedProjectRef -Linked "wkjnauxqwlqhsqrqnkhg`n" -Expected (Get-ProductionProjectRef))
    Assert-Equal 'ref guard refuses another project' $false (Test-LinkedProjectRef -Linked 'abcdefghijklmnopqrst' -Expected (Get-ProductionProjectRef))
    Assert-Equal 'ref guard refuses empty' $false (Test-LinkedProjectRef -Linked '' -Expected (Get-ProductionProjectRef))
    Assert-Equal 'ref guard refuses a different case' $false (Test-LinkedProjectRef -Linked 'WKJNAUXQWLQHSQRQNKHG' -Expected (Get-ProductionProjectRef))
    Assert-Equal 'apply on main' $true (Test-ApplyAllowed -HeadOnMain $true -AllowBranch $false)
    Assert-Equal 'apply on a tag whose commit is not on main is refused' $false (Test-ApplyAllowed -HeadOnMain $false -AllowBranch $false)
    Assert-Equal 'apply on a branch is refused' $false (Test-ApplyAllowed -HeadOnMain $false -AllowBranch $false)
    Assert-Equal 'apply on a branch with -AllowBranch' $true (Test-ApplyAllowed -HeadOnMain $false -AllowBranch $true)

    # The guarded project is the one the docs name.
    $docs = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/setup/cloud-supabase.md') -Raw
    Assert-Equal 'production ref is in cloud-supabase.md' $true $docs.Contains((Get-ProductionProjectRef))
}
finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "$script:failures of $script:count checks failed." -ForegroundColor Red
    exit 1
}
Write-Host "release-backend self-test passed ($script:count checks)." -ForegroundColor Green
