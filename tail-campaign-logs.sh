#!/bin/bash
echo "Monitoring CRBT Campaign Service AI Music Generation Logs..."
echo "Press Ctrl+C to stop."
tail -f logs/crbt-campaign-service/app.log | grep --line-buffered -E "GENERATE-START|WALLET-DEDUCT|LYRIA-CACHE|LYRIA-GENERATE|LYRIA-API|WALLET-REFUND|GENERATE-SUCCESS|GENERATE-FAILURE"
