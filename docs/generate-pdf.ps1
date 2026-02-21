# Generate PDF from BiasharaHub System Documentation (Markdown)
# Requires: Pandoc (https://pandoc.org/). Install: choco install pandoc, or download from pandoc.org

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$mdFile   = Join-Path $scriptDir "BiasharaHub_System_Documentation.md"
$pdfFile  = Join-Path $scriptDir "BiasharaHub_System_Documentation.pdf"

if (-not (Test-Path $mdFile)) {
    Write-Error "Markdown file not found: $mdFile"
    exit 1
}

$pandoc = Get-Command pandoc -ErrorAction SilentlyContinue
if (-not $pandoc) {
    Write-Host "Pandoc not found. Install from https://pandoc.org/ or run: choco install pandoc"
    Write-Host "Alternatively:"
    Write-Host "  - Open the .md file in VS Code and use 'Markdown PDF' extension to export PDF"
    Write-Host "  - Or use an online Markdown-to-PDF converter"
    exit 1
}

Write-Host "Generating PDF from $mdFile ..."
& pandoc $mdFile -o $pdfFile -f markdown -t pdf --pdf-engine=xelatex "-Vgeometry:margin=1in" 2>$null
if ($LASTEXITCODE -ne 0) {
    # Fallback without xelatex (uses default engine)
    & pandoc $mdFile -o $pdfFile -f markdown -t pdf
}
if (Test-Path $pdfFile) {
    Write-Host "PDF saved to: $pdfFile"
} else {
    Write-Host "PDF generation failed. Try: pandoc BiasharaHub_System_Documentation.md -o BiasharaHub_System_Documentation.pdf"
}
