#!/usr/bin/env bash
set -euo pipefail

curl -X POST 'http://localhost:4567/sensorUpdates' \
  -H 'Content-Type: application/json' \
  -d '{
    "deviceId": "spotter-7.stedi.local",
    "sensorType": "ultraSonicSensor",
    "distance": "1cm",
    "timestamp": 1659538351527,
    "message": "Sample update from spotter-7",
    "messageOrigin": "DEVICE",
    "date": 1659538351527
  }'
