$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
Set-Location $backendRoot

# Load environment variables from .env file
$envFile = Join-Path $backendRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
    Write-Host '.env loaded' -ForegroundColor Green
} else {
    Write-Warning '.env file not found — AI_PROVIDER_API_KEY may not be set'
}

docker compose up -d postgres redis kafka kafka-ui qdrant

$services = @(
    'auth-service',
    'task-service',
    'email-service',
    'ai-agent-service',
    'document-service',
    'report-service',
    'notification-service',
    'workflow-service',
    'gateway-service'
)

foreach ($service in $services) {
    $serviceDir = Join-Path $backendRoot $service
    Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$serviceDir'; Write-Host 'Starting $service ...' -ForegroundColor Cyan; mvn spring-boot:run"
}