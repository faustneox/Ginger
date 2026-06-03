$ErrorActionPreference = 'Stop'
$libsFile = "gradle\libs.versions.toml"
$buildFile = "app\build.gradle.kts"
if (-not (Test-Path $libsFile)) { Write-Error "File $libsFile not found"; exit 1 }

# Read versions section
$lines = Get-Content $libsFile
$inVersions = $false
$versions = @{}
foreach ($line in $lines) {
    $t = $line.Trim()
    if ($t -eq '[versions]') { $inVersions = $true; continue }
    if ($inVersions -and $t.StartsWith('[')) { break }
    $m = [regex]::Match($line, '^\s*([^\s=]+)\s*=\s*"([^\"]+)"')
    if ($m.Success) { $versions[$m.Groups[1].Value] = $m.Groups[2].Value }
}

# Parse libraries
$libs = @()
foreach ($line in $lines) {
    $m = [regex]::Match($line, '^\s*([^=]+?)\s*=\s*\{(.*)\}')
    if ($m.Success) {
        $key = $m.Groups[1].Value.Trim()
        $inside = $m.Groups[2].Value
        $g = [regex]::Match($inside, 'group\s*=\s*"([^"]+)"')
        $n = [regex]::Match($inside, 'name\s*=\s*"([^"]+)"')
        $v = [regex]::Match($inside, 'version\.ref\s*=\s*"([^"]+)"')
        if ($g.Success -and $n.Success -and $v.Success) {
            $libs += [pscustomobject]@{
                key = $key
                group = $g.Groups[1].Value
                artifact = $n.Groups[1].Value
                versionRef = $v.Groups[1].Value
                currentVersion = ($versions[$v.Groups[1].Value] -as [string])
            }
        }
    }
}

# Parse string-literal dependencies in build.gradle.kts
$deps = @{}
if (Test-Path $buildFile) {
    $buildLines = Get-Content $buildFile
    foreach ($l in $buildLines) {
        $matches = [regex]::Matches($l, '"([A-Za-z0-9\._\-\+]+):([A-Za-z0-9\._\-]+):([^"]+)"')
        foreach ($m in $matches) {
            $g = $m.Groups[1].Value; $a = $m.Groups[2].Value; $ver = $m.Groups[3].Value
            $k = $g + ":" + $a
            if (-not $deps.ContainsKey($k)) {
                    # Resolve ${libs.versions.xxx.get()} template to actual value when possible
                    $resolvedVer = $ver
                    $tmpl = [regex]::Match($ver, '\$\{libs\.versions\.([^)]+)\.get\(\)\}')
                    if ($tmpl.Success) {
                        $key = $tmpl.Groups[1].Value
                        if ($versions.ContainsKey($key)) { $resolvedVer = $versions[$key] }
                    }
                    $deps[$k] = [pscustomobject]@{ group=$g; artifact=$a; currentVersion=$resolvedVer }
            }
        }
    }
}

# Merge unique queries
$queries = @{}
foreach ($lib in $libs) { $k = "$($lib.group):$($lib.artifact)"; if (-not $queries.ContainsKey($k)) { $queries[$k] = [pscustomobject]@{ group=$lib.group; artifact=$lib.artifact; source='libs'; versionRef=$lib.versionRef; currentVersion=$lib.currentVersion } } }
foreach ($d in $deps.Values) { $k = "$($d.group):$($d.artifact)"; if (-not $queries.ContainsKey($k)) { $queries[$k] = [pscustomobject]@{ group=$d.group; artifact=$d.artifact; source='build'; currentVersion=$d.currentVersion } } }

function Get-Latest($g, $a) {
    $gEnc = [uri]::EscapeDataString($g)
    $aEnc = [uri]::EscapeDataString($a)
    $url = ("https://search.maven.org/solrsearch/select?q=g:%22{0}%22%20AND%20a:%22{1}%22&rows=1&wt=json" -f $gEnc, $aEnc)
    try {
        $r = Invoke-RestMethod -Uri $url -UseBasicParsing -ErrorAction Stop -TimeoutSec 30
        if ($r.response.docs.Count -gt 0) {
            $doc = $r.response.docs[0]
            $res = $null
            if ($doc.PSObject.Properties['latestVersion'] -and $doc.latestVersion) { $res = [string]$doc.latestVersion }
            elseif ($doc.PSObject.Properties['v'] -and $doc.v) { $res = [string]$doc.v }
            return $res
        }
    } catch {
        return $null
    }
    return $null
}

$result = @()
foreach ($q in $queries.Values) {
    $latest = Get-Latest $q.group $q.artifact
    $src = if ($q.PSObject.Properties['source'] -and $q.source) { [string]$q.source } else { 'libs' }
    $vr = if ($q.PSObject.Properties['versionRef'] -and $q.versionRef) { [string]$q.versionRef } else { '' }
    $cv = if ($q.PSObject.Properties['currentVersion'] -and $q.currentVersion) { [string]$q.currentVersion } else { '' }
    $lv = if ($latest) { [string]$latest } else { '' }
    $result += [pscustomobject]@{
        group = [string]$q.group
        artifact = [string]$q.artifact
        source = $src
        versionRef = $vr
        currentVersion = $cv
        latestVersion = $lv
    }
}

$outFile = 'scripts\latest-versions.json'
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath $outFile -Encoding utf8
Write-Output "Wrote $outFile"
