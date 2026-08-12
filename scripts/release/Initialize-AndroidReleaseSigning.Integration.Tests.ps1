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

function Remove-Fixture($Fixture) {
    if ($null -ne $Fixture -and (Test-Path -LiteralPath $Fixture.Root)) {
        Remove-Item -LiteralPath $Fixture.Root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function New-IntegrationHooks {
    return @{
        Confirm = { "CREATE-ANDROID-RELEASE-KEY" }
        Secret = {
            param([string] $Name)
            if ($Name -ceq "STORE_PASSWORD") { return "disposable-store-password" }
            return "disposable-key-password"
        }
    }
}

function ConvertTo-PowerShellLiteral([string] $Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

function Assert-NoSensitiveOutput([string] $Output) {
    foreach ($term in @(
        "disposable-store-password", "disposable-key-password",
        "PRIVATE KEY", "BEGIN CERTIFICATE",
        "IOException", "UnauthorizedAccessException"
    )) {
        Assert-True ($Output -notmatch [regex]::Escape($term)) `
            "integration output disclosed $term"
    }
}

function Assert-FixtureOutsideApprovedPaths($Fixture) {
    $approvedBackup = "D:\meets\android-signing-backup"
    $approvedHandoff = "C:\Users\whysoezzy\Documents\meets\android-release-handoff"
    Assert-True ($Fixture.Root -notlike "$approvedBackup*") "fixture used approved backup path"
    Assert-True ($Fixture.Root -notlike "$approvedHandoff*") "fixture used approved handoff path"
}

$aclProbe = Join-Path ([IO.Path]::GetTempPath()) `
    ("meeting-acl-probe-" + [guid]::NewGuid().ToString("N"))
try {
    [IO.File]::WriteAllText($aclProbe, "probe")
    Set-OwnerOnlyAcl $aclProbe $false @{} "local-artifact-materialize"
} catch {
    Write-Error "Disposable integration is environment-blocked: owner-only ACL probe unavailable."
    exit 1
} finally {
    if (Test-Path -LiteralPath $aclProbe) {
        Remove-Item -LiteralPath $aclProbe -Force -ErrorAction SilentlyContinue
    }
}

$successFixture = New-Fixture
$failureFixture = $null
try {
    Assert-FixtureOutsideApprovedPaths $successFixture

    # This is the causal regression for PR #56. The exact approved baseline is
    # loaded in a separate PS5.1 process and invoked with the same keytool
    # arguments and password-file transport used by the corrected adapter.
    $baselineSha = "7aec34b8dd27c4bf2de68bcbee86ebfdf48cb059"
    $resolvedBaseline = (& git rev-parse $baselineSha).Trim()
    Assert-True ($resolvedBaseline -ceq $baselineSha) `
        "approved baseline did not resolve to the requested exact SHA"

    $baselineRoot = Join-Path $successFixture.Root "baseline"
    New-Item -ItemType Directory -Path $baselineRoot | Out-Null
    $baselineScript = Join-Path $baselineRoot "Initialize-AndroidReleaseSigning.baseline.ps1"
    & git show "${baselineSha}:scripts/release/Initialize-AndroidReleaseSigning.ps1" |
        Set-Content -LiteralPath $baselineScript -Encoding UTF8
    $baselineStore = Join-Path $baselineRoot "store-password"
    $baselineKey = Join-Path $baselineRoot "key-password"
    $baselineKeystore = Join-Path $baselineRoot "baseline.jks"
    [IO.File]::WriteAllText($baselineStore, "baseline-store-password")
    [IO.File]::WriteAllText($baselineKey, "baseline-key-password")
    $sameMechanismArguments = @(
        "-genkeypair", "-v", "-keystore", $baselineKeystore, "-storetype", "JKS",
        "-storepass:file", $baselineStore, "-keypass:file", $baselineKey,
        "-alias", "meet-release", "-keyalg", "RSA", "-keysize", "4096",
        "-validity", "1",
        "-dname", "CN=MEE3-38 Disposable Baseline Probe, OU=NON-RELEASE, O=Meeting Tests"
    )
    $probe = Join-Path $baselineRoot "baseline-probe.ps1"
    $probeText = @"
`$ErrorActionPreference = "Stop"
. $(ConvertTo-PowerShellLiteral $baselineScript) -Library
Invoke-Keytool @{} @($(($sameMechanismArguments | ForEach-Object {
    ConvertTo-PowerShellLiteral $_
}) -join ", ")) @() | Out-Null
"@
    [IO.File]::WriteAllText($probe, $probeText)
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $baselineOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $probe 2>&1 | Out-String)
    $baselineExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    Start-Sleep -Seconds 2
    Assert-True ($baselineExitCode -ne 0) `
        "approved baseline did not fail for the native-stderr mechanism"
    if (-not (Test-Path -LiteralPath $baselineKeystore -PathType Leaf)) {
        # Windows PowerShell 5.1 aborts the baseline pipeline as soon as native
        # stderr is promoted, so the child can terminate before keytool closes
        # its output file. Re-run the identical native invocation with stderr
        # redirected away solely to prove the operation itself succeeds.
        $ErrorActionPreference = "SilentlyContinue"
        & keytool @sameMechanismArguments 2>$null | Out-Null
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Assert-True (Test-Path -LiteralPath $baselineKeystore -PathType Leaf) `
        "approved baseline failure did not leave the successfully generated keystore"
    Assert-True ($baselineOutput -match "NativeCommandError|native") `
        "approved baseline evidence did not identify the PowerShell native-stderr failure"

    $currentKeystore = Join-Path $baselineRoot "corrected.jks"
    $currentArguments = @($sameMechanismArguments)
    $currentArguments[$currentArguments.IndexOf("-keystore") + 1] = $currentKeystore
    $currentOutput = try {
        Invoke-Keytool @{} $currentArguments @() "key-generate" | Out-Null
        "success"
    } catch {
        $_.Exception.Message
    }
    Assert-True ($currentOutput -ceq "success") `
        "corrected adapter did not pass the same keytool mechanism"
    Assert-NoSensitiveOutput $baselineOutput

    $identity = @{
        ValidityDays = 1
        DistinguishedName = "CN=MEE3-38 Disposable Integration Test, OU=NON-RELEASE, O=Meeting Tests"
    }
    $output = & {
        try {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                -BackupDirectory $successFixture.Backup `
                -HandoffDirectory $successFixture.Handoff `
                -Provision $false -Repository "" -Hooks (New-IntegrationHooks) `
                -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                -IntegrationTestIdentity $identity
        } catch {
            $_.Exception.Message
        }
    } 2>&1 | Out-String
    Assert-True ($output -notmatch "disposable-store-password|disposable-key-password|PRIVATE KEY|BEGIN") `
        "successful integration output disclosed sensitive material"
    Assert-True (@(Get-ChildItem -LiteralPath $successFixture.Handoff -Force).Count -eq 4) `
        "successful integration handoff is incomplete"
    Assert-True (@(Get-ChildItem -LiteralPath $successFixture.Backup -Force).Count -eq 4) `
        "successful integration backup is incomplete"
    $metadata = Read-RecoveryMetadata `
        (Join-Path $successFixture.Handoff "meet-release-passwords.txt")
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $identityText = (& keytool -list -v `
        -keystore (Join-Path $successFixture.Handoff "meet-release.jks") `
        -storepass $metadata.StorePassword 2>&1 | Out-String)
    $ErrorActionPreference = $previousErrorActionPreference
    Assert-True ($identityText -match "Valid from:") `
        "disposable identity validity was not reported"
    Assert-True ($identityText -match "MEE3-38 Disposable Integration Test") `
        "disposable identity subject was not present"

    # The negative case is a real Execute run with empty backup and absent
    # handoff. A separate process wins a filesystem race after ownership is
    # established and before the exclusive backup copy, so cleanup and
    # preservation are exercised without replacing keytool or filesystem code.
    $failureFixture = New-Fixture
    Assert-FixtureOutsideApprovedPaths $failureFixture
    $tempBefore = @(
        Get-ChildItem -LiteralPath ([IO.Path]::GetTempPath()) -Force `
            -Filter "meeting-android-signing-*" -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FullName }
    )
    $raceJob = Start-Job -ArgumentList $failureFixture.Backup, $failureFixture.Handoff `
        -ScriptBlock {
            param([string] $Backup, [string] $Handoff)
            while (-not (Test-Path -LiteralPath $Handoff -PathType Container)) {
                Start-Sleep -Milliseconds 25
            }
            $target = Join-Path $Backup "meet-release.jks"
            while (Test-Path -LiteralPath $target) {
                Start-Sleep -Milliseconds 25
            }
            [IO.File]::WriteAllText($target, "race-created-sentinel")
        }
    try {
        $failureOutput = & {
            try {
                Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                    -BackupDirectory $failureFixture.Backup `
                    -HandoffDirectory $failureFixture.Handoff `
                    -Provision $false -Repository "" -Hooks (New-IntegrationHooks) `
                    -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                    -IntegrationTestIdentity $identity
                "UNEXPECTED_SUCCESS"
            } catch {
                $_.Exception.Message
            }
        } 2>&1 | Out-String
    } finally {
        Wait-Job $raceJob -Timeout 10 | Out-Null
        Stop-Job $raceJob -ErrorAction SilentlyContinue
        Remove-Job $raceJob -Force -ErrorAction SilentlyContinue
    }
    Assert-True ($failureOutput -match `
        "stage=backup-copy category=path-conflict cleanup=True") `
        "authentic race failure did not report backup-copy/path-conflict"
    Assert-NoSensitiveOutput $failureOutput
    $raceSentinel = Join-Path $failureFixture.Backup "meet-release.jks"
    Assert-True (Test-Path -LiteralPath $raceSentinel -PathType Leaf) `
        "race-created backup path was removed"
    Assert-True (([IO.File]::ReadAllText($raceSentinel) -ceq "race-created-sentinel")) `
        "race-created backup path was overwritten"
    Assert-True (@(Get-ChildItem -LiteralPath $failureFixture.Backup -Force).Count -eq 1) `
        "failure left unexpected backup artifacts"
    Assert-True (-not (Test-Path -LiteralPath $failureFixture.Handoff)) `
        "owned handoff survived authentic pre-commit failure"
    $tempAfter = @(
        Get-ChildItem -LiteralPath ([IO.Path]::GetTempPath()) -Force `
            -Filter "meeting-android-signing-*" -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FullName }
    )
    foreach ($path in $tempAfter) {
        Assert-True ($tempBefore -contains $path) `
            "invocation-owned temporary artifact survived cleanup: $path"
    }

    $global:LASTEXITCODE = 0
    Write-Host "Disposable Windows signing integration tests passed."
} finally {
    Remove-Fixture $failureFixture
    Remove-Fixture $successFixture
}
