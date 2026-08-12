$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptPath = Join-Path $PSScriptRoot "Initialize-AndroidReleaseSigning.ps1"
. $scriptPath -Library

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Assert-Throws([scriptblock] $Action, [string] $Message) {
    $thrown = $false
    try {
        & $Action
    } catch {
        $thrown = $true
    }
    Assert-True $thrown $Message
}

function Assert-InputValidationOnly([scriptblock] $Action, [string] $Message) {
    try {
        & $Action
    } catch {
        Assert-True ($_.Exception.Data["SigningStage"] -ceq "input-validation") `
            "$Message did not fail at input-validation"
        Assert-True ($_.Exception.Data["SigningCategory"] -ceq "validation") `
            "$Message did not fail with validation category"
        return
    }
    throw "ASSERTION FAILED: $Message was accepted"
}

function New-Fixture([string] $Name) {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("android-signing-" + $Name + "-" + [guid]::NewGuid().ToString("N"))
    $backup = Join-Path $root "backup"
    $handoff = Join-Path $root "handoff"
    New-Item -ItemType Directory -Path $backup | Out-Null
    return [pscustomobject]@{
        Root = $root
        Backup = $backup
        Handoff = $handoff
    }
}

function Remove-Fixture($Fixture) {
    if (Test-Path -LiteralPath $Fixture.Root) {
        Remove-Item -LiteralPath $Fixture.Root -Recurse -Force
    }
}

function New-TestHooks {
    param(
        [System.Collections.ArrayList] $GhCalls,
        [bool] $FailGh = $false,
        [System.Collections.ArrayList] $AclCalls = $(New-Object System.Collections.ArrayList)
    )
    $keytoolCalls = New-Object System.Collections.ArrayList
    $copyCalls = New-Object System.Collections.ArrayList
    $aclPaths = New-Object System.Collections.ArrayList
    $copySeams = New-Object System.Collections.ArrayList
    $confirm = { "CREATE-ANDROID-RELEASE-KEY" }.GetNewClosure()
    $secret = {
            param([string] $Name)
            if ($Name -eq "STORE_PASSWORD") { return "store-password-fixture" }
            return "key-password-fixture"
        }.GetNewClosure()
    $acl = {
            param([string] $Path, [bool] $IsDirectory)
            [void]$aclPaths.Add($Path)
            [void]$AclCalls.Add([pscustomobject]@{ Path = $Path; IsDirectory = $IsDirectory })
        }.GetNewClosure()
    $keytool = {
            param([string[]] $Arguments, [string[]] $PasswordInput)
            [void]$keytoolCalls.Add($Arguments)
            if ($Arguments -contains "-genkeypair") {
                $keystore = $Arguments[$Arguments.IndexOf("-keystore") + 1]
                [IO.File]::WriteAllBytes($keystore, [Text.Encoding]::UTF8.GetBytes("fixture-keystore"))
                return ""
            }
            if ($Arguments -contains "-exportcert" -and $Arguments -contains "-file") {
                $certificate = $Arguments[$Arguments.IndexOf("-file") + 1]
                [IO.File]::WriteAllBytes($certificate, [Text.Encoding]::UTF8.GetBytes("fixture-certificate"))
                return ""
            }
            if ($Arguments -contains "-list") {
                if ($Arguments -contains "-keypass" -or $Arguments -contains "-keypass:file" -or
                    $Arguments -notcontains "store-password-fixture") {
                    throw "invalid fixture password"
                }
                return ""
            }
            if ($Arguments -contains "-certreq") {
                if ($Arguments -contains "-keypass" -and
                    ($Arguments -notcontains "store-password-fixture" -or
                        $Arguments -notcontains "key-password-fixture")) {
                    throw "invalid fixture password"
                }
                return ""
            }
            return ""
        }.GetNewClosure()
    $certificateBytes = {
            param([string] $Keystore, [string] $StorePassword, [string] $Alias)
            if ($StorePassword -ne "store-password-fixture" -or $Alias -cne "meet-release") {
                throw "invalid fixture identity"
            }
            return [Text.Encoding]::UTF8.GetBytes("fixture-certificate")
        }.GetNewClosure()
    $gh = {
            param([string[]] $Arguments, [string] $InputValue)
            [void]$GhCalls.Add([pscustomobject]@{
                Arguments = $Arguments
                Input = $InputValue
            })
            if ($FailGh) {
                throw "injected GitHub failure"
            }
            return [pscustomobject]@{ ExitCode = 0 }
        }.GetNewClosure()
    $copy = {
            param([string] $Source, [string] $Destination, [object] $DestinationStream)
            [void]$copySeams.Add([pscustomobject]@{ Source = $Source; Destination = $Destination })
            [void]$copyCalls.Add([pscustomobject]@{ Source = $Source; Destination = $Destination })
            $sourceStream = [IO.File]::OpenRead($Source)
            try { $sourceStream.CopyTo($DestinationStream) } finally { $sourceStream.Dispose() }
        }.GetNewClosure()
    $hash = {
            param([string] $Path)
            (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
        }.GetNewClosure()
    return @{
        Confirm = $confirm
        Secret = $secret
        Acl = $acl
        Keytool = $keytool
        CertificateBytes = $certificateBytes
        Gh = $gh
        Copy = $copy
        Hash = $hash
        KeytoolCalls = $keytoolCalls
        CopyCalls = $copyCalls
        AclPaths = $aclPaths
        CopySeams = $copySeams
    }
}

function Invoke-TestCase([string] $Name, [scriptblock] $Body) {
    try {
        & $Body
        Write-Host "PASS: $Name"
    } catch {
        Write-Error "FAIL: $Name - $($_.Exception.Message)`n$($_.ScriptStackTrace)"
        throw
    }
}

Invoke-TestCase "canonical equal and nested paths reject before artifacts" {
    foreach ($pair in @(
        @("C:\Root\Backup", "c:\root\backup"),
        @("C:\Root\Backup", "C:\Root\Backup\handoff"),
        @("C:\Root\Backup\handoff", "C:\Root\Backup")
    )) {
        Assert-Throws { Assert-SeparateSigningPaths $pair[0] $pair[1] } "path topology was accepted"
    }
    $fixture = New-Fixture "reparse"
    try {
        $alias = Join-Path $fixture.Root "alias"
        New-Item -ItemType Junction -Path $alias -Target $fixture.Backup | Out-Null
        Assert-Throws { Assert-NoReparsePointPath $alias } "reparse-point path was accepted"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "ACL construction separates directory and file inheritance" {
    $directoryRule = Get-OwnerOnlyAclRule $true
    $fileRule = Get-OwnerOnlyAclRule $false
    Assert-True (($directoryRule.InheritanceFlags -band [System.Security.AccessControl.InheritanceFlags]::ContainerInherit) -ne 0) `
        "directory rule lacks container inheritance"
    Assert-True (($directoryRule.InheritanceFlags -band [System.Security.AccessControl.InheritanceFlags]::ObjectInherit) -ne 0) `
        "directory rule lacks object inheritance"
    Assert-True ($fileRule.InheritanceFlags -eq [System.Security.AccessControl.InheritanceFlags]::None) `
        "file rule inherited directory flags"
}

Invoke-TestCase "preflight is fresh, empty, and artifact-free" {
    $fixture = New-Fixture "preflight"
    try {
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        Invoke-AndroidSigningBootstrap -BootstrapMode Preflight -BackupDirectory $fixture.Backup `
            -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        Assert-True (-not (Test-Path -LiteralPath $fixture.Handoff)) "preflight created handoff"
        Assert-True (@(Get-ChildItem -LiteralPath $fixture.Backup).Count -eq 0) "preflight changed backup"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "disposable seam requires exact ordinal Confirm and Secret hooks" {
    $fixture = New-Fixture "hook-authority"
    try {
        $identity = @{
            ValidityDays = 1
            DistinguishedName = "CN=MEE3-38 Disposable Integration Test, OU=NON-RELEASE, O=Meeting Tests"
        }
        $allowed = @{
            Confirm = { "CREATE-ANDROID-RELEASE-KEY" }
            Secret = { param([string] $Name) "disposable-$Name" }
        }
        $forbidden = @(
            "Keytool", "CertificateBytes", "Hash", "Acl", "BeforeMove",
            "BeforeCopy", "Copy", "BeforeDelete", "Gh", "Error", "Future"
        )
        foreach ($key in $forbidden) {
            $hooks = @{} + $allowed
            $hooks[$key] = { }
            Assert-InputValidationOnly {
                Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                    -BackupDirectory $fixture.Backup -HandoffDirectory $fixture.Handoff `
                    -Provision $false -Repository "" -Hooks $hooks `
                    -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                    -IntegrationTestIdentity $identity
            } "forbidden hook $key"
        }
        foreach ($hooks in @(
            $null,
            @{ Confirm = $allowed.Confirm },
            @{ Secret = $allowed.Secret },
            @{ confirm = $allowed.Confirm; Secret = $allowed.Secret },
            @{ Confirm = $allowed.Confirm; SECRET = $allowed.Secret },
            @{ Confirm = $null; Secret = $allowed.Secret },
            @{ Confirm = "not-a-script"; Secret = $allowed.Secret }
        )) {
            Assert-InputValidationOnly {
                Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                    -BackupDirectory $fixture.Backup -HandoffDirectory $fixture.Handoff `
                    -Provision $false -Repository "" -Hooks $hooks `
                    -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                    -IntegrationTestIdentity $identity
            } "invalid exact hook set"
        }
        foreach ($invalidIdentity in @(
            @{ ValidityDays = 1 },
            @{ DistinguishedName = $identity.DistinguishedName },
            @{ ValidityDays = "1"; DistinguishedName = $identity.DistinguishedName },
            @{ ValidityDays = 2; DistinguishedName = $identity.DistinguishedName },
            @{ ValidityDays = 1; DistinguishedName = "CN=Meet Android Release, OU=Release, O=Meet" },
            @{ validityDays = 1; DistinguishedName = $identity.DistinguishedName },
            @{ ValidityDays = 1; DistinguishedName = $identity.DistinguishedName; Extra = $true }
        )) {
            Assert-InputValidationOnly {
                Invoke-AndroidSigningBootstrap -BootstrapMode Execute `
                    -BackupDirectory $fixture.Backup -HandoffDirectory $fixture.Handoff `
                    -Provision $false -Repository "" -Hooks $allowed `
                    -IntegrationTestAuthority "MEE3-38-DISPOSABLE-NONRELEASE-V1" `
                    -IntegrationTestIdentity $invalidIdentity
            } "invalid disposable identity"
        }
        Assert-True (@(Get-ChildItem -LiteralPath $fixture.Backup -Force).Count -eq 0) `
            "input-validation negatives mutated backup sentinel"
        Assert-True (-not (Test-Path -LiteralPath $fixture.Handoff)) `
            "input-validation negatives created handoff"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "Execute refuses overwrite and confirmation mismatch" {
    $fixture = New-Fixture "refusal"
    try {
        New-Item -ItemType Directory -Path $fixture.Handoff | Out-Null
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "existing handoff was overwritten"
        Remove-Item -LiteralPath $fixture.Handoff -Recurse -Force
        $hooks.Confirm = { "wrong-confirmation" }
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "confirmation mismatch was accepted"
        Assert-True (-not (Test-Path -LiteralPath $fixture.Handoff)) "refusal created artifacts"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "Execute creates and verifies four artifacts without GitHub before commit" {
    $fixture = New-Fixture "execute"
    try {
        $ghCalls = New-Object System.Collections.ArrayList
        $aclCalls = New-Object System.Collections.ArrayList
        $hooks = New-TestHooks $ghCalls $false $aclCalls
        $hooks.Error = {
            param([string] $Message)
            Write-Host "HOOK ERROR: $Message"
        }
        Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
            -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        Assert-True ($ghCalls.Count -eq 0) "GitHub was called without provisioning"
        $generationCall = @($hooks.KeytoolCalls | Where-Object { $_ -contains "-genkeypair" })[0]
        Assert-True ($generationCall -contains "-storetype" -and
            $generationCall[$generationCall.IndexOf("-storetype") + 1] -ceq "JKS") `
            "generation did not explicitly select JKS"
        $listCalls = @($hooks.KeytoolCalls | Where-Object { $_ -contains "-list" })
        Assert-True ($listCalls.Count -gt 0) "identity list verification was not called"
        foreach ($listCall in $listCalls) {
            Assert-True ($listCall -notcontains "-keypass" -and $listCall -notcontains "-keypass:file") `
                "unsupported key password option was passed to keytool -list"
        }
        Assert-True (@($hooks.KeytoolCalls | Where-Object { $_ -contains "-certreq" }).Count -gt 0) `
            "private-key unlock verification was not called"
        Assert-True (@(Get-ChildItem -LiteralPath $fixture.Handoff).Count -eq 4) "handoff is incomplete"
        Assert-True (@(Get-ChildItem -LiteralPath $fixture.Backup).Count -eq 4) "backup is incomplete"
        Assert-ArtifactSetsEqual $fixture.Handoff $fixture.Backup
        Assert-True (@($aclCalls | Where-Object { $_.IsDirectory }).Count -ge 2) "directory ACL seam not used"
        Assert-True (@($aclCalls | Where-Object { -not $_.IsDirectory }).Count -ge 4) "file ACL seam not used"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "pre-commit failure removes invocation-owned artifacts" {
    $fixture = New-Fixture "cleanup"
    try {
        $ghCalls = New-Object System.Collections.ArrayList
        $hooks = New-TestHooks $ghCalls
        $hooks.Error = {
            param([string] $Message)
            Write-Host "HOOK ERROR: $Message"
        }
        $hooks.BeforeCopy = {
            param([string] $Source, [string] $Destination)
            throw "injected copy failure"
        }
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "pre-commit failure did not fail"
        Assert-True (-not (Test-Path -LiteralPath $fixture.Handoff)) "owned handoff survived pre-commit failure"
        Assert-True (@(Get-ChildItem -LiteralPath $fixture.Backup).Count -eq 0) `
            "backup changed after pre-copy failure"
        Assert-True ($ghCalls.Count -eq 0) "GitHub was called before commit"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "staged deletion failure remains cleanup-owned and is retried" {
    $fixture = New-Fixture "delete-retry"
    try {
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        $hooks.BeforeDelete = {
            param([string] $Path)
            throw "injected staged deletion failure"
        }
        $source = Join-Path $fixture.Root "staged-secret.tmp"
        $destination = Join-Path $fixture.Handoff "final-secret.tmp"
        New-Item -ItemType Directory -Path $fixture.Handoff | Out-Null
        [IO.File]::WriteAllText($source, "secret")
        $ownedFiles = New-Object System.Collections.ArrayList
        $ownedDirectories = New-Object System.Collections.ArrayList
        [void]$ownedFiles.Add($source)
        Assert-Throws {
            Move-StagedArtifact $source $destination $ownedFiles $ownedDirectories $hooks
        } "staged deletion failure was not reported"
        Assert-True ($ownedFiles.Contains($source)) `
            "staged source was untracked before successful deletion"
        Assert-True (Test-Path -LiteralPath $source -PathType Leaf) `
            "staged source was removed after deletion failure"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "replacement of an owned file is preserved during cleanup" {
    $fixture = New-Fixture "replacement"
    try {
        $path = Join-Path $fixture.Root "owned.tmp"
        [IO.File]::WriteAllText($path, "original")
        $ownedFiles = New-Object System.Collections.ArrayList
        $ownedDirectories = New-Object System.Collections.ArrayList
        Add-OwnedFile $ownedFiles $path
        Set-OwnedFileFingerprint $path
        [IO.File]::WriteAllText($path, "replacement")
        Assert-Throws {
            Remove-OwnedArtifacts $ownedFiles $ownedDirectories @{}
        } "replacement file was treated as invocation-owned"
        Assert-True (([IO.File]::ReadAllText($path) -ceq "replacement")) `
            "replacement file was deleted during cleanup"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "race-equivalent pre-existing targets are never overwritten or removed" {
    $fixture = New-Fixture "race"
    try {
        $preExisting = Join-Path $fixture.Root "pre-existing"
        New-Item -ItemType Directory -Path $preExisting | Out-Null
        $preExistingBytes = [Text.Encoding]::UTF8.GetBytes("do-not-touch")
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        $hooks.BeforeMove = {
            param([string] $Source, [string] $Destination)
            if ($Destination -like "*meet-release.jks" -and -not (Test-Path -LiteralPath $Destination)) {
                [IO.File]::WriteAllBytes($Destination, $preExistingBytes)
            }
        }.GetNewClosure()
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "race-equivalent pre-existing target was accepted"
        $target = Join-Path $fixture.Handoff "meet-release.jks"
        Assert-True (Test-Path -LiteralPath $target -PathType Leaf) "pre-existing target was removed"
        Assert-True ((Compare-Bytes ([IO.File]::ReadAllBytes($target)) $preExistingBytes)) `
            "pre-existing target was overwritten"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "backup copy seam never overwrites a concurrent destination" {
    $fixture = New-Fixture "backup-race"
    try {
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        $hooks.BeforeCopy = {
            param([string] $Source, [string] $Destination)
            [IO.File]::WriteAllText($Destination, "pre-existing")
        }.GetNewClosure()
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "backup race was accepted"
        $backupTarget = Join-Path $fixture.Backup "meet-release.jks"
        Assert-True (Test-Path -LiteralPath $backupTarget -PathType Leaf) `
            "pre-existing backup race artifact was removed"
        Assert-True (([IO.File]::ReadAllText($backupTarget) -ceq "pre-existing")) `
            "pre-existing backup race artifact was overwritten"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "partial backup copy remains for diagnosis and is not cleanup-owned" {
    $fixture = New-Fixture "backup-partial"
    try {
        $hooks = New-TestHooks (New-Object System.Collections.ArrayList)
        $hooks.Copy = {
            param([string] $Source, [string] $Destination, [IO.Stream] $DestinationStream)
            $bytes = [Text.Encoding]::UTF8.GetBytes("partial-copy")
            $DestinationStream.Write($bytes, 0, $bytes.Length)
            throw "injected partial copy failure"
        }.GetNewClosure()
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        } "partial backup copy was accepted"
        $target = Join-Path $fixture.Backup "meet-release.jks"
        Assert-True (Test-Path -LiteralPath $target -PathType Leaf) "partial destination was deleted"
        Assert-True (([IO.File]::ReadAllText($target) -ceq "partial-copy")) `
            "partial destination bytes were not preserved"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "Provision rejects inconsistent fixtures before GitHub and does no local writes" {
    $fixture = New-Fixture "provision-invalid"
    try {
        New-Item -ItemType Directory -Path $fixture.Handoff | Out-Null
        $ghCalls = New-Object System.Collections.ArrayList
        $hooks = New-TestHooks $ghCalls
        Set-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release-passwords.txt") `
            -Value "STORE_PASSWORD=bad`nKEY_PASSWORD=bad`nALIAS=wrong`nCERTIFICATE_SHA256=bad"
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Provision -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $true -Repository "owner/repo" -Hooks $hooks
        } "invalid Provision fixture was accepted"
        Assert-True ($ghCalls.Count -eq 0) "GitHub was called for invalid fixture"
    } finally { Remove-Fixture $fixture }
}

Invoke-TestCase "Provision rejects alias, password, fingerprint, and byte inconsistencies before GitHub" {
    foreach ($mutation in @("ALIAS", "PASSWORD", "FINGERPRINT", "BYTES")) {
        $fixture = New-Fixture ("provision-" + $mutation.ToLowerInvariant())
        try {
            $ghCalls = New-Object System.Collections.ArrayList
            $hooks = New-TestHooks $ghCalls
            Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
            switch ($mutation) {
                "ALIAS" {
                    (Get-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release-passwords.txt")) `
                        -replace "ALIAS=meet-release", "ALIAS=wrong-alias" |
                        Set-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release-passwords.txt")
                }
                "PASSWORD" {
                    (Get-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release-passwords.txt")) `
                        -replace "KEY_PASSWORD=key-password-fixture", "KEY_PASSWORD=wrong-password" |
                        Set-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release-passwords.txt")
                }
                "FINGERPRINT" {
                    (Get-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release.sha256")) `
                        -replace "^[0-9a-f]+$", ("0" * 64) |
                        Set-Content -LiteralPath (Join-Path $fixture.Handoff "meet-release.sha256")
                }
                "BYTES" {
                    Add-Content -LiteralPath (Join-Path $fixture.Backup "meet-release.cer") -Value "mismatch"
                }
            }
            Assert-Throws {
                Invoke-AndroidSigningBootstrap -BootstrapMode Provision -BackupDirectory $fixture.Backup `
                    -HandoffDirectory $fixture.Handoff -Provision $true -Repository "owner/repo" -Hooks $hooks
            } "$mutation inconsistency was accepted"
            Assert-True ($ghCalls.Count -eq 0) "$mutation inconsistency reached GitHub"
        } finally { Remove-Fixture $fixture }
    }
}

Invoke-TestCase "Provision retries same committed identity with exact stdin commands" {
    $fixture = New-Fixture "provision-retry"
    try {
        $ghCalls = New-Object System.Collections.ArrayList
        $hooks = New-TestHooks $ghCalls
        $hooks.Error = {
            param([string] $Message)
            Write-Host "HOOK ERROR: $Message"
        }
        Invoke-AndroidSigningBootstrap -BootstrapMode Execute -BackupDirectory $fixture.Backup `
            -HandoffDirectory $fixture.Handoff -Provision $false -Repository "" -Hooks $hooks
        $before = @{}
        foreach ($name in $script:AndroidSigningArtifactNames) {
            $before[$name] = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $fixture.Handoff $name)))
        }
        $hooks.Gh = {
            param([string[]] $Arguments, [string] $InputValue)
            [void]$ghCalls.Add([pscustomobject]@{ Arguments = $Arguments; Input = $InputValue })
            if ($ghCalls.Count -eq 5) { throw "injected failure between environments" }
            return [pscustomobject]@{ ExitCode = 0 }
        }
        $hooks.KeytoolCalls.Clear()
        $hooks.CopyCalls.Clear()
        $hooks.AclPaths.Clear()
        $beforeProvisionFiles = @(
            Get-ChildItem -LiteralPath $fixture.Handoff -Force | ForEach-Object { $_.Name }
        )
        $beforeProvisionBackupFiles = @(
            Get-ChildItem -LiteralPath $fixture.Backup -Force | ForEach-Object { $_.Name }
        )
        Assert-Throws {
            Invoke-AndroidSigningBootstrap -BootstrapMode Provision -BackupDirectory $fixture.Backup `
                -HandoffDirectory $fixture.Handoff -Provision $true -Repository "owner/repo" -Hooks $hooks
        } "injected Provision failure was not reported"
        Assert-True (Test-Path -LiteralPath $fixture.Handoff) "handoff was removed after commit"
        Assert-True (Test-Path -LiteralPath (Join-Path $fixture.Backup "meet-release.jks")) "backup was removed after commit"
        $hooks.Gh = {
            param([string[]] $Arguments, [string] $InputValue)
            [void]$ghCalls.Add([pscustomobject]@{ Arguments = $Arguments; Input = $InputValue })
            return [pscustomobject]@{ ExitCode = 0 }
        }
        Invoke-AndroidSigningBootstrap -BootstrapMode Provision -BackupDirectory $fixture.Backup `
            -HandoffDirectory $fixture.Handoff -Provision $true -Repository "owner/repo" -Hooks $hooks
        Assert-True (@($hooks.KeytoolCalls | Where-Object { $_ -contains "-list" }).Count -ge 2) `
            "Provision did not list both preserved keystores"
        Assert-True (@($hooks.KeytoolCalls | Where-Object { $_ -contains "-certreq" }).Count -ge 2) `
            "Provision did not verify both private-key passwords"
        Assert-True ($hooks.CopyCalls.Count -eq 0) "Provision copied local artifacts"
        Assert-True (
            (@(Get-ChildItem -LiteralPath $fixture.Handoff -Force | ForEach-Object { $_.Name }) -join "|") -ceq
            ($beforeProvisionFiles -join "|")
        ) "Provision changed handoff artifact set"
        Assert-True (
            (@(Get-ChildItem -LiteralPath $fixture.Backup -Force | ForEach-Object { $_.Name }) -join "|") -ceq
            ($beforeProvisionBackupFiles -join "|")
        ) "Provision changed backup artifact set"
        $ephemeralPaths = @($hooks.AclPaths | Where-Object { $_ -like "*meeting-android-signing-*" })
        Assert-True ($ephemeralPaths.Count -ge 2) "Provision did not create secure temporary password files"
        foreach ($ephemeral in $ephemeralPaths) {
            Assert-True (-not (Test-Path -LiteralPath $ephemeral)) "Provision temporary file was not cleaned"
            Assert-True (-not ($ephemeral -like ($fixture.Handoff + "*"))) "temporary file was inside handoff"
            Assert-True (-not ($ephemeral -like ($fixture.Backup + "*"))) "temporary file was inside backup"
        }
        Assert-True ($ghCalls.Count -eq 13) "retry did not emit all eight exact commands"
        $expected = @(
            "RELEASE_KEYSTORE_BASE64", "RELEASE_KEYSTORE_PASSWORD", "RELEASE_KEY_PASSWORD",
            "RELEASE_CERTIFICATE_SHA256", "SNAPSHOT_RELEASE_KEYSTORE_BASE64",
            "SNAPSHOT_RELEASE_KEYSTORE_PASSWORD", "SNAPSHOT_RELEASE_KEY_PASSWORD",
            "RELEASE_CERTIFICATE_SHA256"
        )
        for ($i = 5; $i -lt $ghCalls.Count; $i++) {
            Assert-True ($ghCalls[$i].Arguments[2] -eq $expected[$i - 5]) "unexpected GitHub name"
            Assert-True (-not [string]::IsNullOrWhiteSpace($ghCalls[$i].Input)) "empty stdin value"
            Assert-True ($ghCalls[$i].Arguments -notcontains "store-password-fixture") "password leaked into gh argv"
            Assert-True ($ghCalls[$i].Arguments -notcontains "key-password-fixture") "key password leaked into gh argv"
        }
        foreach ($name in $script:AndroidSigningArtifactNames) {
            $after = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $fixture.Handoff $name)))
            Assert-True ($before[$name] -ceq $after) "Provision changed artifact bytes"
        }
        $output = & $scriptPath -Mode Provision -OfflineBackupDirectory $fixture.Backup `
            -CredentialHandoffDirectory $fixture.Handoff -Library 2>&1 | Out-String
        Assert-True ($output -notmatch "store-password-fixture|key-password-fixture|fixture-keystore") `
            "output leaked a secret or key material"
    } finally { Remove-Fixture $fixture }
}

Write-Host "All deterministic Android signing bootstrap tests passed."
