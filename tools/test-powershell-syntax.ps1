[CmdletBinding()]
param(
    [string[]]$Roots
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$scripts = [System.Collections.Generic.List[System.IO.FileInfo]]::new()

if (-not $Roots) {
    $Roots = @('templates', 'examples', 'tools', 'installer') | Where-Object {
        Test-Path -LiteralPath (Join-Path $repositoryRoot $_) -PathType Container
    }
}

foreach ($rootValue in $Roots) {
    $root = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $rootValue))
    $prefix = $repositoryRoot.TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar
    $isRepositoryRoot = $root.Equals($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)
    if (-not $isRepositoryRoot -and -not $root.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Syntax-check root must remain under the repository: $root"
    }
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "Syntax-check root does not exist: $root"
    }
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.ps1' | ForEach-Object {
        $scripts.Add($_)
    }
}

$syntaxErrors = [System.Collections.Generic.List[string]]::new()
foreach ($script in $scripts | Sort-Object FullName -Unique) {
    $tokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile(
        $script.FullName,
        [ref]$tokens,
        [ref]$parseErrors
    ) | Out-Null
    foreach ($parseError in $parseErrors) {
        $relative = [System.IO.Path]::GetRelativePath($repositoryRoot, $script.FullName)
        $syntaxErrors.Add("$relative`:$($parseError.Extent.StartLineNumber): $($parseError.Message)")
    }
}

if ($syntaxErrors.Count -gt 0) {
    Write-Host 'PowerShell syntax validation failed:' -ForegroundColor Red
    $syntaxErrors | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "PowerShell syntax passed for $($scripts.Count) scripts." -ForegroundColor Green
