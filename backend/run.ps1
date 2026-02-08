# SDLCraft Backend Startup Script (Ollama + Integrations)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Load environment variables from .env file
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment from .env..." -ForegroundColor Gray
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
            Write-Host "  Loaded: $name" -ForegroundColor DarkGray
        }
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   SDLCraft Backend" -ForegroundColor Cyan  
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Make sure Ollama is running: " -NoNewline
Write-Host "ollama serve" -ForegroundColor Yellow
Write-Host ""

# Show integrations status
Write-Host "Integrations:" -ForegroundColor White

if ($env:JIRA_URL) {
    Write-Host "  Jira:   $env:JIRA_URL" -ForegroundColor Green
} else {
    Write-Host "  Jira:   Not configured" -ForegroundColor DarkGray
}

if ($env:GITHUB_TOKEN) {
    Write-Host "  GitHub: Configured" -ForegroundColor Green
} else {
    Write-Host "  GitHub: Not configured" -ForegroundColor DarkGray
}

Write-Host ""

mvn spring-boot:run
