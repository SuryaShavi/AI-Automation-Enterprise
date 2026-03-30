param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TimeoutSec = 30,
    [int]$AsyncPollAttempts = 15,
    [int]$AsyncPollDelaySec = 2
)

$ErrorActionPreference = "Stop"

try {
    Add-Type -AssemblyName System.Net.Http
} catch {
    # Assembly may already be loaded.
}

$results = New-Object System.Collections.ArrayList

function Add-Result {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Details
    )

    $null = $results.Add([PSCustomObject]@{
        Test = $Name
        Passed = $Passed
        Details = $Details
    })

    if ($Passed) {
        Write-Host "[PASS] $Name - $Details" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Name - $Details" -ForegroundColor Red
    }
}

function Get-ErrorMessage {
    param([System.Management.Automation.ErrorRecord]$Err)

    try {
        if ($Err.Exception.Response -and $Err.Exception.Response.GetResponseStream()) {
            $reader = New-Object System.IO.StreamReader($Err.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            if ($body) {
                return $body
            }
        }
    } catch {
        # Ignore stream parsing failure.
    }

    return $Err.Exception.Message
}

function Invoke-ApiJson {
    param(
        [ValidateSet("GET", "POST", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$AccessToken = $null,
        [switch]$SkipEnvelope
    )

    $headers = @{}
    if ($AccessToken) {
        $headers["Authorization"] = "Bearer $AccessToken"
    }

    $uri = "$BaseUrl$Path"

    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 10
        $response = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $json -ContentType "application/json" -TimeoutSec $TimeoutSec
    } else {
        $response = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -TimeoutSec $TimeoutSec
    }

    if ($SkipEnvelope) {
        return $response
    }

    if (-not $response) {
        throw "Empty response from $Path"
    }

    if ($response.error) {
        $msg = if ($response.error.message) { $response.error.message } else { "Unknown API envelope error" }
        throw "API error at ${Path}: $msg"
    }

    return $response.data
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Upload-Document {
    param(
        [string]$FilePath,
        [string]$AccessToken
    )

    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.Timeout = [System.TimeSpan]::FromSeconds($TimeoutSec)
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $AccessToken)

        $multipart = New-Object System.Net.Http.MultipartFormDataContent
        $stream = [System.IO.File]::OpenRead($FilePath)
        try {
            $fileContent = New-Object System.Net.Http.StreamContent($stream)
            $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream")
            $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))

            $response = $client.PostAsync("$BaseUrl/documents/upload", $multipart).GetAwaiter().GetResult()
            $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        } finally {
            $stream.Dispose()
            $multipart.Dispose()
        }

        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $($response.StatusCode) when uploading document: $raw"
        }

        $envelope = $raw | ConvertFrom-Json
        if ($envelope.error) {
            $msg = if ($envelope.error.message) { $envelope.error.message } else { "Document upload returned envelope error" }
            throw $msg
        }

        return $envelope.data
    } finally {
        $client.Dispose()
    }
}

Write-Host "Running v1 mandatory freeze validation against $BaseUrl" -ForegroundColor Cyan

$accessToken = $null
$refreshToken = $null
$createdTaskId = $null
$ingestedEmailId = $null
$documentId = $null
$chatId = $null
$generatedReportId = $null

# 1) Gateway reachability
try {
    $statusCode = 0
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/health/services" -TimeoutSec $TimeoutSec
        $statusCode = [int]$resp.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        } else {
            throw
        }
    }

    Assert-True -Condition (($statusCode -eq 200) -or ($statusCode -eq 401)) -Message "Unexpected status code from gateway health route: $statusCode"
    Add-Result -Name "Gateway route availability" -Passed $true -Details "Gateway is reachable (status $statusCode)"
} catch {
    Add-Result -Name "Gateway route availability" -Passed $false -Details (Get-ErrorMessage $_)
}

# 2) Auth register + me + refresh
$email = "freeze.v1.$([guid]::NewGuid().ToString('N').Substring(0,10))@example.com"
$password = "FreezePass1A"

try {
    $register = Invoke-ApiJson -Method "POST" -Path "/auth/register" -Body @{
        email = $email
        firstName = "Freeze"
        lastName = "Tester"
        password = $password
    }

    $accessToken = $register.accessToken
    $refreshToken = $register.refreshToken

    Assert-True -Condition ([string]::IsNullOrWhiteSpace($accessToken) -eq $false) -Message "Access token missing after register"
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($refreshToken) -eq $false) -Message "Refresh token missing after register"

    $me = Invoke-ApiJson -Method "GET" -Path "/auth/me" -AccessToken $accessToken
    Assert-True -Condition ($me.email -eq $email) -Message "auth/me email mismatch"

    $refreshed = Invoke-ApiJson -Method "POST" -Path "/auth/refresh" -Body @{ refreshToken = $refreshToken }
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($refreshed.accessToken) -eq $false) -Message "Refresh endpoint did not return new access token"
    $accessToken = $refreshed.accessToken
    $refreshToken = $refreshed.refreshToken

    Add-Result -Name "Auth flow" -Passed $true -Details "Register, auth/me, and refresh succeeded"
} catch {
    Add-Result -Name "Auth flow" -Passed $false -Details (Get-ErrorMessage $_)
}

# 3) Task CRUD and board
try {
    $dueAt = (Get-Date).AddDays(2).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

    $createdTask = Invoke-ApiJson -Method "POST" -Path "/tasks" -AccessToken $accessToken -Body @{
        title = "Freeze validation task"
        description = "Created by mandatory freeze validation"
        priority = "MEDIUM"
        dueAt = $dueAt
    }

    $createdTaskId = $createdTask.id
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($createdTaskId) -eq $false) -Message "Task id missing"

    $taskList = Invoke-ApiJson -Method "GET" -Path "/tasks" -AccessToken $accessToken
    Assert-True -Condition ($taskList.items.Count -ge 1) -Message "Task list is empty"

    $updatedTask = Invoke-ApiJson -Method "PATCH" -Path "/tasks/$createdTaskId" -AccessToken $accessToken -Body @{
        status = "IN_PROGRESS"
    }
    Assert-True -Condition ($updatedTask.status -eq "IN_PROGRESS") -Message "Task status update failed"

    $board = Invoke-ApiJson -Method "GET" -Path "/tasks/board" -AccessToken $accessToken
    Assert-True -Condition ($board.columns.Count -ge 1) -Message "Task board columns missing"

    Add-Result -Name "Task CRUD + board" -Passed $true -Details "Create/list/update/board endpoints succeeded"
} catch {
    Add-Result -Name "Task CRUD + board" -Passed $false -Details (Get-ErrorMessage $_)
}

# 4) Email ingest + extraction
$extractedTitles = @()
try {
    $ingest = Invoke-ApiJson -Method "POST" -Path "/emails/ingest" -AccessToken $accessToken -Body @{
        senderName = "Manager"
        senderEmail = "manager@example.com"
        subject = "Please complete sales report by Friday"
        bodyText = "Please complete sales report by Friday and share with leadership."
        priority = "HIGH"
    }

    $ingestedEmailId = $ingest.id
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($ingestedEmailId) -eq $false) -Message "Email ingest did not return id"

    $extract = Invoke-ApiJson -Method "POST" -Path "/emails/$ingestedEmailId/extract-tasks" -AccessToken $accessToken
    $taskCount = if ($extract.tasks) { $extract.tasks.Count } else { 0 }
    Assert-True -Condition ($taskCount -ge 1) -Message "No tasks extracted from email"

    foreach ($t in $extract.tasks) {
        if ($t.title) {
            $extractedTitles += $t.title
        }
    }

    Add-Result -Name "Email ingest + task extraction" -Passed $true -Details "Email ingested and $taskCount tasks extracted"
} catch {
    Add-Result -Name "Email ingest + task extraction" -Passed $false -Details (Get-ErrorMessage $_)
}

# 5) Async check: extracted task -> aieap.tasks
try {
    Assert-True -Condition ($extractedTitles.Count -gt 0) -Message "Cannot validate async task creation; no extracted titles"

    $found = $false
    for ($i = 0; $i -lt $AsyncPollAttempts; $i++) {
        $taskList = Invoke-ApiJson -Method "GET" -Path "/tasks" -AccessToken $accessToken
        foreach ($item in $taskList.items) {
            if ($extractedTitles -contains $item.title) {
                $found = $true
                break
            }
        }

        if ($found) { break }
        Start-Sleep -Seconds $AsyncPollDelaySec
    }

    Assert-True -Condition $found -Message "Extracted tasks were not auto-created in tasks list (Kafka flow may be down)"
    Add-Result -Name "Email->Task async automation" -Passed $true -Details "Extracted task was observed in /tasks"
} catch {
    Add-Result -Name "Email->Task async automation" -Passed $false -Details (Get-ErrorMessage $_)
}

# 6) Document upload + ask
$tmpDoc = Join-Path $env:TEMP ("freeze-doc-" + [guid]::NewGuid().ToString("N") + ".txt")
Set-Content -Path $tmpDoc -Value "Refund policy: refunds are allowed within 30 days with receipt." -Encoding UTF8

try {
    $uploadData = Upload-Document -FilePath $tmpDoc -AccessToken $accessToken
    $documentId = $uploadData.id
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($documentId) -eq $false) -Message "Document upload did not return id"

    $ask = Invoke-ApiJson -Method "POST" -Path "/documents/$documentId/ask" -AccessToken $accessToken -Body @{ question = "What is the refund window?" }
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($ask.answer) -eq $false) -Message "Document ask returned empty answer"

    Add-Result -Name "Document upload + ask" -Passed $true -Details "RAG flow returned a non-empty answer"
} catch {
    Add-Result -Name "Document upload + ask" -Passed $false -Details (Get-ErrorMessage $_)
} finally {
    if (Test-Path $tmpDoc) { Remove-Item $tmpDoc -Force }
}

# 7) AI chat core
try {
    $chat = Invoke-ApiJson -Method "POST" -Path "/ai/chat" -AccessToken $accessToken -Body @{
        chatId = $null
        prompt = "What tasks are pending today?"
        mode = "general"
        attachments = @()
    }

    $chatId = $chat.chatId
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($chatId) -eq $false) -Message "Chat id missing"
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($chat.message.content) -eq $false) -Message "AI chat returned empty message"

    $messages = Invoke-ApiJson -Method "GET" -Path "/ai/chats/$chatId/messages" -AccessToken $accessToken
    Assert-True -Condition ($messages.Count -ge 1) -Message "Chat history is empty"

    Add-Result -Name "AI assistant chat" -Passed $true -Details "Chat and history retrieval succeeded"
} catch {
    Add-Result -Name "AI assistant chat" -Passed $false -Details (Get-ErrorMessage $_)
}

# 8) Notifications inbox
try {
    $notifications = Invoke-ApiJson -Method "GET" -Path "/notifications" -AccessToken $accessToken
    Assert-True -Condition ($null -ne $notifications.items) -Message "Notifications list did not return items"

    $null = Invoke-ApiJson -Method "PATCH" -Path "/notifications/read-all" -AccessToken $accessToken

    Add-Result -Name "Notifications inbox" -Passed $true -Details "List and mark-all-read succeeded"
} catch {
    Add-Result -Name "Notifications inbox" -Passed $false -Details (Get-ErrorMessage $_)
}

# 9) Dashboard essentials
try {
    $metrics = Invoke-ApiJson -Method "GET" -Path "/dashboard/metrics" -AccessToken $accessToken
    Assert-True -Condition ($metrics.totalTasks -ne $null) -Message "Dashboard metrics missing totalTasks"
    Assert-True -Condition ($metrics.pendingTasks -ne $null) -Message "Dashboard metrics missing pendingTasks"
    Assert-True -Condition ($metrics.completedTasks -ne $null) -Message "Dashboard metrics missing completedTasks"

    $activity = Invoke-ApiJson -Method "GET" -Path "/dashboard/activity" -AccessToken $accessToken
    Assert-True -Condition ($null -ne $activity) -Message "Dashboard activity missing"

    $services = Invoke-ApiJson -Method "GET" -Path "/health/services" -AccessToken $accessToken
    Assert-True -Condition (@($services).Count -ge 1) -Message "Service health returned no services"

    Add-Result -Name "Dashboard essentials" -Passed $true -Details "Metrics, activity, and health endpoints succeeded"
} catch {
    Add-Result -Name "Dashboard essentials" -Passed $false -Details (Get-ErrorMessage $_)
}

# 10) Report generation smoke
try {
    $report = Invoke-ApiJson -Method "POST" -Path "/reports/generate" -AccessToken $accessToken -Body @{
        reportType = "WEEKLY_PRODUCTIVITY"
        title = "Freeze Validation Report"
        parameters = @{}
    }

    $generatedReportId = $report.id
    Assert-True -Condition ([string]::IsNullOrWhiteSpace($generatedReportId) -eq $false) -Message "Report generate did not return id"

    $reportDetail = Invoke-ApiJson -Method "GET" -Path "/reports/$generatedReportId" -AccessToken $accessToken
    Assert-True -Condition ($reportDetail.id -eq $generatedReportId) -Message "Report detail id mismatch"

    Add-Result -Name "Report generation" -Passed $true -Details "Generate and fetch report succeeded"
} catch {
    Add-Result -Name "Report generation" -Passed $false -Details (Get-ErrorMessage $_)
}

# 11) Task delete cleanup
try {
    if ($createdTaskId) {
        $null = Invoke-ApiJson -Method "DELETE" -Path "/tasks/$createdTaskId" -AccessToken $accessToken
    }
    Add-Result -Name "Task cleanup" -Passed $true -Details "Cleanup completed"
} catch {
    Add-Result -Name "Task cleanup" -Passed $false -Details (Get-ErrorMessage $_)
}

Write-Host ""
Write-Host "========== Freeze v1 Validation Summary ==========" -ForegroundColor Cyan
$results | Format-Table -AutoSize

$failed = @($results | Where-Object { -not $_.Passed })
if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "FAILED: $($failed.Count) mandatory checks failed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "SUCCESS: all mandatory v1 freeze checks passed." -ForegroundColor Green
exit 0
