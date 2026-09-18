<#
.SYNOPSIS
    Creates the update channel's manifest signing key (ADR 0004), once.

.DESCRIPTION
      1. Generate an ECDSA P-256 private key with OpenSSL in -KeyFolder (default: the Kingston drive).
         It refuses to replace an existing key.
      2. Write the public key to contracts/keys/release-manifest-public.b64. Commit that file: both
         apps build it in and trust only manifests signed by this key.
      3. Optionally upload the private key as the GOALMAKER_MANIFEST_SIGNING_KEY GitHub Actions
         secret, which the release workflow signs with.

    The private key is not password-protected (CI needs it unattended), so keep the folder offline.
    Losing it means a new key and one manual update of each app; leaking it means rotating it the
    same way.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\setup-update-signing.ps1 -KeyFolder 'E:\goalmaker-signing' -UploadSecret
#>
[CmdletBinding()]
param(
    [string] $KeyFolder = 'E:\goalmaker-signing',
    [switch] $UploadSecret
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$publicKeyFile = Join-Path $repoRoot 'contracts\keys\release-manifest-public.b64'
$privateKey = Join-Path $KeyFolder 'release-manifest-signing.pem'
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if (-not $openssl) {
    $gitOpenssl = Join-Path $env:ProgramFiles 'Git\usr\bin\openssl.exe'
    if (Test-Path -LiteralPath $gitOpenssl) { $openssl = Get-Item -LiteralPath $gitOpenssl }
}
if (-not $openssl) { throw 'openssl not found (it ships with Git for Windows).' }
$opensslPath = if ($openssl -is [System.Management.Automation.CommandInfo]) { $openssl.Source } else { $openssl.FullName }

# OpenSSL reports progress on stderr, which Windows PowerShell 5.1 turns into a terminating error
# under 'Stop'; run it with 'Continue' and judge it by its exit code instead.
function Invoke-OpenSsl([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $opensslPath @Arguments 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "openssl $($Arguments[0]) failed with exit code $LASTEXITCODE" }
    } finally {
        $ErrorActionPreference = $previous
    }
}

if (Test-Path -LiteralPath $privateKey) {
    Write-Host "A signing key already exists at $privateKey - keeping it." -ForegroundColor Yellow
} else {
    New-Item -ItemType Directory -Force -Path $KeyFolder | Out-Null
    Invoke-OpenSsl @('ecparam', '-name', 'prime256v1', '-genkey', '-noout', '-out', $privateKey)
    Write-Host "Created $privateKey" -ForegroundColor Green
}

$temp = [IO.Path]::GetTempFileName()
try {
    Invoke-OpenSsl @('ec', '-in', $privateKey, '-pubout', '-outform', 'DER', '-out', $temp)
    $publicBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($temp))
} finally {
    Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $publicKeyFile) | Out-Null
if ((Test-Path -LiteralPath $publicKeyFile) -and ((Get-Content -LiteralPath $publicKeyFile -Raw).Trim() -ne $publicBase64)) {
    throw "$publicKeyFile holds a DIFFERENT public key. Rotating keys is deliberate: delete that file first."
}
[IO.File]::WriteAllText($publicKeyFile, "$publicBase64`n", $Utf8NoBom)
Write-Host "Wrote $publicKeyFile - commit it." -ForegroundColor Green

$readme = @(
    'GoalMaker update channel: release manifest signing key (ECDSA P-256, PEM)',
    "Public key (also in contracts/keys/release-manifest-public.b64): $publicBase64",
    "File SHA-256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $privateKey).Hash)",
    '',
    'CI signs every release manifest with this key (GitHub secret GOALMAKER_MANIFEST_SIGNING_KEY).',
    'Both apps trust only manifests signed by it. Keep this folder offline.'
)
[IO.File]::WriteAllText((Join-Path $KeyFolder 'README.txt'), (($readme -join "`r`n") + "`r`n"), $Utf8NoBom)

if ($UploadSecret) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh CLI not found on PATH.' }
    Get-Content -LiteralPath $privateKey -Raw | & gh secret set GOALMAKER_MANIFEST_SIGNING_KEY --repo lukr-99/GoalMaker
    if ($LASTEXITCODE -ne 0) { throw "gh secret set failed with exit code $LASTEXITCODE" }
    Write-Host 'Uploaded GOALMAKER_MANIFEST_SIGNING_KEY.' -ForegroundColor Green
}
