param(
    [string]$AuthBaseUrl = "http://localhost:8081",
    [string]$PaymentBaseUrl = "http://localhost:8085",
    [string]$WalletBaseUrl = "http://localhost:8086",
    [string]$CampaignBaseUrl = "http://localhost:8090",
    [string]$LibraryBaseUrl = "http://localhost:8091",
    [string]$AudioBaseUrl = "http://localhost:8092",
    [string]$CoreAdapterBaseUrl = "http://localhost:8094",
    [string]$FileBaseUrl = "http://localhost:8083",
    [string]$AuditBaseUrl = "http://localhost:8084",
    [string]$CreditTransactionBaseUrl = "http://localhost:8093",
    [string]$Email = "uat.microservice@example.com",
    [string]$Password = "Password@12345",
    [string]$Msisdn = "84960000999",
    [string]$PackageCode = "UAT_CRBT_001",
    [long]$PaymentAmountMmk = 1000,
    [int]$CreditAmount = 10,
    [string]$RingtoneUrl = "https://example.com/audio/uat-ringtone.mp3",
    [string]$TargetBucket = "media-audio",
    [string]$ReportDir = "reports/uat",
    [switch]$Strict
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$RunId = "uat-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$StartedAt = (Get-Date).ToUniversalTime().ToString("o")
$Results = New-Object System.Collections.ArrayList
$State = @{
    AccessToken = $null
    RefreshToken = $null
    UserId = $null
    CategoryId = $null
    MoodId = $null
    RingtoneId = $null
    CampaignId = $null
    PackageId = $null
    AudioJobId = $null
    AssignmentId = $null
    FileId = $null
    IdempotencyKey = $RunId + "-payment"
}

function ConvertTo-JsonBody {
    param([object]$Body)
    if ($null -eq $Body) { return $null }
    if ($Body -is [byte[]]) { return $Body }
    return ($Body | ConvertTo-Json -Depth 20 -Compress)
}

function Get-DataValue {
    param([object]$Response)
    if ($null -eq $Response) { return $null }
    if ($Response.PSObject.Properties.Name -contains "data") { return $Response.data }
    return $Response
}

function Get-FirstValue {
    param([object]$Data)
    if ($null -eq $Data) { return $null }
    if ($Data -is [System.Array]) {
        if ($Data.Length -gt 0) { return $Data[0] }
        return $null
    }
    if ($Data.PSObject.Properties.Name -contains "content") {
        $content = $Data.content
        if ($content -is [System.Array] -and $content.Length -gt 0) { return $content[0] }
    }
    if ($Data.PSObject.Properties.Name -contains "items") {
        $items = $Data.items
        if ($items -is [System.Array] -and $items.Length -gt 0) { return $items[0] }
    }
    return $Data
}

function Get-PropertyValue {
    param([object]$Object, [string[]]$Names)
    if ($null -eq $Object) { return $null }
    foreach ($name in $Names) {
        if ($Object.PSObject.Properties.Name -contains $name) { return $Object.$name }
    }
    return $null
}

function New-Headers {
    param([string]$CorrelationId)
    $headers = @{
        "Accept" = "application/json"
        "X-Correlation-ID" = $CorrelationId
        "X-User-Id" = if ($State.UserId) { [string]$State.UserId } else { "1" }
        "X-User-Email" = $Email
        "X-User-Roles" = "ADMIN,USER"
    }
    if ($State.AccessToken) { $headers["Authorization"] = "Bearer $($State.AccessToken)" }
    return $headers
}

function Redact-Text {
    param([string]$Text)
    if (-not $Text) { return "" }
    $redacted = $Text -replace '"accessToken"\s*:\s*"[^"]+"', '"accessToken":"<redacted>"'
    $redacted = $redacted -replace '"refreshToken"\s*:\s*"[^"]+"', '"refreshToken":"<redacted>"'
    $redacted = $redacted -replace 'Bearer\s+[A-Za-z0-9._~+/=-]+', 'Bearer <redacted>'
    return $redacted
}

function Add-Result {
    param(
        [int]$Seq,
        [string]$Service,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Status,
        [Nullable[int]]$StatusCode,
        [long]$DurationMs,
        [string]$CorrelationId,
        [string]$Message,
        [string]$ResponseExcerpt
    )
    $row = [pscustomobject]@{
        seq = $Seq
        service = $Service
        name = $Name
        method = $Method
        url = $Url
        status = $Status
        statusCode = $StatusCode
        durationMs = $DurationMs
        correlationId = $CorrelationId
        message = $Message
        responseExcerpt = Redact-Text $ResponseExcerpt
    }
    [void]$Results.Add($row)
    $color = "White"
    if ($Status -eq "PASS") { $color = "Green" }
    elseif ($Status -eq "WARN") { $color = "Yellow" }
    elseif ($Status -eq "FAIL") { $color = "Red" }
    elseif ($Status -eq "SKIP") { $color = "DarkYellow" }
    Write-Host ("[{0}] {1:000} {2} {3} {4}" -f $Status, $Seq, $Service, $Method, $Name) -ForegroundColor $color
}

function Invoke-UatStep {
    param(
        [int]$Seq,
        [string]$Service,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [scriptblock]$Validate = $null,
        [scriptblock]$Capture = $null,
        [switch]$Optional,
        [switch]$AllowClientError,
        [switch]$RequiresUser,
        [switch]$RequiresToken,
        [string]$ContentType = "application/json",
        [hashtable]$ExtraHeaders = @{}
    )

    if ($RequiresUser -and -not $State.UserId) {
        Add-Result $Seq $Service $Name $Method $Url "SKIP" $null 0 "" "Missing userId dependency" ""
        return $null
    }
    if ($RequiresToken -and -not $State.AccessToken) {
        Add-Result $Seq $Service $Name $Method $Url "SKIP" $null 0 "" "Missing access token dependency" ""
        return $null
    }

    $correlationId = $RunId + "-" + $Seq.ToString("000")
    $headers = New-Headers $correlationId
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $bodyValue = ConvertTo-JsonBody $Body
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = $null
    $responseText = ""
    $json = $null

    try {
        $params = @{
            Method = $Method
            Uri = $Url
            Headers = $headers
            TimeoutSec = 45
            UseBasicParsing = $true
        }
        if ($null -ne $Body) { $params["Body"] = $bodyValue }
        if ($ContentType) { $params["ContentType"] = $ContentType }
        $response = Invoke-WebRequest @params
        $timer.Stop()
        $statusCode = [int]$response.StatusCode
        $responseText = [string]$response.Content
        if ($responseText) {
            try { $json = $responseText | ConvertFrom-Json } catch { $json = $null }
        }
        $status = "PASS"
        $message = "HTTP $statusCode"
        if ($Validate) {
            $validation = & $Validate $json $responseText $statusCode
            if ($validation -is [string] -and $validation.Length -gt 0) {
                $status = "FAIL"
                $message = $validation
            }
        }
        if ($Capture -and $status -eq "PASS") { & $Capture $json }
        Add-Result $Seq $Service $Name $Method $Url $status $statusCode $timer.ElapsedMilliseconds $correlationId $message $responseText.Substring(0, [Math]::Min(500, $responseText.Length))
        return $json
    }
    catch {
        $timer.Stop()
        $message = $_.Exception.Message
        if ($_.Exception -and $_.Exception.PSObject.Properties['Response'] -ne $null -and $_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseText = $reader.ReadToEnd()
                }
            } catch {}
        }
        $status = "FAIL"
        if (($Optional -or $AllowClientError) -and $statusCode -ge 400 -and $statusCode -lt 500) { $status = "WARN" }
        Add-Result $Seq $Service $Name $Method $Url $status $statusCode $timer.ElapsedMilliseconds $correlationId $message $responseText.Substring(0, [Math]::Min(500, $responseText.Length))
        return $null
    }
}

function Test-AnySuccess {
    param($Json, $Text, $StatusCode)
    if ($StatusCode -ge 200 -and $StatusCode -lt 300) { return "" }
    return "Expected 2xx"
}

function Test-HasData {
    param($Json, $Text, $StatusCode)
    if ($StatusCode -lt 200 -or $StatusCode -ge 300) { return "Expected 2xx" }
    if ($null -eq $Json) { return "Expected JSON response" }
    if ($Json.PSObject.Properties.Name -contains "data") { return "" }
    return "Expected ApiResponse.data"
}

function Save-Reports {
    if (-not (Test-Path $ReportDir)) { New-Item -ItemType Directory -Force $ReportDir | Out-Null }
    $completedAt = (Get-Date).ToUniversalTime().ToString("o")
    $summary = [pscustomobject]@{
        total = $Results.Count
        passed = @($Results | Where-Object { $_.status -eq "PASS" }).Count
        failed = @($Results | Where-Object { $_.status -eq "FAIL" }).Count
        warnings = @($Results | Where-Object { $_.status -eq "WARN" }).Count
        skipped = @($Results | Where-Object { $_.status -eq "SKIP" }).Count
    }
    $report = [pscustomobject]@{
        runId = $RunId
        startedAt = $StartedAt
        completedAt = $completedAt
        summary = $summary
        results = $Results
    }
    $jsonPath = Join-Path $ReportDir ("uat-e2e-{0}.json" -f $RunId)
    $csvPath = Join-Path $ReportDir ("uat-e2e-{0}.csv" -f $RunId)
    $report | ConvertTo-Json -Depth 20 | Out-File -FilePath $jsonPath -Encoding utf8
    $Results | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $csvPath
    Write-Host "Report JSON: $jsonPath"
    Write-Host "Report CSV:  $csvPath"
    Write-Host ("Summary: total={0} pass={1} warn={2} fail={3} skip={4}" -f $summary.total, $summary.passed, $summary.warnings, $summary.failed, $summary.skipped)
    if ($Strict -and $summary.failed -gt 0) { exit 1 }
}

try {
    Write-Host "Phase 8 QE/UAT E2E run: $RunId"

    Invoke-UatStep 1 "auth-service" "Auth register" "POST" "$AuthBaseUrl/api/auth/register" @{ email = $Email; password = $Password } ${function:Test-HasData} $null -AllowClientError
    Invoke-UatStep 2 "auth-service" "Auth login" "POST" "$AuthBaseUrl/api/auth/login" @{ email = $Email; password = $Password } ${function:Test-HasData} { param($json) $data = Get-DataValue $json; $State.AccessToken = Get-PropertyValue $data @("accessToken"); $State.RefreshToken = Get-PropertyValue $data @("refreshToken"); $userId = Get-PropertyValue $data @("userId", "id"); if ($userId) { $State.UserId = $userId } }
    Invoke-UatStep 3 "auth-service" "Auth refresh" "POST" "$AuthBaseUrl/api/auth/refresh" @{ refreshToken = $State.RefreshToken } ${function:Test-HasData} { param($json) $data = Get-DataValue $json; $State.AccessToken = Get-PropertyValue $data @("accessToken") } -Optional
    Invoke-UatStep 4 "auth-service" "CRBT provision" "POST" "$AuthBaseUrl/internal/crbt/provision" @{ msisdn = $Msisdn } ${function:Test-AnySuccess} { param($json) $data = Get-DataValue $json; $State.UserId = Get-PropertyValue $data @("userId", "id") }
    Invoke-UatStep 5 "auth-service" "CRBT user credit" "GET" "$AuthBaseUrl/internal/crbt/user-credit/$Msisdn" $null ${function:Test-AnySuccess} { param($json) $data = Get-DataValue $json; $userId = Get-PropertyValue $data @("userId", "id"); if ($userId) { $State.UserId = $userId } } -AllowClientError

    Invoke-UatStep 6 "payment-gateway-service" "Payment charge" "POST" "$PaymentBaseUrl/api/payments/charge" @{ idempotencyKey = $State.IdempotencyKey; packageCode = $PackageCode; amountMmk = $PaymentAmountMmk; creditAmount = $CreditAmount } ${function:Test-HasData} $null -RequiresUser
    Invoke-UatStep 7 "payment-gateway-service" "Payment idempotency lookup" "GET" "$PaymentBaseUrl/api/payments/idempotency/$($State.IdempotencyKey)" $null ${function:Test-AnySuccess} $null -AllowClientError
    Invoke-UatStep 8 "credit-wallet-service" "Wallet internal balance" "GET" "$WalletBaseUrl/api/wallet/internal/$($State.UserId)/balance" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    Invoke-UatStep 9 "credit-wallet-service" "Wallet add" "POST" "$WalletBaseUrl/api/wallet/$($State.UserId)/add" @{ amount = 5; reason = "UAT_ADD"; referenceId = "$RunId-wallet-add" } ${function:Test-HasData} $null -RequiresUser
    Invoke-UatStep 10 "credit-wallet-service" "Wallet deduct" "POST" "$WalletBaseUrl/api/wallet/$($State.UserId)/deduct" @{ amount = 1; reason = "UAT_DEDUCT"; referenceId = "$RunId-wallet-deduct" } ${function:Test-HasData} $null -RequiresUser
    Invoke-UatStep 11 "credit-wallet-service" "Wallet me" "GET" "$WalletBaseUrl/api/wallet/me" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError

    $start = (Get-Date).ToUniversalTime().AddMinutes(-5).ToString("o")
    $end = (Get-Date).ToUniversalTime().AddDays(7).ToString("o")
    Invoke-UatStep 12 "crbt-campaign-service" "Campaign create" "POST" "$CampaignBaseUrl/campaigns" @{ name = "UAT Campaign $RunId"; description = "UAT campaign"; startAt = $start; endAt = $end; packages = @(@{ name = "UAT Package"; price = 1000; creditAmount = $CreditAmount; validityDays = 30 }) } ${function:Test-HasData} { param($json) $data = Get-DataValue $json; $State.CampaignId = Get-PropertyValue $data @("id"); $packages = Get-PropertyValue $data @("packages"); if ($packages) { $State.PackageId = if ($packages -is [System.Array]) { Get-PropertyValue $packages[0] @("id") } else { Get-PropertyValue $packages @("id") } } } -RequiresUser
    Invoke-UatStep 13 "crbt-campaign-service" "Campaign active list" "GET" "$CampaignBaseUrl/campaigns/active" $null ${function:Test-AnySuccess} { param($json) if (-not $State.PackageId) { $first = Get-FirstValue (Get-DataValue $json); $packages = Get-PropertyValue $first @("packages"); if ($packages) { $State.PackageId = if ($packages -is [System.Array]) { Get-PropertyValue $packages[0] @("id") } else { Get-PropertyValue $packages @("id") } } } }
    if ($State.PackageId) { Invoke-UatStep 14 "crbt-campaign-service" "Campaign subscribe" "POST" "$CampaignBaseUrl/campaigns/subscribe" @{ packageId = [long]$State.PackageId } ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError } else { Add-Result 14 "crbt-campaign-service" "Campaign subscribe" "POST" "$CampaignBaseUrl/campaigns/subscribe" "SKIP" $null 0 "" "Missing packageId dependency" "" }
    Invoke-UatStep 15 "crbt-campaign-service" "Campaign generate" "POST" "$CampaignBaseUrl/campaigns/generate?genre=Pop&mood=Happy" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError

    Invoke-UatStep 16 "crbt-community-library" "Create category" "POST" "$LibraryBaseUrl/library/categories" @{ name = "UAT Category $RunId"; description = "UAT category" } ${function:Test-HasData} { param($json) $State.CategoryId = Get-PropertyValue (Get-DataValue $json) @("id") }
    Invoke-UatStep 17 "crbt-community-library" "List categories" "GET" "$LibraryBaseUrl/library/categories" $null ${function:Test-AnySuccess} $null
    if ($State.CategoryId) { Invoke-UatStep 18 "crbt-community-library" "Update category" "PUT" "$LibraryBaseUrl/library/categories/$($State.CategoryId)" @{ name = "UAT Category Updated $RunId"; description = "UAT category updated" } ${function:Test-HasData} $null } else { Add-Result 18 "crbt-community-library" "Update category" "PUT" "$LibraryBaseUrl/library/categories/{id}" "SKIP" $null 0 "" "Missing categoryId dependency" "" }
    Invoke-UatStep 19 "crbt-community-library" "Create mood" "POST" "$LibraryBaseUrl/library/moods" @{ name = "UAT Mood $RunId"; description = "UAT mood" } ${function:Test-HasData} { param($json) $State.MoodId = Get-PropertyValue (Get-DataValue $json) @("id") }
    Invoke-UatStep 20 "crbt-community-library" "List moods" "GET" "$LibraryBaseUrl/library/moods" $null ${function:Test-AnySuccess} $null
    if ($State.MoodId) { Invoke-UatStep 21 "crbt-community-library" "Update mood" "PUT" "$LibraryBaseUrl/library/moods/$($State.MoodId)" @{ name = "UAT Mood Updated $RunId"; description = "UAT mood updated" } ${function:Test-HasData} $null } else { Add-Result 21 "crbt-community-library" "Update mood" "PUT" "$LibraryBaseUrl/library/moods/{id}" "SKIP" $null 0 "" "Missing moodId dependency" "" }
    if ($State.CategoryId -and $State.MoodId) { Invoke-UatStep 22 "crbt-community-library" "Create ringtone" "POST" "$LibraryBaseUrl/library/ringtones" @{ title = "UAT Ringtone $RunId"; artistName = "UAT Artist"; audioUrl = $RingtoneUrl; coverImageUrl = "https://example.com/cover.png"; durationSeconds = 30; featured = $true; moodId = [long]$State.MoodId; status = $true; categoryId = [long]$State.CategoryId } ${function:Test-HasData} { param($json) $State.RingtoneId = Get-PropertyValue (Get-DataValue $json) @("id") } } else { Add-Result 22 "crbt-community-library" "Create ringtone" "POST" "$LibraryBaseUrl/library/ringtones" "SKIP" $null 0 "" "Missing categoryId or moodId dependency" "" }
    if ($State.RingtoneId) { Invoke-UatStep 23 "crbt-community-library" "Update ringtone" "PUT" "$LibraryBaseUrl/library/ringtones/$($State.RingtoneId)" @{ title = "UAT Ringtone Updated $RunId"; artistName = "UAT Artist"; audioUrl = $RingtoneUrl; coverImageUrl = "https://example.com/cover.png"; durationSeconds = 31; featured = $false; moodId = [long]$State.MoodId; status = $true; categoryId = [long]$State.CategoryId } ${function:Test-HasData} $null } else { Add-Result 23 "crbt-community-library" "Update ringtone" "PUT" "$LibraryBaseUrl/library/ringtones/{id}" "SKIP" $null 0 "" "Missing ringtoneId dependency" "" }
    if ($State.RingtoneId) { Invoke-UatStep 24 "crbt-community-library" "Patch ringtone status" "PATCH" "$LibraryBaseUrl/library/ringtones/$($State.RingtoneId)/status" @{ status = $true } ${function:Test-AnySuccess} $null } else { Add-Result 24 "crbt-community-library" "Patch ringtone status" "PATCH" "$LibraryBaseUrl/library/ringtones/{id}/status" "SKIP" $null 0 "" "Missing ringtoneId dependency" "" }
    Invoke-UatStep 25 "crbt-community-library" "Search ringtones" "GET" "$LibraryBaseUrl/library/ringtones/search?q=UAT" $null ${function:Test-AnySuccess} $null
    Invoke-UatStep 26 "crbt-community-library" "Ringtone statistics" "GET" "$LibraryBaseUrl/library/ringtones/statistics" $null ${function:Test-AnySuccess} $null
    Invoke-UatStep 27 "crbt-community-library" "Export ringtones" "GET" "$LibraryBaseUrl/library/ringtones/export" $null ${function:Test-AnySuccess} $null
    Invoke-UatStep 28 "crbt-community-library" "Random ringtone" "GET" "$LibraryBaseUrl/library/ringtones/random" $null ${function:Test-AnySuccess} $null -AllowClientError

    Invoke-UatStep 29 "audio-generation-service" "Audio job submit" "POST" "$AudioBaseUrl/audio-jobs" @{ prompt = "Create short upbeat UAT CRBT tune"; voiceId = "vi-VN-HoaiMyNeural"; type = "MUSIC"; audioFileKey = $null; vocalStart = $null; vocalEnd = $null } ${function:Test-HasData} { param($json) $State.AudioJobId = Get-PropertyValue (Get-DataValue $json) @("id") } -RequiresUser -AllowClientError
    Invoke-UatStep 30 "audio-generation-service" "Audio job list" "GET" "$AudioBaseUrl/audio-jobs" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    if ($State.AudioJobId) { Invoke-UatStep 31 "audio-generation-service" "Audio job detail" "GET" "$AudioBaseUrl/audio-jobs/$($State.AudioJobId)" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError } else { Add-Result 31 "audio-generation-service" "Audio job detail" "GET" "$AudioBaseUrl/audio-jobs/{jobId}" "SKIP" $null 0 "" "Missing audioJobId dependency" "" }
    Invoke-UatStep 32 "audio-generation-service" "Audio analyze" "POST" "$AudioBaseUrl/audio-jobs/analyze" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    Invoke-UatStep 33 "audio-generation-service" "DIY generate" "POST" "$AudioBaseUrl/diy/generate" @{ prompt = "Create DIY UAT CRBT tune"; voiceId = "vi-VN-HoaiMyNeural"; type = "DIY"; audioFileKey = $null; vocalStart = $null; vocalEnd = $null } ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    Invoke-UatStep 34 "audio-generation-service" "DIY analyze" "POST" "$AudioBaseUrl/diy/analyze" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError

    Invoke-UatStep 35 "crbt-core-adapter" "Assign ringtone" "POST" "$CoreAdapterBaseUrl/ringtone-assignments" @{ msisdn = $Msisdn; ringtoneUrl = $RingtoneUrl } ${function:Test-HasData} { param($json) $State.AssignmentId = Get-PropertyValue (Get-DataValue $json) @("id") } -AllowClientError
    Invoke-UatStep 36 "crbt-core-adapter" "List assignments" "GET" "$CoreAdapterBaseUrl/ringtone-assignments?msisdn=$Msisdn" $null ${function:Test-AnySuccess} $null -AllowClientError

    Invoke-UatStep 37 "file-service" "Presigned upload URL" "GET" "$FileBaseUrl/api/files/presigned/upload?originalName=uat-audio.mp3&contentType=audio/mpeg" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    $boundary = [System.Guid]::NewGuid().ToString()
    $bytes = [System.Text.Encoding]::ASCII.GetBytes("--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"uat-audio.mp3`"`r`nContent-Type: audio/mpeg`r`n`r`nUAT-AUDIO`r`n--$boundary--`r`n")
    Invoke-UatStep 38 "file-service" "Multipart upload" "POST" "$FileBaseUrl/api/files/upload" $bytes ${function:Test-HasData} { param($json) $State.FileId = Get-PropertyValue (Get-DataValue $json) @("id") } -RequiresUser -ContentType "multipart/form-data; boundary=$boundary"
    if ($State.FileId) { Invoke-UatStep 39 "file-service" "Confirm file" "POST" "$FileBaseUrl/api/files/$($State.FileId)/confirm" @{ targetBucket = $TargetBucket } ${function:Test-HasData} $null -RequiresUser -AllowClientError } else { Add-Result 39 "file-service" "Confirm file" "POST" "$FileBaseUrl/api/files/{fileId}/confirm" "SKIP" $null 0 "" "Missing fileId dependency" "" }
    if ($State.FileId) { Invoke-UatStep 40 "file-service" "Presigned download URL" "GET" "$FileBaseUrl/api/files/$($State.FileId)/presigned/download" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError } else { Add-Result 40 "file-service" "Presigned download URL" "GET" "$FileBaseUrl/api/files/{fileId}/presigned/download" "SKIP" $null 0 "" "Missing fileId dependency" "" }
    Invoke-UatStep 41 "file-service" "Internal upload audio" "POST" "$FileBaseUrl/api/files/internal/upload-audio?bucket=$TargetBucket" ([System.Text.Encoding]::ASCII.GetBytes("UAT-AUDIO")) ${function:Test-AnySuccess} $null -ContentType "application/octet-stream" -AllowClientError

    Invoke-UatStep 42 "crbt-credit-transaction-service" "Credit transaction history" "GET" "$CreditTransactionBaseUrl/credit-transactions/history" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    Invoke-UatStep 43 "crbt-credit-transaction-service" "Credit transaction export" "GET" "$CreditTransactionBaseUrl/credit-transactions/export" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError
    Invoke-UatStep 44 "audit-log-service" "Audit query" "GET" "$AuditBaseUrl/audit/query" $null ${function:Test-AnySuccess} $null -AllowClientError

    if ($State.AssignmentId) { Invoke-UatStep 45 "crbt-core-adapter" "Remove assignment" "DELETE" "$CoreAdapterBaseUrl/ringtone-assignments/$($State.AssignmentId)" $null ${function:Test-AnySuccess} $null -AllowClientError } else { Add-Result 45 "crbt-core-adapter" "Remove assignment" "DELETE" "$CoreAdapterBaseUrl/ringtone-assignments/{assignmentId}" "SKIP" $null 0 "" "Missing assignmentId dependency" "" }
    if ($State.FileId) { Invoke-UatStep 46 "file-service" "Delete file" "DELETE" "$FileBaseUrl/api/files/$($State.FileId)" $null ${function:Test-AnySuccess} $null -RequiresUser -AllowClientError } else { Add-Result 46 "file-service" "Delete file" "DELETE" "$FileBaseUrl/api/files/{fileId}" "SKIP" $null 0 "" "Missing fileId dependency" "" }
    if ($State.RingtoneId) { Invoke-UatStep 47 "crbt-community-library" "Delete ringtone" "DELETE" "$LibraryBaseUrl/library/ringtones/$($State.RingtoneId)" $null ${function:Test-AnySuccess} $null -AllowClientError } else { Add-Result 47 "crbt-community-library" "Delete ringtone" "DELETE" "$LibraryBaseUrl/library/ringtones/{id}" "SKIP" $null 0 "" "Missing ringtoneId dependency" "" }
    if ($State.MoodId) { Invoke-UatStep 48 "crbt-community-library" "Delete mood" "DELETE" "$LibraryBaseUrl/library/moods/$($State.MoodId)" $null ${function:Test-AnySuccess} $null -AllowClientError } else { Add-Result 48 "crbt-community-library" "Delete mood" "DELETE" "$LibraryBaseUrl/library/moods/{id}" "SKIP" $null 0 "" "Missing moodId dependency" "" }
    if ($State.CategoryId) { Invoke-UatStep 49 "crbt-community-library" "Delete category" "DELETE" "$LibraryBaseUrl/library/categories/$($State.CategoryId)" $null ${function:Test-AnySuccess} $null -AllowClientError } else { Add-Result 49 "crbt-community-library" "Delete category" "DELETE" "$LibraryBaseUrl/library/categories/{id}" "SKIP" $null 0 "" "Missing categoryId dependency" "" }

    Save-Reports
}
catch {
    Add-Result 999 "uat-runner" "Unhandled runner exception" "N/A" "N/A" "FAIL" $null 0 "" $_.Exception.Message ""
    Save-Reports
    if ($Strict) { exit 1 }
}
