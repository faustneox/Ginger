$ErrorActionPreference = 'Stop'
$excludePatterns = @('\\.git', '\\gradle\\wrapper\\', '\\.gradle')
$all = Get-ChildItem -Path . -Recurse -Force -File -ErrorAction SilentlyContinue
$files = foreach ($f in $all) {
    $p = $f.FullName
    $skip = $false
    foreach ($pat in $excludePatterns) { if ($p -match $pat) { $skip = $true; break } }
    if (-not $skip) { $f }
}
$top = $files | Sort-Object Length -Descending | Select-Object @{Name='Path';Expression={$_.FullName}}, @{Name='SizeMB';Expression={[math]::Round($_.Length/1MB,2)}} -First 200
$out = 'scripts\largest-files.json'
$top | ConvertTo-Json -Depth 3 | Out-File -FilePath $out -Encoding utf8
Write-Output "Wrote $out"
