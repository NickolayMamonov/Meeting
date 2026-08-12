[CmdletBinding()]
param(
    [ValidateSet("Preflight", "Execute", "Provision")]
    [string] $Mode = "Preflight",
    [string] $OfflineBackupDirectory,
    [string] $CredentialHandoffDirectory,
    [switch] $ProvisionGitHubSecrets,
    [string] $GitHubRepository,
    [hashtable] $TestHooks,
    [switch] $Library
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$script:LibraryMode = $Library.IsPresent
$script:SigningStages = @(
    "input-validation", "preflight", "handoff-create", "handoff-acl",
    "secret-temp-create", "key-generate", "certificate-export",
    "local-artifact-materialize", "local-identity-verify", "backup-copy",
    "backup-identity-verify", "backup-commit", "github-environment-write",
    "cleanup"
)
$script:SigningCategories = @(
    "validation", "missing-tool", "access-denied", "path-conflict",
    "process-start", "process-exit", "malformed-output",
    "identity-mismatch", "github-write", "cleanup", "unknown"
)

$script:AndroidSigningArtifactNames = @(
    "meet-release.jks",
    "meet-release.cer",
    "meet-release.sha256",
    "meet-release-passwords.txt"
)
$script:OwnedFileFingerprints = @{}

function New-SigningContext {
    return [pscustomobject]@{
        Stage = "input-validation"
        BackupCommitted = $false
        CleanupComplete = $true
    }
}

function Set-SigningStage([pscustomobject] $Context, [string] $Stage) {
    if ($script:SigningStages -notcontains $Stage) {
        $Context.Stage = "input-validation"
        return
    }
    $Context.Stage = $Stage
}

function New-SigningFailure([string] $Stage, [string] $Category) {
    $failure = New-Object System.Exception("Signing bootstrap failure")
    if (-not [string]::IsNullOrWhiteSpace($Stage)) {
        $failure.Data["SigningStage"] = if ($script:SigningStages -contains $Stage) { $Stage } else { "unknown" }
    }
    $failure.Data["SigningCategory"] = if ($script:SigningCategories -contains $Category) { $Category } else { "unknown" }
    return $failure
}

function Get-SigningFailureCategory([System.Management.Automation.ErrorRecord] $ErrorRecord) {
    if ($null -ne $ErrorRecord.Exception.Data["SigningCategory"]) {
        $category = [string]$ErrorRecord.Exception.Data["SigningCategory"]
        if ($script:SigningCategories -contains $category) { return $category }
    }
    $exception = $ErrorRecord.Exception
    if ($exception -is [System.Management.Automation.CommandNotFoundException]) { return "missing-tool" }
    if ($exception -is [System.UnauthorizedAccessException]) { return "access-denied" }
    if ($exception -is [System.IO.IOException]) { return "path-conflict" }
    if ($exception -is [System.FormatException]) { return "malformed-output" }
    return "unknown"
}

function Get-SigningFailureStage([System.Management.Automation.ErrorRecord] $ErrorRecord, [pscustomobject] $Context) {
    if ($null -ne $ErrorRecord.Exception.Data["SigningStage"]) {
        $stage = [string]$ErrorRecord.Exception.Data["SigningStage"]
        if ($script:SigningStages -contains $stage) { return $stage }
    }
    if ($null -ne $Context -and $script:SigningStages -contains $Context.Stage) { return $Context.Stage }
    return "unknown"
}

function Format-SigningFailure(
    [string] $Stage,
    [string] $Category,
    [bool] $BackupCommitted,
    [bool] $CleanupComplete
) {
    if ($script:SigningStages -notcontains $Stage) { $Stage = "unknown" }
    if ($script:SigningCategories -notcontains $Category) { $Category = "unknown" }
    if ($BackupCommitted) {
        return "Signing bootstrap failed after backup commit; stage=$Stage category=$Category cleanup=$([bool]$CleanupComplete). Rerun -Mode Provision with the same paths. No secret values were printed."
    }
    return "Signing bootstrap failed before backup commit; stage=$Stage category=$Category cleanup=$([bool]$CleanupComplete). Invocation-owned partial artifacts were removed when cleanup completed. No secret values were printed."
}

function Test-OrdinalKeySet(
    [string[]] $Actual,
    [string[]] $Expected
) {
    if ($Actual.Count -ne $Expected.Count) {
        return $false
    }
    foreach ($expectedKey in $Expected) {
        $found = $false
        foreach ($actualKey in $Actual) {
            if ([string]::Equals($actualKey, $expectedKey, [StringComparison]::Ordinal)) {
                $found = $true
                break
            }
        }
        if (-not $found) {
            return $false
        }
    }
    return $true
}

function Assert-IntegrationTestContract(
    [string] $BootstrapMode,
    [bool] $Provision,
    [hashtable] $Hooks,
    [string] $IntegrationTestAuthority,
    [hashtable] $IntegrationTestIdentity
) {
    $authoritySupplied = -not [string]::IsNullOrWhiteSpace($IntegrationTestAuthority)
    $identitySupplied = $null -ne $IntegrationTestIdentity
    if (-not $authoritySupplied -and -not $identitySupplied) {
        return $null
    }
    if (-not $script:LibraryMode -or $IntegrationTestAuthority -cne "MEE3-38-DISPOSABLE-NONRELEASE-V1" -or
        $BootstrapMode -cne "Execute" -or $Provision -or $null -eq $IntegrationTestIdentity) {
        throw (New-SigningFailure "input-validation" "validation")
    }
    $keys = @($IntegrationTestIdentity.Keys | ForEach-Object { [string]$_ })
    if (-not (Test-OrdinalKeySet $keys @("ValidityDays", "DistinguishedName"))) {
        throw (New-SigningFailure "input-validation" "validation")
    }
    $validityValue = $null
    $distinguishedNameValue = $null
    foreach ($key in $keys) {
        if ([string]::Equals($key, "ValidityDays", [StringComparison]::Ordinal)) {
            $validityValue = $IntegrationTestIdentity[$key]
        } else {
            $distinguishedNameValue = $IntegrationTestIdentity[$key]
        }
    }
    if ($validityValue -isnot [int] -or
        [int]$validityValue -ne 1 -or
        $distinguishedNameValue -isnot [string] -or
        [string]$distinguishedNameValue -cne
        "CN=MEE3-38 Disposable Integration Test, OU=NON-RELEASE, O=Meeting Tests") {
        throw (New-SigningFailure "input-validation" "validation")
    }
    if ($null -eq $Hooks) {
        throw (New-SigningFailure "input-validation" "validation")
    }
    $hookKeys = @($Hooks.Keys | ForEach-Object { [string]$_ })
    if (-not (Test-OrdinalKeySet $hookKeys @("Confirm", "Secret"))) {
        throw (New-SigningFailure "input-validation" "validation")
    }
    $confirmHook = $null
    $secretHook = $null
    foreach ($key in $hookKeys) {
        if ([string]::Equals($key, "Confirm", [StringComparison]::Ordinal)) {
            $confirmHook = $Hooks[$key]
        } else {
            $secretHook = $Hooks[$key]
        }
    }
    if ($confirmHook -isnot [scriptblock] -or $secretHook -isnot [scriptblock]) {
        throw (New-SigningFailure "input-validation" "validation")
    }
    return [pscustomobject]@{
        ValidityDays = 1
        DistinguishedName = [string]$distinguishedNameValue
    }
}

if (-not $Library -and ([string]::IsNullOrWhiteSpace($OfflineBackupDirectory) -or
    [string]::IsNullOrWhiteSpace($CredentialHandoffDirectory))) {
    throw "OfflineBackupDirectory and CredentialHandoffDirectory are required"
}

function ConvertTo-CanonicalPath([string] $Path) {
    $canonical = [IO.Path]::GetFullPath($Path).Replace("/", "\")
    $root = [IO.Path]::GetPathRoot($canonical)
    while ($canonical.Length -gt $root.Length -and $canonical.EndsWith("\")) {
        $canonical = $canonical.Substring(0, $canonical.Length - 1)
    }
    return $canonical
}

function Get-PathComponents([string] $Path) {
    return @((ConvertTo-CanonicalPath $Path) -split "\\" | Where-Object { $_.Length -gt 0 })
}

function Test-PathEqualOrNested([string] $First, [string] $Second) {
    $left = Get-PathComponents $First
    $right = Get-PathComponents $Second
    if ($left.Count -eq $right.Count) {
        for ($i = 0; $i -lt $left.Count; $i++) {
            if (-not [string]::Equals($left[$i], $right[$i], [StringComparison]::OrdinalIgnoreCase)) {
                return $false
            }
        }
        return $true
    }

    $shorter = $left
    $longer = $right
    if ($left.Count -gt $right.Count) {
        $shorter = $right
        $longer = $left
    }
    for ($i = 0; $i -lt $shorter.Count; $i++) {
        if (-not [string]::Equals($shorter[$i], $longer[$i], [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
    }
    return $true
}

function Assert-SeparateSigningPaths([string] $Backup, [string] $Handoff) {
    if (Test-PathEqualOrNested $Backup $Handoff) {
        throw "Offline backup and credential handoff directories must be separate, non-nested paths"
    }
}

function Assert-NoReparsePointPath([string] $Path) {
    $canonical = ConvertTo-CanonicalPath $Path
    $root = [IO.Path]::GetPathRoot($canonical)
    $current = $root
    foreach ($component in @($canonical.Substring($root.Length) -split "\\" |
            Where-Object { $_.Length -gt 0 })) {
        $current = Join-Path $current $component
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Signing paths must not contain reparse-point components"
            }
        }
    }
}

function Assert-NewPath([string] $Path) {
    if (Test-Path -LiteralPath $Path) {
        throw "Refusing to overwrite existing path"
    }
}

function Add-OwnedFile([System.Collections.ArrayList] $OwnedFiles, [string] $Path) {
    if (-not $OwnedFiles.Contains($Path)) {
        [void]$OwnedFiles.Add($Path)
    }
}

function Set-OwnedFileFingerprint([string] $Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $item = Get-Item -LiteralPath $Path -Force
        $script:OwnedFileFingerprints[$Path] = [pscustomobject]@{
            Bytes = [IO.File]::ReadAllBytes($Path)
            Length = $item.Length
            CreationTimeUtc = $item.CreationTimeUtc
        }
    }
}

function Remove-OwnedFile([System.Collections.ArrayList] $OwnedFiles, [string] $Path) {
    [void]$OwnedFiles.Remove($Path)
    [void]$script:OwnedFileFingerprints.Remove($Path)
}

function Remove-OwnedDirectory(
    [System.Collections.ArrayList] $OwnedDirectories,
    [string] $Path
) {
    [void]$OwnedDirectories.Remove($Path)
}

function Get-InvocationTempPath([string] $Suffix) {
    return [IO.Path]::Combine(
        [IO.Path]::GetTempPath(),
        "meeting-android-signing-" + [guid]::NewGuid().ToString("N") + $Suffix
    )
}

function Test-IsAclSupportedPath([string] $Path) {
    try {
        $root = [IO.Path]::GetPathRoot($Path)
        if ([string]::IsNullOrWhiteSpace($root)) {
            return $false
        }
        $drive = Get-CimInstance Win32_LogicalDisk -Filter ("DeviceID='" + $root.TrimEnd("\") + "'")
        return $null -eq $drive -or $drive.FileSystem -ne "FAT32"
    } catch {
        return $false
    }
}

function New-SecureEphemeralFile(
    [hashtable] $Hooks,
    [System.Collections.ArrayList] $OwnedFiles
) {
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $path = Get-InvocationTempPath ".tmp"
        try {
            $stream = [IO.File]::Open(
                $path,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::None
            )
            $stream.Dispose()
            break
        } catch [IO.IOException] {
            if (Test-Path -LiteralPath $path) {
                if ($attempt -eq 10) {
                    throw "Unable to create secure temporary file after repeated name collisions"
                }
                continue
            }
            throw "Unable to create secure temporary file"
        }
    }
    Add-OwnedFile $OwnedFiles $path
    Set-OwnerOnlyAcl $path $false $Hooks
    return $path
}

function New-SecureEphemeralDirectory(
    [hashtable] $Hooks,
    [System.Collections.ArrayList] $OwnedRecursiveDirectories
) {
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $path = Get-InvocationTempPath ""
        try {
            New-Item -ItemType Directory -Path $path -ErrorAction Stop | Out-Null
            break
        } catch [IO.IOException] {
            if (Test-Path -LiteralPath $path) {
                if ($attempt -eq 10) {
                    throw "Unable to create secure temporary directory after repeated name collisions"
                }
                continue
            }
            throw "Unable to create secure temporary directory"
        }
    }
    [void]$OwnedRecursiveDirectories.Add($path)
    Set-OwnerOnlyAcl $path $true $Hooks
    return $path
}

function Move-StagedArtifact(
    [string] $Source,
    [string] $Destination,
    [System.Collections.ArrayList] $OwnedFiles,
    [System.Collections.ArrayList] $OwnedDirectories,
    [hashtable] $Hooks
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("BeforeMove")) {
        & $Hooks["BeforeMove"] $Source $Destination
    }
    try {
        $sourceStream = [IO.File]::OpenRead($Source)
        try {
            $destinationStream = [IO.File]::Open(
                $Destination,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None
            )
            Add-OwnedFile $OwnedFiles $Destination
            try {
                $sourceStream.CopyTo($destinationStream)
                $destinationStream.Flush()
            } finally {
                $destinationStream.Dispose()
            }
        } finally {
            $sourceStream.Dispose()
        }
    } catch {
        if ((Test-Path -LiteralPath $Destination -PathType Leaf) -and
            -not $OwnedFiles.Contains($Destination)) {
            throw "Refusing to overwrite a path created concurrently"
        }
        throw
    }
    if ($null -ne $Hooks -and $Hooks.ContainsKey("BeforeDelete")) {
        & $Hooks["BeforeDelete"] $Source
    }
    Remove-Item -LiteralPath $Source -Force
    Remove-OwnedFile $OwnedFiles $Source
    if ($OwnedFiles.Contains($Destination)) {
        Set-OwnedFileFingerprint $Destination
    }
    if (Test-IsAclSupportedPath $Destination) {
        Set-OwnerOnlyAcl $Destination $false $Hooks
    }
}

function Copy-FileExclusively(
    [string] $Source,
    [string] $Destination,
    [System.Collections.ArrayList] $OwnedFiles,
    [hashtable] $Hooks
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("BeforeCopy")) {
        & $Hooks["BeforeCopy"] $Source $Destination
    }
    $sourceStream = $null
    $destinationStream = $null
    $destinationCreated = $false
    try {
        $sourceStream = [IO.File]::OpenRead($Source)
        $destinationStream = [IO.File]::Open(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        Add-OwnedFile $OwnedFiles $Destination
        $destinationCreated = $true
        if ($null -ne $Hooks -and $Hooks.ContainsKey("Copy")) {
            & $Hooks["Copy"] $Source $Destination $destinationStream
        } else {
            $sourceStream.CopyTo($destinationStream)
        }
        $destinationStream.Flush()
    } catch {
        if ($destinationCreated) {
            Remove-OwnedFile $OwnedFiles $Destination
        } elseif (Test-Path -LiteralPath $Destination -PathType Leaf) {
            throw "Refusing to overwrite a path created concurrently"
        }
        throw
    } finally {
        if ($null -ne $destinationStream) {
            $destinationStream.Dispose()
        }
        if ($null -ne $sourceStream) {
            $sourceStream.Dispose()
        }
    }
}

function Write-ExclusiveText(
    [string] $Path,
    [string[]] $Lines,
    [System.Collections.ArrayList] $OwnedFiles
) {
    $stream = [IO.File]::Open(
        $Path,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    Add-OwnedFile $OwnedFiles $Path
    try {
        $writer = New-Object IO.StreamWriter(
            $stream,
            (New-Object Text.UTF8Encoding($false))
        )
        try {
            foreach ($line in $Lines) {
                $writer.WriteLine($line)
            }
            $writer.Flush()
        } finally {
            $writer.Dispose()
        }
        Set-OwnedFileFingerprint $Path
    } finally {
        $stream.Dispose()
    }
}

function Get-OwnerOnlyAclRule([bool] $IsDirectory) {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $inheritance = [System.Security.AccessControl.InheritanceFlags]::None
    if ($IsDirectory) {
        $inheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor `
            [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
    }
    return New-Object System.Security.AccessControl.FileSystemAccessRule(
        $identity,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        $inheritance,
        [System.Security.AccessControl.PropagationFlags]::None,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
}

function Set-OwnerOnlyAcl([string] $Path, [bool] $IsDirectory, [hashtable] $Hooks) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Acl")) {
        & $Hooks["Acl"] $Path $IsDirectory
        return
    }
    $acl = Get-Acl -LiteralPath $Path
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $existingRules = @($acl.Access)
    $expectedInheritance = if ($IsDirectory) {
        [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor `
            [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
    } else {
        [System.Security.AccessControl.InheritanceFlags]::None
    }
    if ($acl.AreAccessRulesProtected -and $existingRules.Count -eq 1) {
        $existing = $existingRules[0]
        if ($existing.IdentityReference.Value -ceq $identity -and
            (($existing.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::FullControl) -eq
                [System.Security.AccessControl.FileSystemRights]::FullControl) -and
            $existing.AccessControlType -eq [System.Security.AccessControl.AccessControlType]::Allow -and
            -not $existing.IsInherited -and $existing.InheritanceFlags -eq $expectedInheritance) {
            return
        }
    }
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetAccessRule((Get-OwnerOnlyAclRule $IsDirectory))
    try {
        Set-Acl -LiteralPath $Path -AclObject $acl
    } catch [System.Security.AccessControl.PrivilegeNotHeldException] {
        throw (New-SigningFailure "local-artifact-materialize" "access-denied")
    } catch [System.UnauthorizedAccessException] {
        throw (New-SigningFailure "local-artifact-materialize" "access-denied")
    }
}

function Invoke-Keytool(
    [hashtable] $Hooks,
    [string[]] $Arguments,
    [string[]] $PasswordInput = @(),
    [string] $Stage = "unknown"
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Keytool")) {
        $result = & $Hooks["Keytool"] $Arguments $PasswordInput
        if ($null -ne $result -and $result.PSObject.Properties["ExitCode"]) {
            if ([int]$result.ExitCode -ne 0) {
                throw "keytool verification command failed"
            }
            return [string]$result.Output
        }
        return [string]$result
    }
    $previousPreference = $ErrorActionPreference
    try {
        # Temurin keytool writes ordinary progress and warning text to stderr.
        # Windows PowerShell 5.1 promotes native stderr to the Error stream,
        # which must not turn a successful keytool exit into a terminating
        # NativeCommandError.
        $ErrorActionPreference = "SilentlyContinue"
        $output = (& keytool @Arguments 2>$null | Out-String)
        $exitCode = $LASTEXITCODE
    } catch {
        throw (New-SigningFailure $Stage "process-start")
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw (New-SigningFailure $Stage "process-exit")
    }
    return $output
}

function Get-PasswordArguments(
    [hashtable] $Hooks,
    [string] $StorePassword,
    [string] $KeyPassword,
    [string] $StorePasswordFile,
    [string] $KeyPasswordFile
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Keytool")) {
        return @("-storepass", $StorePassword, "-keypass", $KeyPassword)
    }
    return @("-storepass:file", $StorePasswordFile, "-keypass:file", $KeyPasswordFile)
}

function Get-CertificateBytesFromKeystore(
    [hashtable] $Hooks,
    [string] $Keystore,
    [string] $StorePassword,
    [string] $Alias,
    [string] $StorePasswordFile
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("CertificateBytes")) {
        return [byte[]](& $Hooks["CertificateBytes"] $Keystore $StorePassword $Alias)
    }
    $arguments = @("-exportcert", "-rfc", "-keystore", $Keystore, "-alias", $Alias)
    if ($null -ne $StorePasswordFile) {
        $arguments += @("-storepass:file", $StorePasswordFile)
        $pem = Invoke-Keytool $Hooks $arguments @() "local-identity-verify"
    } else {
        $pem = Invoke-Keytool $Hooks ($arguments + @("-storepass", $StorePassword)) `
            @() "local-identity-verify"
    }
    $match = [regex]::Match($pem, "-----BEGIN CERTIFICATE-----(?<body>.*?)-----END CERTIFICATE-----", "Singleline")
    if (-not $match.Success) {
        throw "keytool did not return a certificate"
    }
    $body = $match.Groups["body"].Value -replace "\s", ""
    try {
        return [Convert]::FromBase64String($body)
    } catch {
        throw "keytool returned an invalid certificate"
    }
}

function Get-ArtifactHash([hashtable] $Hooks, [string] $Path) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Hash")) {
        return ([string](& $Hooks["Hash"] $Path)).Trim().ToLowerInvariant()
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Normalize-Fingerprint([string] $Fingerprint) {
    $normalized = ($Fingerprint -replace "[:\s-]", "").Trim().ToLowerInvariant()
    if ($normalized -notmatch "^[0-9a-f]{64}$") {
        throw "Certificate fingerprint is not a SHA-256 value"
    }
    return $normalized
}

function Read-RecoveryMetadata([string] $Path) {
    $lines = [IO.File]::ReadAllLines($Path)
    $values = @{}
    foreach ($line in $lines) {
        if ($line -notmatch "^(STORE_PASSWORD|KEY_PASSWORD|ALIAS|CERTIFICATE_SHA256)=(.*)$") {
            throw "Recovery metadata has an invalid format"
        }
        $name = $Matches[1]
        if ($values.ContainsKey($name)) {
            throw "Recovery metadata contains a duplicate field"
        }
        $values[$name] = $Matches[2]
    }
    foreach ($required in @("STORE_PASSWORD", "KEY_PASSWORD", "ALIAS", "CERTIFICATE_SHA256")) {
        if (-not $values.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($values[$required])) {
            throw "Recovery metadata is incomplete"
        }
    }
    if ($values["STORE_PASSWORD"].Contains("`r") -or $values["STORE_PASSWORD"].Contains("`n") -or
        $values["KEY_PASSWORD"].Contains("`r") -or $values["KEY_PASSWORD"].Contains("`n")) {
        throw "Recovery metadata contains an invalid password"
    }
    if ($values["ALIAS"] -cne "meet-release") {
        throw "Recovery metadata alias must be meet-release"
    }
    return [pscustomobject]@{
        StorePassword = $values["STORE_PASSWORD"]
        KeyPassword = $values["KEY_PASSWORD"]
        Alias = $values["ALIAS"]
        Fingerprint = Normalize-Fingerprint $values["CERTIFICATE_SHA256"]
    }
}

function Compare-Bytes([byte[]] $Left, [byte[]] $Right) {
    if ($Left.Length -ne $Right.Length) {
        return $false
    }
    for ($i = 0; $i -lt $Left.Length; $i++) {
        if ($Left[$i] -ne $Right[$i]) {
            return $false
        }
    }
    return $true
}

function Assert-KeystoreIdentity(
    [hashtable] $Hooks,
    [string] $Keystore,
    [string] $Certificate,
    [pscustomobject] $Metadata,
    [string] $StorePasswordFile,
    [string] $KeyPasswordFile
) {
    $listArguments = @(
        "-list", "-v", "-keystore", $Keystore, "-storetype", "JKS", "-alias", $Metadata.Alias
    )
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Keytool")) {
        $listArguments += @("-storepass", $Metadata.StorePassword)
        Invoke-Keytool $Hooks $listArguments @() "local-identity-verify" | Out-Null
    } elseif (-not [string]::IsNullOrWhiteSpace($StorePasswordFile)) {
        $listArguments += @("-storepass:file", $StorePasswordFile)
        Invoke-Keytool $Hooks $listArguments @() "local-identity-verify" | Out-Null
    } else {
        $listArguments += @("-storepass", $Metadata.StorePassword)
        Invoke-Keytool $Hooks $listArguments @() "local-identity-verify" | Out-Null
    }

    $requestPath = Get-InvocationTempPath ".csr"
    try {
        $requestArguments = @(
            "-certreq", "-keystore", $Keystore, "-storetype", "JKS",
            "-alias", $Metadata.Alias, "-file", $requestPath
        )
        if ($null -ne $Hooks -and $Hooks.ContainsKey("Keytool")) {
            Invoke-Keytool $Hooks ($requestArguments + @(
                "-storepass", $Metadata.StorePassword, "-keypass", $Metadata.KeyPassword
            )) @() "local-identity-verify" | Out-Null
        } elseif (-not [string]::IsNullOrWhiteSpace($StorePasswordFile)) {
            Invoke-Keytool $Hooks ($requestArguments + @(
                "-storepass:file", $StorePasswordFile, "-keypass:file", $KeyPasswordFile
            )) @() "local-identity-verify" | Out-Null
        } else {
            Invoke-Keytool $Hooks ($requestArguments + @(
                "-storepass", $Metadata.StorePassword, "-keypass", $Metadata.KeyPassword
            )) @() "local-identity-verify" | Out-Null
        }

        $exported = Get-CertificateBytesFromKeystore $Hooks $Keystore $Metadata.StorePassword `
            $Metadata.Alias $StorePasswordFile
        $certificateBytes = [IO.File]::ReadAllBytes($Certificate)
        if (-not (Compare-Bytes $exported $certificateBytes)) {
            throw "Keystore certificate does not match the public certificate artifact"
        }
    } finally {
        if (Test-Path -LiteralPath $requestPath -PathType Leaf) {
            Remove-Item -LiteralPath $requestPath -Force -ErrorAction Stop
        }
    }
}

function Assert-ArtifactSet(
    [hashtable] $Hooks,
    [string] $Directory,
    [string] $StorePasswordFile = $null,
    [string] $KeyPasswordFile = $null
) {
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        throw "Signing artifact directory is missing"
    }
    $entries = @(Get-ChildItem -LiteralPath $Directory -Force)
    if ($entries.Count -ne $script:AndroidSigningArtifactNames.Count) {
        throw "Signing artifact directory is incomplete"
    }
    foreach ($name in $script:AndroidSigningArtifactNames) {
        $path = Join-Path $Directory $name
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Signing artifact directory is incomplete"
        }
    }

    $metadata = Read-RecoveryMetadata (Join-Path $Directory "meet-release-passwords.txt")
    $certificate = Join-Path $Directory "meet-release.cer"
    $fingerprint = Normalize-Fingerprint ([IO.File]::ReadAllText((Join-Path $Directory "meet-release.sha256")))
    $derived = Get-ArtifactHash $Hooks $certificate
    if ($fingerprint -cne $derived -or $metadata.Fingerprint -cne $derived) {
        throw "Certificate fingerprint does not match the certificate artifact"
    }
    Assert-KeystoreIdentity $Hooks (Join-Path $Directory "meet-release.jks") $certificate `
        $metadata $StorePasswordFile $KeyPasswordFile
    return [pscustomobject]@{
        Metadata = $metadata
        Fingerprint = $derived
    }
}

function Assert-ArtifactSetsEqual([string] $First, [string] $Second) {
    foreach ($name in $script:AndroidSigningArtifactNames) {
        $left = [IO.File]::ReadAllBytes((Join-Path $First $name))
        $right = [IO.File]::ReadAllBytes((Join-Path $Second $name))
        if (-not (Compare-Bytes $left $right)) {
            throw "Local and offline signing artifacts differ"
        }
    }
}

function New-RandomSecret() {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Copy-SigningArtifact(
    [hashtable] $Hooks,
    [string] $Source,
    [string] $Destination,
    [System.Collections.ArrayList] $OwnedFiles,
    [System.Collections.ArrayList] $OwnedDirectories
) {
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Copy")) {
        if ($Hooks.ContainsKey("BeforeCopy")) {
            & $Hooks["BeforeCopy"] $Source $Destination
        }
        $destinationStream = [IO.File]::Open(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        Add-OwnedFile $OwnedFiles $Destination
        try {
            & $Hooks["Copy"] $Source $Destination $destinationStream
            $destinationStream.Flush()
        } catch {
            Remove-OwnedFile $OwnedFiles $Destination
            throw
        } finally {
            $destinationStream.Dispose()
        }
        Set-OwnedFileFingerprint $Destination
        if (Test-IsAclSupportedPath $Destination) {
            Set-OwnerOnlyAcl $Destination $false $Hooks
        }
        return
    }
    Copy-FileExclusively $Source $Destination $OwnedFiles $Hooks
    if (Test-IsAclSupportedPath $Destination) {
        Set-OwnerOnlyAcl $Destination $false $Hooks
    }
}

function Invoke-GitHubWrite(
    [hashtable] $Hooks,
    [string] $Environment,
    [string] $Kind,
    [string] $Name,
    [string] $InputValue,
    [string] $Repository
) {
    $arguments = @($Kind, "set", $Name, "--env", $Environment)
    if (-not [string]::IsNullOrWhiteSpace($Repository)) {
        $arguments += @("--repo", $Repository)
    }
    if ($null -ne $Hooks -and $Hooks.ContainsKey("Gh")) {
        $result = & $Hooks["Gh"] $arguments $InputValue
        if ($null -ne $result -and $result.PSObject.Properties["ExitCode"] -and [int]$result.ExitCode -ne 0) {
            throw "GitHub provisioning command failed"
        }
        return
    }
    $InputValue | & gh @arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub provisioning command failed"
    }
}

function Invoke-AllGitHubProvisioning(
    [hashtable] $Hooks,
    [string] $Keystore,
    [pscustomobject] $Metadata,
    [string] $CertificateFingerprint,
    [string] $Repository
) {
    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore))
    $writes = @(
        @("android-release", "secret", "RELEASE_KEYSTORE_BASE64", $keystoreBase64),
        @("android-release", "secret", "RELEASE_KEYSTORE_PASSWORD", $Metadata.StorePassword),
        @("android-release", "secret", "RELEASE_KEY_PASSWORD", $Metadata.KeyPassword),
        @("android-release", "variable", "RELEASE_CERTIFICATE_SHA256", $CertificateFingerprint),
        @("android-snapshot-signing", "secret", "SNAPSHOT_RELEASE_KEYSTORE_BASE64", $keystoreBase64),
        @("android-snapshot-signing", "secret", "SNAPSHOT_RELEASE_KEYSTORE_PASSWORD", $Metadata.StorePassword),
        @("android-snapshot-signing", "secret", "SNAPSHOT_RELEASE_KEY_PASSWORD", $Metadata.KeyPassword),
        @("android-snapshot-signing", "variable", "RELEASE_CERTIFICATE_SHA256", $CertificateFingerprint)
    )
    foreach ($write in $writes) {
        Invoke-GitHubWrite $Hooks $write[0] $write[1] $write[2] $write[3] $Repository
    }
}

function Remove-OwnedArtifacts(
    [System.Collections.ArrayList] $OwnedFiles,
    [System.Collections.ArrayList] $OwnedDirectories,
    [hashtable] $Hooks
) {
    $cleanupFailures = New-Object System.Collections.ArrayList
    $retryFiles = New-Object System.Collections.ArrayList
    foreach ($path in @($OwnedFiles | Sort-Object Length -Descending)) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                if ($script:OwnedFileFingerprints.ContainsKey($path)) {
                    $expected = $script:OwnedFileFingerprints[$path]
                    $item = Get-Item -LiteralPath $path -Force
                    $actual = [IO.File]::ReadAllBytes($path)
                    if ($item.Length -ne $expected.Length -or
                        $item.CreationTimeUtc -ne $expected.CreationTimeUtc -or
                        -not (Compare-Bytes ([byte[]]$expected.Bytes) $actual)) {
                        throw "Invocation-owned file identity changed"
                    }
                }
                if ($null -ne $Hooks -and $Hooks.ContainsKey("BeforeDelete")) {
                    & $Hooks["BeforeDelete"] $path
                }
                Remove-Item -LiteralPath $path -Force -ErrorAction Stop
            } catch {
                [void]$retryFiles.Add($path)
            }
        }
    }
    foreach ($path in @($retryFiles)) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                if ($script:OwnedFileFingerprints.ContainsKey($path)) {
                    $expected = $script:OwnedFileFingerprints[$path]
                    $item = Get-Item -LiteralPath $path -Force
                    $actual = [IO.File]::ReadAllBytes($path)
                    if ($item.Length -ne $expected.Length -or
                        $item.CreationTimeUtc -ne $expected.CreationTimeUtc -or
                        -not (Compare-Bytes ([byte[]]$expected.Bytes) $actual)) {
                        throw "Invocation-owned file identity changed"
                    }
                }
                if ($null -ne $Hooks -and $Hooks.ContainsKey("BeforeDelete")) {
                    & $Hooks["BeforeDelete"] $path
                }
                Remove-Item -LiteralPath $path -Force -ErrorAction Stop
            } catch {
                [void]$cleanupFailures.Add($path)
            }
        }
    }
    foreach ($path in @($OwnedDirectories | Sort-Object Length -Descending)) {
        if (Test-Path -LiteralPath $path -PathType Container) {
            $children = @(Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue)
            if ($children.Count -eq 0) {
                try {
                    Remove-Item -LiteralPath $path -Force -ErrorAction Stop
                } catch {
                    [void]$cleanupFailures.Add($path)
                }
            }
        }
    }
    if ($cleanupFailures.Count -gt 0) {
        throw "Cleanup failed for invocation-owned paths: $($cleanupFailures -join ', ')"
    }
}

function Remove-OwnedTemporaryFiles(
    [string[]] $Paths,
    [hashtable] $Hooks
) {
    $temporaryFiles = New-Object System.Collections.ArrayList
    foreach ($path in $Paths) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            Add-OwnedFile $temporaryFiles $path
        }
    }
    Remove-OwnedArtifacts $temporaryFiles (New-Object System.Collections.ArrayList) $Hooks
}

function Get-SigningPaths([string] $Backup, [string] $Handoff) {
    return [pscustomobject]@{
        Backup = $Backup
        Handoff = $Handoff
        Keystore = Join-Path $Handoff "meet-release.jks"
        Certificate = Join-Path $Handoff "meet-release.cer"
        Fingerprint = Join-Path $Handoff "meet-release.sha256"
        Recovery = Join-Path $Handoff "meet-release-passwords.txt"
    }
}

function Assert-RequiredCommands([hashtable] $Hooks, [bool] $NeedGh) {
    if (($null -eq $Hooks -or -not $Hooks.ContainsKey("Keytool")) -and
        -not (Get-Command keytool -ErrorAction SilentlyContinue)) {
        throw "keytool is required"
    }
    if ($NeedGh -and ($null -eq $Hooks -or -not $Hooks.ContainsKey("Gh")) -and
        -not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "gh is required"
    }
}

function Invoke-AndroidSigningBootstrap {
    param(
        [ValidateSet("Preflight", "Execute", "Provision")]
        [string] $BootstrapMode,
        [string] $BackupDirectory,
        [string] $HandoffDirectory,
        [bool] $Provision,
        [string] $Repository,
        [hashtable] $Hooks,
        [string] $IntegrationTestAuthority,
        [hashtable] $IntegrationTestIdentity
    )

    $context = New-SigningContext
    $testIdentity = Assert-IntegrationTestContract $BootstrapMode $Provision $Hooks `
        $IntegrationTestAuthority $IntegrationTestIdentity
    Set-SigningStage $context "input-validation"
    $backup = ConvertTo-CanonicalPath $BackupDirectory
    $handoff = ConvertTo-CanonicalPath $HandoffDirectory
    Assert-NoReparsePointPath $backup
    Assert-NoReparsePointPath $handoff
    Assert-SeparateSigningPaths $backup $handoff
    $paths = Get-SigningPaths $backup $handoff
    $ownedFiles = New-Object System.Collections.ArrayList
    $ownedDirectories = New-Object System.Collections.ArrayList
    $backupCommitted = $false
    $temporaryStorePasswordFile = $null
    $temporaryKeyPasswordFile = $null
    $stagingDirectory = $null
    Write-Host "Offline backup directory: $backup"
    Write-Host "Credential handoff directory: $handoff"
    Write-Host "Signing artifacts: meet-release.jks, meet-release.cer, meet-release.sha256, meet-release-passwords.txt"

    if ($BootstrapMode -eq "Provision") {
        Set-SigningStage $context "preflight"
        Assert-RequiredCommands $Hooks $true
        if (-not (Test-Path -LiteralPath $backup -PathType Container) -or
            -not (Test-Path -LiteralPath $handoff -PathType Container)) {
            throw "Provision requires populated handoff and offline backup directories"
        }
        try {
            $metadata = Read-RecoveryMetadata $paths.Recovery
            $temporaryStorePasswordFile = New-SecureEphemeralFile $Hooks $ownedFiles
            [IO.File]::WriteAllText($temporaryStorePasswordFile, $metadata.StorePassword)
            Set-OwnedFileFingerprint $temporaryStorePasswordFile
            $temporaryKeyPasswordFile = New-SecureEphemeralFile $Hooks $ownedFiles
            [IO.File]::WriteAllText($temporaryKeyPasswordFile, $metadata.KeyPassword)
            Set-OwnedFileFingerprint $temporaryKeyPasswordFile
            $local = Assert-ArtifactSet $Hooks $handoff $temporaryStorePasswordFile $temporaryKeyPasswordFile
            $offline = Assert-ArtifactSet $Hooks $backup $temporaryStorePasswordFile $temporaryKeyPasswordFile
            Assert-ArtifactSetsEqual $handoff $backup
            if ($local.Fingerprint -cne $offline.Fingerprint -or
                $local.Metadata.Alias -cne "meet-release" -or
                $local.Metadata.StorePassword -cne $offline.Metadata.StorePassword -or
                $local.Metadata.KeyPassword -cne $offline.Metadata.KeyPassword) {
                throw "Preserved signing identities are inconsistent"
            }
            Invoke-AllGitHubProvisioning $Hooks $paths.Keystore $local.Metadata $local.Fingerprint $Repository
            Write-Host "Provisioned the verified meet-release identity to android-release and android-snapshot-signing."
            return
        } catch {
            if ($null -ne $Hooks -and $Hooks.ContainsKey("Error")) {
                & $Hooks["Error"] $_.Exception.Message
            }
            throw "Provision failed before or during GitHub mutation; handoff and offline backup were preserved. Rerun -Mode Provision with the same paths. No secret values were printed."
        } finally {
            Remove-OwnedArtifacts $ownedFiles $ownedDirectories $Hooks
        }
    }

    if (-not (Test-Path -LiteralPath $backup -PathType Container)) {
        throw "Offline backup directory must already exist"
    }
    if (@(Get-ChildItem -LiteralPath $backup -Force).Count -ne 0) {
        throw "Offline backup directory must be empty for Execute"
    }
    if (Test-Path -LiteralPath $handoff) {
        throw "Credential handoff directory must not already exist for Execute"
    }
    Assert-RequiredCommands $Hooks ($Provision -or $false)

    if ($BootstrapMode -eq "Preflight") {
        Set-SigningStage $context "preflight"
        Write-Host "Preflight passed; no signing material was created."
        return
    }

    $confirmation = if ($null -ne $Hooks -and $Hooks.ContainsKey("Confirm")) {
        [string](& $Hooks["Confirm"])
    } else {
        Read-Host "Type CREATE-ANDROID-RELEASE-KEY to continue"
    }
    if ($confirmation -cne "CREATE-ANDROID-RELEASE-KEY") {
        throw "Explicit confirmation was not provided"
    }

    try {
        Set-SigningStage $context "handoff-create"
        New-Item -ItemType Directory -Path $handoff -ErrorAction Stop | Out-Null
        [void]$ownedDirectories.Add($handoff)
        Set-SigningStage $context "handoff-acl"
        Set-OwnerOnlyAcl $handoff $true $Hooks

        $storePassword = if ($null -ne $Hooks -and $Hooks.ContainsKey("Secret")) {
            [string](& $Hooks["Secret"] "STORE_PASSWORD")
        } else {
            New-RandomSecret
        }
        $keyPassword = if ($null -ne $Hooks -and $Hooks.ContainsKey("Secret")) {
            [string](& $Hooks["Secret"] "KEY_PASSWORD")
        } else {
            New-RandomSecret
        }
        Set-SigningStage $context "secret-temp-create"
        $temporaryStorePasswordFile = New-SecureEphemeralFile $Hooks $ownedFiles
        [IO.File]::WriteAllText($temporaryStorePasswordFile, $storePassword)
        Set-OwnedFileFingerprint $temporaryStorePasswordFile
        $temporaryKeyPasswordFile = New-SecureEphemeralFile $Hooks $ownedFiles
        [IO.File]::WriteAllText($temporaryKeyPasswordFile, $keyPassword)
        Set-OwnedFileFingerprint $temporaryKeyPasswordFile
        $stagingDirectory = New-SecureEphemeralDirectory $Hooks $ownedDirectories
        Assert-NewPath $paths.Keystore
        $stagedKeystore = Join-Path $stagingDirectory "meet-release.jks"
        Assert-NewPath $stagedKeystore
        Set-SigningStage $context "key-generate"
        $generationPasswords = Get-PasswordArguments $Hooks $storePassword $keyPassword `
            $temporaryStorePasswordFile $temporaryKeyPasswordFile
        $validityDays = if ($null -ne $testIdentity) { $testIdentity.ValidityDays } else { 3650 }
        $distinguishedName = if ($null -ne $testIdentity) {
            $testIdentity.DistinguishedName
        } else {
            "CN=Meet Android Release, OU=Release, O=Meet"
        }
        Invoke-Keytool $Hooks (@(
            "-genkeypair", "-v", "-keystore", $stagedKeystore, "-storetype", "JKS"
        ) + $generationPasswords + @(
            "-alias", "meet-release", "-keyalg", "RSA", "-keysize", "4096",
            "-validity", [string]$validityDays, "-dname", $distinguishedName
        )) @() "key-generate" | Out-Null
        if (-not (Test-Path -LiteralPath $stagedKeystore -PathType Leaf)) {
            throw "keytool did not create the staged keystore"
        }
        Add-OwnedFile $ownedFiles $stagedKeystore
        Set-OwnedFileFingerprint $stagedKeystore
        Move-StagedArtifact $stagedKeystore $paths.Keystore $ownedFiles $ownedDirectories $Hooks

        Set-SigningStage $context "certificate-export"
        Assert-NewPath $paths.Certificate
        $stagedCertificate = Join-Path $stagingDirectory "meet-release.cer"
        Assert-NewPath $stagedCertificate
        $exportArguments = @(
            "-exportcert", "-keystore", $paths.Keystore,
            "-alias", "meet-release", "-file", $stagedCertificate
        )
        $exportArguments += @("-storepass:file", $temporaryStorePasswordFile)
        Invoke-Keytool $Hooks $exportArguments @() "certificate-export" | Out-Null
        if (-not (Test-Path -LiteralPath $stagedCertificate -PathType Leaf)) {
            throw "keytool did not create the staged certificate"
        }
        Add-OwnedFile $ownedFiles $stagedCertificate
        Set-OwnedFileFingerprint $stagedCertificate
        Move-StagedArtifact $stagedCertificate $paths.Certificate $ownedFiles $ownedDirectories $Hooks

        Set-SigningStage $context "local-artifact-materialize"
        Assert-NewPath $paths.Fingerprint
        $stagedFingerprint = Join-Path $stagingDirectory "meet-release.sha256"
        Assert-NewPath $stagedFingerprint
        $fingerprint = Get-ArtifactHash $Hooks $paths.Certificate
        Write-ExclusiveText $stagedFingerprint @($fingerprint) $ownedFiles
        Move-StagedArtifact $stagedFingerprint $paths.Fingerprint $ownedFiles $ownedDirectories $Hooks

        Assert-NewPath $paths.Recovery
        $stagedRecovery = Join-Path $stagingDirectory "meet-release-passwords.txt"
        Assert-NewPath $stagedRecovery
        Write-ExclusiveText $stagedRecovery @(
            "STORE_PASSWORD=$storePassword",
            "KEY_PASSWORD=$keyPassword",
            "ALIAS=meet-release",
            "CERTIFICATE_SHA256=$fingerprint"
        ) $ownedFiles
        Move-StagedArtifact $stagedRecovery $paths.Recovery $ownedFiles $ownedDirectories $Hooks
        Set-SigningStage $context "local-identity-verify"
        $local = Assert-ArtifactSet $Hooks $handoff $temporaryStorePasswordFile $temporaryKeyPasswordFile
        foreach ($name in $script:AndroidSigningArtifactNames) {
            Set-SigningStage $context "backup-copy"
            Copy-SigningArtifact $Hooks (Join-Path $handoff $name) (Join-Path $backup $name) `
                $ownedFiles $ownedDirectories
        }
        Set-SigningStage $context "backup-identity-verify"
        $offline = Assert-ArtifactSet $Hooks $backup $temporaryStorePasswordFile $temporaryKeyPasswordFile
        Assert-ArtifactSetsEqual $handoff $backup
        if ($local.Fingerprint -cne $offline.Fingerprint -or
            $local.Metadata.StorePassword -cne $offline.Metadata.StorePassword -or
            $local.Metadata.KeyPassword -cne $offline.Metadata.KeyPassword) {
            throw "Local and offline signing identities are inconsistent"
        }
        Set-SigningStage $context "backup-commit"
        $backupCommitted = $true
        $context.BackupCommitted = $true

        if ($Provision) {
            Set-SigningStage $context "github-environment-write"
            Invoke-AllGitHubProvisioning $Hooks $paths.Keystore $local.Metadata $local.Fingerprint $Repository
            Write-Host "Provisioned signing secrets and variables to both GitHub Environments."
        }
        Write-Host "Generated and verified one meet-release identity with fingerprint $fingerprint."
    } catch {
        $failureStage = Get-SigningFailureStage $_ $context
        $failureCategory = Get-SigningFailureCategory $_
        if (-not $backupCommitted) {
            try {
                Set-SigningStage $context "cleanup"
                Remove-OwnedArtifacts $ownedFiles $ownedDirectories $Hooks
            } catch {
                $context.CleanupComplete = $false
                throw (Format-SigningFailure $failureStage $failureCategory $false $false)
            }
            throw (Format-SigningFailure $failureStage $failureCategory $false $true)
        }
        throw (Format-SigningFailure $failureStage $failureCategory $true $true)
    } finally {
        $temporaryCleanupFailures = New-Object System.Collections.ArrayList
        try {
            Set-SigningStage $context "cleanup"
            Remove-OwnedTemporaryFiles @($temporaryStorePasswordFile, $temporaryKeyPasswordFile) $Hooks
        } catch {
            [void]$temporaryCleanupFailures.Add("temporary invocation-owned files")
        }
        if ($temporaryCleanupFailures.Count -gt 0) {
            throw (Format-SigningFailure (Get-SigningFailureStage $_ $context) "cleanup" $backupCommitted $false)
        }
    }
}

if (-not $Library) {
    Invoke-AndroidSigningBootstrap -BootstrapMode $Mode -BackupDirectory $OfflineBackupDirectory `
        -HandoffDirectory $CredentialHandoffDirectory -Provision $ProvisionGitHubSecrets.IsPresent `
        -Repository $GitHubRepository -Hooks $TestHooks -IntegrationTestAuthority $null `
        -IntegrationTestIdentity $null
}
