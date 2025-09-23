#!/usr/bin/env bash
set -euo pipefail

# Cross-platform current timestamp in ms (uses Perl for BSD/macOS compatibility)
ts=$(perl -MTime::HiRes=time -e 'printf("%.0f\n", time()*1000)')

payload=$(cat <<JSON
{
  "deviceId": "spotter-7.stedi.local",
  "sensorType": "ultraSonicSensor",
  "distance": "1cm",
  "timestamp": ${ts},
  "message": "Sample update from spotter-7 at ${ts}",
  "messageOrigin": "DEVICE",
  "date": ${ts}
}
JSON
)

curl -sS -X POST 'http://localhost:4567/sensorUpdates' \
  -H 'Content-Type: application/json' \
  -d "${payload}"
