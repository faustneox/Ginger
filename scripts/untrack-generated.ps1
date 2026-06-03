# Remove generated/CI/log files from git index but keep local copies
$patterns = @('^ci-logs/','^local-run-logs/','^logcat_dump.txt$','^gradle-deps-debug.txt$','^build_output','^build.log$','^build_compile.log$','^compile_stacktrace','^debugUnitTestCompileDeps','^test_out','^test_run_log.txt$','^tmp_parse_output.txt$','^unit_test_results.txt$','^scripts/latest-versions.json$','^scripts/stable-candidates.json$')
$files = git ls-files | Where-Object { $p = $_; foreach($pat in $patterns){ if ($p -match $pat){ return $true } }; $false }
if (-not $files) { Write-Host "No tracked files matched patterns."; exit 0 }
Write-Host "Files matched (count: $($files.Count)):`n"
$files | ForEach-Object { Write-Host " - $_" }

# Remove tracked directories explicitly (if present)
git rm -r --cached --ignore-unmatch ci-logs local-run-logs 2>$null

# Remove individual files from index (keep local copies)
foreach ($f in $files) {
    if ($f -notmatch '^(ci-logs/|local-run-logs/)') {
        git rm --cached --ignore-unmatch "$f"
    }
}

# Stage .gitignore and commit
git add .gitignore
try {
    git commit -m "chore: remove generated logs/CI artifacts from repo and add to .gitignore"
} catch {
    Write-Host "No commit needed or commit failed: $($_)"
}

# Push current branch
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
Write-Host "Pushing branch $branch..."
try {
    git push --set-upstream origin $branch
} catch {
    Write-Host "Push failed or already up-to-date: $($_)"
}

Write-Host "Done."