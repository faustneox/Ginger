<#
Usage: run this script from any location; it will change directory to the repository root.
PowerShell example:
  .\scripts\create_pr_and_push.ps1 -Email "you@example.com" -Name "Your Name"

This script:
- creates/updates branch `ci/add-test-report-workflow`
- stages files added by the assistant and commits with a message including `[ci run]`
- pushes the branch to `origin`
- attempts to create a PR with `gh` if the GitHub CLI is installed
#>

param(
    [string]$Remote = 'origin',
    [string]$Branch = 'ci/add-test-report-workflow',
    [string]$Base = 'main',
    [string]$Email = 'you@example.com',
    [string]$Name = 'Your Name'
)

# Determine repository root (assumes this script is in <repo>/scripts)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
Set-Location -Path $repoRoot

Write-Host "Repository root: $repoRoot"

# Ensure git is available
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error "git is not available in this environment. Install git and re-run the script."
    exit 1
}

# Fetch and prepare branch
git fetch $Remote
git checkout -B $Branch

# Stage files
git add .github/workflows/android-test-results.yml gradle/libs.versions.toml app/build.gradle.kts pr_body.md

# Configure user if provided
if ($Email) { git config user.email $Email }
if ($Name) { git config user.name $Name }

# Commit (if there are changes to commit)
$changes = git status --porcelain
if ([string]::IsNullOrWhiteSpace($changes)) {
    Write-Host "No changes to commit."
} else {
    git commit -m "chore(ci): add workflow to run unit tests and upload results; align Kotlin stdlib [ci run]"
}

# Push branch
git push -u $Remote $Branch

# Create PR with gh if available
if (Get-Command gh -ErrorAction SilentlyContinue) {
    gh pr create --title "chore(ci): add unit tests workflow and align Kotlin stdlib [ci run]" --body-file pr_body.md --base $Base
} else {
    Write-Host "gh CLI not found. Install GitHub CLI (https://cli.github.com/) or create the PR via GitHub web UI."
}
