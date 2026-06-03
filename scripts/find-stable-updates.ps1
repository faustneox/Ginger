$ErrorActionPreference = 'Stop'
$libsFile = "gradle\libs.versions.toml"
$buildFile = "app\build.gradle.kts"
if (-not (Test-Path $libsFile)) { Write-Error "File $libsFile not found"; exit 1 }

$lines = Get-Content $libsFile

$inVersions = $false
$versions = @{}
foreach ($line in $lines) {
    $t = $line.Trim()
    if ($t -eq '[versions]') { $inVersions = $true; continue }
    if ($inVersions -and $t -match '^\[') { $inVersions = $false }
    if ($inVersions) {
        $m = [regex]::Match($line, '^\s*([^=\s]+)\s*=\s*"(.*)"')
        if ($m.Success) { $versions[$m.Groups[1].Value] = $m.Groups[2].Value }
    }
}

# parse libraries
$inLibraries = $false
$libs = @()
foreach ($line in $lines) {
    $t = $line.Trim()
    if ($t -eq '[libraries]') { $inLibraries = $true; continue }
    if ($inLibraries -and $t -match '^\[') { break }
    $m = [regex]::Match($line, '^\s*([^=]+?)\s*=\s*\{(.*)\}')
    if ($m.Success) {
        $key = $m.Groups[1].Value.Trim()
        $inside = $m.Groups[2].Value
        $g = [regex]::Match($inside, 'group\s*=\s*"(.*?)"')
        $n = [regex]::Match($inside, 'name\s*=\s*"(.*?)"')
        $v = [regex]::Match($inside, 'version\.ref\s*=\s*"(.*?)"')
        $versionRef = if ($v.Success) { $v.Groups[1].Value } else { $null }
        $currentVersion = if ($versionRef -and $versions.ContainsKey($versionRef)) { $versions[$versionRef] } else { '' }
        if ($g.Success -and $n.Success) {
            $libs += [pscustomobject]@{ group=$g.Groups[1].Value; artifact=$n.Groups[1].Value; versionRef=$versionRef; currentVersion=$currentVersion }
        }
    }
}

# parse string-literal dependencies in build.gradle.kts
$deps = @()
if (Test-Path $buildFile) {
    $buildLines = Get-Content $buildFile
    foreach ($l in $buildLines) {
        $matches = [regex]::Matches($l, '"([A-Za-z0-9\._\-\+]+):([A-Za-z0-9\._\-]+):([^\"]+)"')
        foreach ($m in $matches) {
            $g = $m.Groups[1].Value; $a = $m.Groups[2].Value; $ver = $m.Groups[3].Value
            $k = $g + ":" + $a
            if (-not ($deps | Where-Object { $_.group -eq $g -and $_.artifact -eq $a })) {
                $tmpl = [regex]::Match($ver, '\$\{libs\.versions\.([^\}]+)\.get\(\)\}')
                if ($tmpl.Success -and $versions.ContainsKey($tmpl.Groups[1].Value)) { $ver = $versions[$tmpl.Groups[1].Value] }
                $deps += [pscustomobject]@{ group=$g; artifact=$a; currentVersion=$ver }
            }
        }
    }
}

# Merge unique queries
$queries = @{}
foreach ($entry in $libs) { $k = $entry.group + ":" + $entry.artifact; if (-not $queries.ContainsKey($k)) { $queries[$k] = [pscustomobject]@{ group=$entry.group; artifact=$entry.artifact; versionRef=$entry.versionRef; currentVersion=$entry.currentVersion } } }
foreach ($d in $deps) { $k = $d.group + ":" + $d.artifact; if (-not $queries.ContainsKey($k)) { $queries[$k] = [pscustomobject]@{ group=$d.group; artifact=$d.artifact; versionRef=''; currentVersion=$d.currentVersion } } }

function Get-Latest-Stable($group, $artifact, $currentVersion) {
    $groupPath = $group.Replace('.', '/')
    $urls = @("https://dl.google.com/dl/android/maven2/$groupPath/$artifact/maven-metadata.xml", "https://repo1.maven.org/maven2/$groupPath/$artifact/maven-metadata.xml")
    $versions = @()
    foreach ($url in $urls) {
        try {
            $xml = Invoke-RestMethod -Uri $url -UseBasicParsing -ErrorAction Stop -TimeoutSec 30
            if ($xml.versioning -and $xml.versioning.versions.version) {
                foreach ($v in $xml.versioning.versions.version) { $versions += [string]$v }
            }
        } catch { }
    }
    if ($versions.Count -eq 0) { return $null }
    $stable = $versions | Where-Object { $_ -notmatch '(?i)(alpha|beta|rc|m|eap|preview|snapshot)' }
    if ($stable.Count -eq 0) { return $null }
    $candidate = $stable[-1]
    if (-not $currentVersion) { return $candidate }
    $m1 = [regex]::Match($currentVersion, '^\s*(\d+)')
    $m2 = [regex]::Match($candidate, '^\s*(\d+)')
    if ($m1.Success -and $m2.Success) {
        if ([int]$m1.Groups[1].Value -eq [int]$m2.Groups[1].Value) { return $candidate } else { return $null }
    } else {
        return $null
    }
}

$result = @()
foreach ($q in $queries.Values) {
    $latest = Get-Latest-Stable $q.group $q.artifact $q.currentVersion
    $result += [pscustomobject]@{ group=$q.group; artifact=$q.artifact; versionRef=$q.versionRef; currentVersion=$q.currentVersion; latestStable=$latest }
}

$outFile = 'scripts\stable-candidates.json'
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath $outFile -Encoding utf8
Write-Output "Wrote $outFile"
