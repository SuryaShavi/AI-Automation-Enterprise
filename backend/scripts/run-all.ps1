$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
Set-Location $backendRoot

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