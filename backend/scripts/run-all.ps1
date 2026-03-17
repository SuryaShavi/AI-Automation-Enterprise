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
    Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$backendRoot'; mvn -pl $service -am spring-boot:run"
}