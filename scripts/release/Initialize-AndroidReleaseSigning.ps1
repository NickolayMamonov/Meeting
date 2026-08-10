[CmdletBinding()]
param(
    [ValidateSet("Preflight", "Execute")]
    [string] $Mode = "Preflight",
    [Parameter(Mandatory = $true)]
    [string] $OfflineBackupDirectory,
    [Parameter(Mandatory = $true)]
    [string] $CredentialHandoffDirectory,
    [switch] $ProvisionGitHubSecrets,
    [string] $GitHubRepository
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-NewPath([string] $Path) {
    if (Test-Path -LiteralPath $Path) {
        throw "Refusing to overwrite existing path: $Path"
    }
}

function Set-OwnerOnlyAcl([string] $Path) {
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $identity,
        "FullControl",
        "ContainerInherit,ObjectInherit",
        "None",
        "Allow"
    )
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

$backup = [IO.Path]::GetFullPath($OfflineBackupDirectory)
$handoff = [IO.Path]::GetFullPath($CredentialHandoffDirectory)
$keystore = Join-Path $handoff "meet-release.jks"
$certificate = Join-Path $handoff "meet-release.cer"
$fingerprint = Join-Path $handoff "meet-release.sha256"
$passwordFile = Join-Path $handoff "meet-release-passwords.txt"
$storePasswordFile = Join-Path $handoff "meet-release-store-password.tmp"
$keyPasswordFile = Join-Path $handoff "meet-release-key-password.tmp"

Write-Host "Offline backup directory: $backup"
Write-Host "Credential handoff directory: $handoff"
Write-Host "Prospective keystore: $keystore"
Write-Host "Prospective certificate: $certificate"
Write-Host "Prospective fingerprint: $fingerprint"
Write-Host "Prospective password handoff: $passwordFile"

if (-not (Test-Path -LiteralPath $backup -PathType Container)) {
    throw "Offline backup directory must already exist"
}
if (Test-Path -LiteralPath $handoff) {
    throw "Credential handoff directory must not already exist"
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool is required"
}
if ($ProvisionGitHubSecrets -and -not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "gh is required when -ProvisionGitHubSecrets is selected"
}

if ($Mode -eq "Preflight") {
    Write-Host "Preflight passed; no signing material was created."
    exit 0
}

$confirmation = Read-Host "Type CREATE-ANDROID-RELEASE-KEY to continue"
if ($confirmation -cne "CREATE-ANDROID-RELEASE-KEY") {
    throw "Explicit confirmation was not provided"
}

New-Item -ItemType Directory -Path $handoff | Out-Null
Set-OwnerOnlyAcl $handoff
Assert-NewPath $keystore
Assert-NewPath $certificate
Assert-NewPath $fingerprint
Assert-NewPath $passwordFile
Assert-NewPath $storePasswordFile
Assert-NewPath $keyPasswordFile

$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng.GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}
$storePassword = New-Secret
$keyPassword = New-Secret
[IO.File]::WriteAllText($storePasswordFile, $storePassword)
[IO.File]::WriteAllText($keyPasswordFile, $keyPassword)
Set-OwnerOnlyAcl $storePasswordFile
Set-OwnerOnlyAcl $keyPasswordFile

& keytool -genkeypair -v -keystore $keystore -storetype JKS `
    -storepass:file $storePasswordFile -keypass:file $keyPasswordFile -alias meet-release `
    -keyalg RSA -keysize 4096 -validity 3650 `
    -dname "CN=Meet Android Release, OU=Release, O=Meet"
if ($LASTEXITCODE -ne 0) { throw "keytool key generation failed" }
& keytool -exportcert -keystore $keystore -storepass:file $storePasswordFile `
    -alias meet-release -file $certificate
if ($LASTEXITCODE -ne 0) { throw "certificate export failed" }
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $certificate).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($fingerprint, $hash + [Environment]::NewLine)
[IO.File]::WriteAllLines($passwordFile, @(
    "STORE_PASSWORD=$storePassword",
    "KEY_PASSWORD=$keyPassword"
))
Set-OwnerOnlyAcl $certificate
Set-OwnerOnlyAcl $fingerprint
Set-OwnerOnlyAcl $passwordFile

$backupKeystore = Join-Path $backup "meet-release.jks"
Assert-NewPath $backupKeystore
Copy-Item -LiteralPath $keystore -Destination $backupKeystore
if ((Get-FileHash $keystore).Hash -ne (Get-FileHash $backupKeystore).Hash) {
    throw "Offline backup verification failed"
}

if ($ProvisionGitHubSecrets) {
    function Set-GitHubEnvironmentSecret([string] $Name, [scriptblock] $WriteValue) {
        $arguments = @("secret", "set", $Name, "--env", "android-release")
        if (-not [string]::IsNullOrWhiteSpace($GitHubRepository)) {
            $arguments += @("--repo", $GitHubRepository)
        }
        & $WriteValue | & gh @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "gh secret set failed for environment secret '$Name'"
        }
    }

    Set-GitHubEnvironmentSecret "RELEASE_KEYSTORE_BASE64" {
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
    }
    Set-GitHubEnvironmentSecret "RELEASE_KEYSTORE_PASSWORD" {
        [IO.File]::ReadAllText($storePasswordFile)
    }
    Set-GitHubEnvironmentSecret "RELEASE_KEY_PASSWORD" {
        [IO.File]::ReadAllText($keyPasswordFile)
    }
    Write-Host "Provisioned signing secrets to the android-release Environment through gh stdin."
}

Write-Host "Generated and backed up one meet-release identity with fingerprint $hash."
Write-Host "Provisioning is intentionally operator-controlled; CI has no key-generation path."
