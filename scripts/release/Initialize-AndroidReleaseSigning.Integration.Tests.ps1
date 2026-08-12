$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1) {
    throw "Disposable integration tests require Windows PowerShell 5.1"
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "Temurin keytool is required"
}
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$keytoolVersion = (& keytool -J-version 2>&1 | Out-String)
$ErrorActionPreference = $previousErrorActionPreference
if ($keytoolVersion -notmatch '21\.') {
    throw "Temurin JDK 21 keytool is required"
}

$scriptPath = Join-Path $PSScriptRoot "Initialize-AndroidReleaseSigning.ps1"
. $scriptPath -Library

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function New-Fixture {
    $root = Join-Path ([IO.Path]::GetTempPath()) `
        ("meeting-signing-integration-" + [guid]::NewGuid().ToString("N"))
    $backup = Join-Path $root "backup"
    $handoff = Join-Path $root "handoff"
    New-Item -ItemType Directory -Path $backup | Out-Null
    return [pscustomobject]@{ Root = $root; Backup = $backup; Handoff = $handoff }
}

function New-IntegrationHooks {
    $hooks = @{
        Confirm = { "CREATE-ANDROID-RELEASE-KEY" }
        Secret = {
            param([string] $Name)
            if ($Name -ceq "STORE_PASSWORD") { return "disposable-store-password" }
            return "disposable-key-password"
        }
    }
    return $hooks
}

function Assert-NoSensitiveOutput([string] $Output) {
    foreach ($term in @(
        "disposable-store-password", "disposable-key-password",
        "PRIVATE KEY", "BEGIN", "keystore", "CREATE-ANDROID-RELEASE-KEY"
    )) {
        Assert-True ($Output -notmatch [regex]::Escape($term)) `
            "integration output disclosed $term"
    }
}

$aclProbe = Join-Path ([IO.Path]::GetTempPath()) ("meeting-acl-probe-" + [guid]::NewGuid().ToString("N"))
try {
    [IO.File]::WriteAllText($aclProbe, "probe")
    Set-OwnerOnlyAcl $aclProbe $false @{}
} catch {
    Write-Warning "Disposable integration is environment-blocked: owner-only ACL probe unavailable."
    exit 0
} finally {
    if (Test-Path -LiteralPath $aclProbe) {
        Remove-Item -LiteralPath $aclProbe -Force -ErrorAction SilentlyContinue
    }
}

$fixture = New-Fixture
try {
    $aclProbeDirectory = Join-Path $fixture.Root "acl-probe"
    New-Item -ItemType Directory -Path $aclProbeDirectory | Out-Null
    try {
        Set-OwnerOnlyAcl $aclProbeDirectory $true @{}
    } catch {
        Write-Warning "Disposable integration is environment-blocked: fixture ACL probe unavailable."
        return
    }
    Remove-Item -LiteralPath $aclProbeDirectory -Recurse -Force

    $approvedBackup = "D:\meets\android-signing-backup"
    $approvedHandoff = "C:\Users\whysoezzy\Documents\meets\android-release-handoff"
    Assert-True (-not (Test-Path -LiteralPath $fixture.Root -PathType Leaf)) "fixture root is a file"
    Assert-True (-not (Test-Path -LiteralPath $fixture.Backup -PathType Container) -or
        $fixture.Backup -notlike "$approvedBackup*") "fixture used approved backup path"
    Assert-True ($fixture.Handoff -notlike "$approvedHandoff*") "fixture used approved handoff path"

    $baselineProbeRoot = Join-Path $fixture.Root "baseline-probe"
    New-Item -ItemType Directory -Path $baselineProbeRoot | Out-Null
    $baselineStore = Join-Path $baselineProbeRoot "store-password"
    $baselineKey = Join-Path $baselineProbeRoot "key-password"
    $baselineKeystore = Join-Path $baselineProbeRoot "baseline.jks"
    [IO.File]::WriteAllText($baselineStore, "baseline-store-password")
    [IO.File]::WriteAllText($baselineKey, "baseline-key-password")
    $baselineFailureObserved = $false
    try {
        & keytool -genkeypair -v -keystore $baselineKeystore -storetype JKS `
            -storepass:file $baselineStore -keypass:file $baselineKey `
            -alias meet-release -keyalg RSA -keysize 2048 -validity 1 `
            -dname "CN=MEE3-38 Baseline Probe, OU=NON-RELEASE, O=Meeting Tests" `
            2>&1 | Out-String | Out-Null
    } catch {
        $baselineFailureObserved = $true
    }
    Assert-True $baselineFailureObserved `
        "baseline PS5.1 native-stderr failure was not reproduced"
    if (Test-Path -LiteralPath $baselineKeystore) {
        Remove-Item -LiteralPath $baselineKeystore -Force
    }

    $identity = @{
        ValidityDays = 1
        DistinguishedName = "CN=MEE3-38 Disposable Integration Test, OU=NON-RELEASE, O=Meeting Tests"
    }
    $hooks = New-IntegrationHooks
    $output = try {
        & {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                -BackupDirectory $fixture.Backup -HandoffDirectory $fixture.Handoff `
                -Provision $false -Repository "" -Hooks $hooks `
                -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                -IntegrationTestIdentity $identity
        } 2>&1 | Out-String
    } catch {
        throw
    }
    Assert-True ($output -notmatch "disposable-store-password|disposable-key-password|PRIVATE KEY|BEGIN") `
        "successful integration output disclosed sensitive material"
    Assert-True (@(Get-ChildItem -LiteralPath $fixture.Handoff -Force).Count -eq 4) `
        "successful integration handoff is incomplete"
    Assert-True (@(Get-ChildItem -LiteralPath $fixture.Backup -Force).Count -eq 4) `
        "successful integration backup is incomplete"
    $metadata = Read-RecoveryMetadata (Join-Path $fixture.Handoff "meet-release-passwords.txt")
    $previousKeytoolPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $identityText = (& keytool -list -v -keystore (Join-Path $fixture.Handoff "meet-release.jks") `
        -storepass $metadata.StorePassword 2>&1 | Out-String)
    $ErrorActionPreference = $previousKeytoolPreference
    Assert-True ($identityText -match "Valid from:") "disposable identity validity was not reported"
    Assert-True ($identityText -match "MEE3-38 Disposable Integration Test") `
        "disposable identity subject was not present"

    Remove-Item -LiteralPath $fixture.Handoff -Recurse -Force
    New-Item -ItemType Directory -Path $fixture.Handoff | Out-Null
    $sentinel = Join-Path $fixture.Backup "sentinel.txt"
    [IO.File]::WriteAllText($sentinel, "pre-existing")
    $failureHooks = New-IntegrationHooks
    $failureHooks.Secret = {
        param([string] $Name)
        if ($Name -ceq "STORE_PASSWORD") { return "disposable-store-password" }
        throw "disposable pre-commit failure"
    }
    $failureOutput = try {
        & {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                -BackupDirectory $fixture.Backup -HandoffDirectory $fixture.Handoff `
                -Provision $false -Repository "" -Hooks $failureHooks `
                -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                -IntegrationTestIdentity $identity
        } 2>&1 | Out-String
    } catch {
        $_ | Out-String
    }
    Assert-NoSensitiveOutput $failureOutput
    Assert-True (Test-Path -LiteralPath $sentinel -PathType Leaf) `
        "pre-existing sentinel was removed"
    Write-Host "Disposable Windows signing integration tests passed."
} finally {
    if (Test-Path -LiteralPath $fixture.Root) {
        Remove-Item -LiteralPath $fixture.Root -Recurse -Force
    }
}
