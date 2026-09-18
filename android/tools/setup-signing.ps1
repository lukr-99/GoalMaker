<#
.SYNOPSIS
    Android release signing setup for GoalMaker (adapted from Tarot2Go): creates the release key,
    backs it up, and optionally uploads the CI secrets.

.DESCRIPTION
      1. Generate android/keystore.jks with keytool, unless one already exists. Passwords are typed
         hidden (or generated with -GeneratePassword) and reach keytool through environment
         variables, never its command line.
      2. Write android/keystore.properties (git-ignored) so local release builds sign.
      3. Copy the keystore into every -BackupTo folder and verify each copy byte for byte.
      4. Optionally upload GOALMAKER_KEYSTORE_BASE64, GOALMAKER_KEYSTORE_PASSWORD,
         GOALMAKER_KEY_ALIAS and GOALMAKER_KEY_PASSWORD as GitHub Actions secrets.

    Losing this key means installed copies can never be updated in place. Keep the backup offline
    (the Kingston drive) and the password in the password manager, never beside the key.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File android\tools\setup-signing.ps1 -BackupTo 'E:\android-keystores\goalmaker'
#>
[CmdletBinding()]
param(
    [string]   $KeyAlias = 'goalmaker',
    [int]      $ValidityDays = 10000,
    [string[]] $BackupTo = @(),
    [switch]   $GeneratePassword,
    [string]   $DistinguishedName,
    [switch]   $UploadSecrets
)

$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path -Parent $PSScriptRoot
$keystorePath = Join-Path $androidRoot 'keystore.jks'
$propertiesPath = Join-Path $androidRoot 'keystore.properties'
# UTF-8 without a byte-order mark: java.util.Properties would read a BOM as part of the first key and
# Gradle would silently build an unsigned release.
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

function New-RandomPassword {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Read-NewPassword([string]$Label) {
    while ($true) {
        $first = Read-Host "$Label (hidden, at least 6 characters)" -AsSecureString
        $second = Read-Host "Repeat $Label" -AsSecureString
        $a = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($first))
        $b = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($second))
        if ($a -ne $b) { Write-Host 'The two entries differ. Try again.' -ForegroundColor Yellow; continue }
        if ($a.Length -lt 6) { Write-Host 'keytool needs at least 6 characters. Try again.' -ForegroundColor Yellow; continue }
        return $a
    }
}

Write-Host '== GoalMaker Android signing setup ==' -ForegroundColor Cyan
$fingerprint = $null
if (Test-Path -LiteralPath $keystorePath) {
    Write-Host "Keystore already exists at $keystorePath - skipping generation." -ForegroundColor Yellow
} else {
    $password = if ($GeneratePassword) { New-RandomPassword } else { Read-NewPassword 'Keystore password' }
    $dname = $DistinguishedName
    if ([string]::IsNullOrWhiteSpace($dname) -and $GeneratePassword) { $dname = 'CN=GoalMaker, O=GoalMaker' }
    while ([string]::IsNullOrWhiteSpace($dname)) {
        $dname = Read-Host 'Distinguished name (e.g. CN=Lukas Krejci, O=GoalMaker, C=CZ)'
    }

    # PKCS12 keystores ignore a separate key password, so the key uses the store password.
    $env:GM_STOREPASS = $password
    try {
        & keytool -genkeypair -keystore $keystorePath -alias $KeyAlias -keyalg RSA -keysize 2048 `
            -validity $ValidityDays -storepass:env GM_STOREPASS -keypass:env GM_STOREPASS -dname $dname
        if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
        $listing = & keytool -list -v -keystore $keystorePath -alias $KeyAlias -storepass:env GM_STOREPASS
        $fingerprint = ($listing | Select-String -Pattern 'SHA256:\s*(.+)$' | Select-Object -First 1).Matches.Groups[1].Value.Trim()
    } finally {
        Remove-Item Env:GM_STOREPASS -ErrorAction SilentlyContinue
    }

    $properties = "storeFile=keystore.jks`nstorePassword=$password`nkeyAlias=$KeyAlias`nkeyPassword=$password`n"
    [IO.File]::WriteAllText($propertiesPath, $properties, $Utf8NoBom)
    Write-Host 'Wrote android/keystore.properties (git-ignored; it holds the password and stays on this PC).' -ForegroundColor Green
    if ($fingerprint) { Write-Host "Signing certificate SHA-256: $fingerprint" }
    Write-Host 'Put the password in your password manager now.' -ForegroundColor Red
}

$keyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $keystorePath).Hash
$folders = @($BackupTo | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().Trim("'", '"') } | Where-Object { $_ })
foreach ($folder in $folders) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    $target = Join-Path $folder 'keystore.jks'
    if ((Test-Path -LiteralPath $target) -and ((Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash -ne $keyHash)) {
        throw "$target already exists and is a DIFFERENT key. Not overwriting it; move it aside first."
    }
    Copy-Item -LiteralPath $keystorePath -Destination $target -Force
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash -ne $keyHash) {
        throw "Backup copy at $target does not match the original."
    }
    $notes = @(
        'GoalMaker Android release signing key',
        'File: keystore.jks',
        "Key alias: $KeyAlias",
        "File SHA-256: $keyHash",
        "Backed up: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    )
    if ($fingerprint) { $notes += "Signing certificate SHA-256: $fingerprint" }
    $notes += @('', 'The password is deliberately NOT stored here. It is in the password manager.',
        'Losing this key means installed copies of GoalMaker can no longer be updated in place.')
    [IO.File]::WriteAllText((Join-Path $folder 'README.txt'), (($notes -join "`r`n") + "`r`n"), $Utf8NoBom)
    Write-Host "Backed up and verified: $target" -ForegroundColor Green
}
if ($folders.Count -eq 0) {
    Write-Host 'No -BackupTo folders given: back up android/keystore.jks OFFLINE yourself.' -ForegroundColor Red
}

$upload = $UploadSecrets -or ((-not $GeneratePassword) -and (Read-Host 'Upload the GitHub Actions signing secrets with gh? (y/N)') -eq 'y')
if ($upload) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh CLI not found on PATH.' }
    $props = @{}
    Get-Content -LiteralPath $propertiesPath | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() }
    }
    # A short-lived dotenv file, not a pipe (PowerShell would append a newline to the password) and
    # not --body (that would put the password on gh's command line).
    $secrets = [IO.Path]::Combine([IO.Path]::GetTempPath(), "goalmaker-secrets-$([guid]::NewGuid().ToString('N')).env")
    try {
        $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
        $lines = "GOALMAKER_KEYSTORE_BASE64=$b64`nGOALMAKER_KEYSTORE_PASSWORD=$($props['storePassword'])`nGOALMAKER_KEY_ALIAS=$($props['keyAlias'])`nGOALMAKER_KEY_PASSWORD=$($props['keyPassword'])`n"
        [IO.File]::WriteAllText($secrets, $lines, $Utf8NoBom)
        & gh secret set --repo lukr-99/GoalMaker --env-file $secrets
        if ($LASTEXITCODE -ne 0) { throw "gh secret set failed with exit code $LASTEXITCODE" }
    } finally {
        Remove-Item -LiteralPath $secrets -Force -ErrorAction SilentlyContinue
    }
    Write-Host 'Uploaded the four GOALMAKER_KEYSTORE / KEY secrets.' -ForegroundColor Green
}

Write-Host 'Done. Test a signed build with: android\gradlew.bat -p android assembleRelease' -ForegroundColor Cyan
