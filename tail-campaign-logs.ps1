Write-Host "Monitoring CRBT Campaign Service AI Music Generation Logs..." -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop." -ForegroundColor Yellow

$LogFile = "logs/crbt-campaign-service/app.log"

if (-not (Test-Path $LogFile)) {
    Write-Host "Error: Log file not found at $LogFile" -ForegroundColor Red
    Exit 1
}

Get-Content -Path $LogFile -Wait -Tail 50 | Select-String -Pattern "GENERATE-START|WALLET-DEDUCT|LYRIA-CACHE|LYRIA-GENERATE|LYRIA-API|WALLET-REFUND|GENERATE-SUCCESS|GENERATE-FAILURE"
