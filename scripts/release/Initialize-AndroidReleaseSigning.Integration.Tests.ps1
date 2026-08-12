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

function ConvertTo-WindowsProcessArgument([string] $Value) {
    if ($null -eq $Value -or $Value.Length -eq 0) {
        return '""'
    }
    if ($Value -notmatch '[\s"]') {
        return $Value
    }

    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('"')
    $backslashes = 0
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $character = $Value[$index]
        if ($character -ceq '\') {
            $backslashes++
            continue
        }
        if ($character -ceq '"') {
            [void]$builder.Append(('\' * (2 * $backslashes + 1) -join ''))
            [void]$builder.Append('"')
            $backslashes = 0
            continue
        }
        if ($backslashes -gt 0) {
            [void]$builder.Append(('\' * $backslashes -join ''))
            $backslashes = 0
        }
        [void]$builder.Append($character)
    }
    if ($backslashes -gt 0) {
        [void]$builder.Append(('\' * (2 * $backslashes) -join ''))
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Invoke-GitText([string[]] $Arguments) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "git"
    $startInfo.Arguments = (($Arguments | ForEach-Object {
        ConvertTo-WindowsProcessArgument $_
    }) -join " ")
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Unable to start git"
        }
        $output = $process.StandardOutput.ReadToEnd()
        $null = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = $output
        }
    } finally {
        $process.Dispose()
    }
}

function Get-VerifiedBaselineScript {
    param(
        [string] $Commit,
        [string] $Path,
        [string] $OutputPath
    )

    $commitResult = Invoke-GitText @("rev-parse", "--verify", "${Commit}^{commit}")
    $resolvedCommit = $commitResult.Output.Trim()
    Assert-True ($commitResult.ExitCode -eq 0 -and $resolvedCommit -ceq $Commit) `
        "baseline commit is unavailable or did not resolve to the requested exact SHA"

    $commitObjectResult = Invoke-GitText @("cat-file", "-e", "${Commit}^{commit}")
    Assert-True ($commitObjectResult.ExitCode -eq 0) `
        "baseline commit object is unavailable"

    $pathObjectResult = Invoke-GitText @("cat-file", "-e", "${Commit}:${Path}")
    Assert-True ($pathObjectResult.ExitCode -eq 0) `
        "baseline script path is unavailable at the requested commit"

    $expectedBlobResult = Invoke-GitText @("rev-parse", "${Commit}:${Path}")
    $expectedBlob = $expectedBlobResult.Output.Trim()
    Assert-True ($expectedBlobResult.ExitCode -eq 0 -and
        $expectedBlob -match '^[0-9a-f]{40}$') `
        "baseline script provenance could not be resolved"

    $showResult = Invoke-GitText @(
        "show", "--format=", "--no-ext-diff", "--no-textconv", "${Commit}:${Path}"
    )
    Assert-True ($showResult.ExitCode -eq 0) `
        "baseline script extraction failed"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$showResult.Output)) `
        "baseline script extraction produced empty content"

    [IO.File]::WriteAllText(
        $OutputPath,
        [string]$showResult.Output,
        (New-Object Text.UTF8Encoding($false))
    )
    Assert-True (Test-Path -LiteralPath $OutputPath -PathType Leaf) `
        "baseline script extraction produced no file"

    $actualBlobResult = Invoke-GitText @("hash-object", $OutputPath)
    $actualBlob = $actualBlobResult.Output.Trim()
    Assert-True ($actualBlobResult.ExitCode -eq 0 -and
        $actualBlob -ceq $expectedBlob) `
        "extracted baseline script provenance does not match the requested commit and path"

    return [pscustomobject]@{
        Commit = $resolvedCommit
        Path = $Path
        Blob = $expectedBlob
        OutputPath = $OutputPath
    }
}

function Invoke-VerifiedBaselineChild {
    param(
        [string] $Commit,
        [string] $Path,
        [string] $OutputPath,
        [string] $ChildScriptPath,
        [scriptblock] $ChildScript
    )

    $evidence = Get-VerifiedBaselineScript -Commit $Commit -Path $Path `
        -OutputPath $OutputPath
    $childText = & $ChildScript $evidence.OutputPath
    [IO.File]::WriteAllText($ChildScriptPath, [string]$childText)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "powershell.exe"
    $startInfo.Arguments = ((@(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $ChildScriptPath
    ) | ForEach-Object {
        ConvertTo-WindowsProcessArgument $_
    }) -join " ")
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Unable to start baseline child"
        }
        $childOutput = $process.StandardOutput.ReadToEnd()
        $childError = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $childResult = [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = $childOutput + $childError
        }
    } finally {
        $process.Dispose()
    }
    return [pscustomobject]@{
        Evidence = $evidence
        ChildResult = $childResult
    }
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
    $baselineScriptPath = "scripts/release/Initialize-AndroidReleaseSigning.ps1"
    $baselineRoot = Join-Path $successFixture.Root "baseline"
    New-Item -ItemType Directory -Path $baselineRoot | Out-Null
    $baselineScript = Join-Path $baselineRoot "Initialize-AndroidReleaseSigning.baseline.ps1"
    foreach ($case in @(
        @{
            Name = "missing baseline commit object"
            Commit = "0000000000000000000000000000000000000000"
            Path = $baselineScriptPath
            OutputPath = Join-Path $baselineRoot "missing-object.ps1"
        },
        @{
            Name = "missing baseline script path"
            Commit = $baselineSha
            Path = "scripts/release/does-not-exist.ps1"
            OutputPath = Join-Path $baselineRoot "missing-path.ps1"
        },
        @{
            Name = "baseline extraction failure"
            Commit = $baselineSha
            Path = $baselineScriptPath
            OutputPath = Join-Path $baselineRoot "extraction-failure"
        }
    )) {
        if ($case.Name -ceq "baseline extraction failure") {
            New-Item -ItemType Directory -Path $case.OutputPath | Out-Null
        }
        $childMarker = Join-Path $baselineRoot `
            ($case.Name.Replace(" ", "-") + "-child-invoked")
        $childScript = Join-Path $baselineRoot `
            ($case.Name.Replace(" ", "-") + "-child.ps1")
        $markerLiteral = ConvertTo-PowerShellLiteral $childMarker
        $threw = $false
        try {
            Invoke-VerifiedBaselineChild -Commit $case.Commit -Path $case.Path `
                -OutputPath $case.OutputPath -ChildScriptPath $childScript `
                -ChildScript {
                    param([string] $VerifiedScriptPath)
                    @"
[IO.File]::WriteAllText($markerLiteral, "child-invoked")
"@
                } | Out-Null
        } catch {
            $threw = $true
        }
        Assert-True $threw "$($case.Name) was accepted"
        Assert-True (-not (Test-Path -LiteralPath $childMarker)) `
            "$($case.Name) launched the baseline child before verification"
    }

    $spacedRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ("meeting signing baseline " + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $spacedRoot | Out-Null
    try {
        $spacedScript = Join-Path $spacedRoot "baseline script.ps1"
        $spacedEvidence = Get-VerifiedBaselineScript -Commit $baselineSha `
            -Path $baselineScriptPath -OutputPath $spacedScript
        Assert-True ($spacedEvidence.Commit -ceq $baselineSha) `
            "baseline extraction failed for a path containing spaces"
    } finally {
        Remove-Item -LiteralPath $spacedRoot -Recurse -Force `
            -ErrorAction SilentlyContinue
    }

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
    $baselineInvocation = Invoke-VerifiedBaselineChild -Commit $baselineSha `
        -Path $baselineScriptPath -OutputPath $baselineScript `
        -ChildScriptPath (Join-Path $baselineRoot "baseline-probe.ps1") `
        -ChildScript {
            param([string] $VerifiedScriptPath)
            @"
`$ErrorActionPreference = "Stop"
. $(ConvertTo-PowerShellLiteral $VerifiedScriptPath) -Library
Invoke-Keytool @{} @($(($sameMechanismArguments | ForEach-Object {
    ConvertTo-PowerShellLiteral $_
}) -join ", ")) @() | Out-Null
"@
        }
    $baselineEvidence = $baselineInvocation.Evidence
    Assert-True ($baselineEvidence.Commit -ceq $baselineSha) `
        "approved baseline did not resolve to the requested exact SHA"
    $expectedBaselineBlob = $baselineEvidence.Blob
    $baselineOutput = $baselineInvocation.ChildResult.Output
    $baselineExitCode = $baselineInvocation.ChildResult.ExitCode
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
