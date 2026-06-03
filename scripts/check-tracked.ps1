$ErrorActionPreference = 'Stop'
$files = Get-Content scripts\largest-files.json | ConvertFrom-Json
$repoRoot = (Get-Location).Path
$result = @()
foreach ($f in $files) {
    $full = $f.Path
    $rel = $full -replace ([regex]::Escape($repoRoot + '\\')), ''
    $rel = $rel -replace '\\', '/'  # use forward slashes for git
    $tracked = $false
    try { git ls-files --error-unmatch $rel > $null 2>&1; if ($LASTEXITCODE -eq 0) { $tracked = $true } } catch { $tracked = $false }
    $result += [pscustomobject]@{ Path = $rel; SizeMB = $f.SizeMB; Tracked = $tracked }
}
$out = 'scripts\largest-tracked.json'
$result | ConvertTo-Json -Depth 3 | Out-File -FilePath $out -Encoding utf8
Write-Output "Wrote $out"
