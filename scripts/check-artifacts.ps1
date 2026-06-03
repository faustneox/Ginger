$ErrorActionPreference='Stop'
function LatestStable($group,$artifact){
    $gp = $group.Replace('.','/')
    $urls = @("https://dl.google.com/dl/android/maven2/$gp/$artifact/maven-metadata.xml", "https://repo1.maven.org/maven2/$gp/$artifact/maven-metadata.xml")
    $versions = @()
    foreach ($u in $urls) {
        try {
            $xml = Invoke-RestMethod -Uri $u -UseBasicParsing -ErrorAction Stop -TimeoutSec 30
            if ($xml.versioning -and $xml.versioning.versions.version) { foreach ($v in $xml.versioning.versions.version) { $versions += [string]$v } }
        } catch { }
    }
    $stable = $versions | Where-Object { $_ -notmatch '(?i)(alpha|beta|rc|m|eap|preview|snapshot)' }
    if ($stable.Count -gt 0) { return $stable[-1] } else { return '' }
}

$artifacts = @(
    @{g='androidx.core'; a='core-ktx'},
    @{g='androidx.appcompat'; a='appcompat'},
    @{g='com.google.android.material'; a='material'},
    @{g='androidx.recyclerview'; a='recyclerview'},
    @{g='com.github.chrisbanes'; a='PhotoView'},
    @{g='androidx.swiperefreshlayout'; a='swiperefreshlayout'}
)
foreach ($it in $artifacts) {
    $res = LatestStable $it.g $it.a
    Write-Output ("$($it.g):$($it.a) -> $res")
}
