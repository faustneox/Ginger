$in = "log_upload.txt"
$out = "log_filtered.txt"
$pattern = 'StorageException|FirebaseException|ChatRepository|ChatViewModel|Object does not exist|Please sign in|UnknownHostException|Unable to resolve host|App Check|AppCheck'

if (-not (Test-Path $in)) { Write-Error "Input file $in not found"; exit 1 }

$lines = Get-Content $in -Encoding Unicode
$outLines = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $lines.Count; $i++) {
    try {
        if ($lines[$i] -match $pattern) {
            $outLines.Add("---- MATCH at line $($i+1) ----")
            $outLines.Add($lines[$i])
            for ($j = 1; $j -le 3; $j++) {
                if ($i + $j -lt $lines.Count) { $outLines.Add($lines[$i + $j]) }
            }
        }
    } catch {
        # ignore lines that can't be matched
    }
}

$outLines | Out-File $out -Encoding UTF8
Write-Output "Wrote $($outLines.Count) lines to $out"