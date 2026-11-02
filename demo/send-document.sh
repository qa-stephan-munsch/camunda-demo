#!/usr/bin/env bash
#
# Sends a document to the Kafka topic documents-in, which starts one document-routing instance.
# Uses the Kafka CLI inside the kafka container, so no local Kafka tools are needed.
#
# Usage: demo/send-document.sh <document.json>
#   e.g. demo/send-document.sh demo/samples/invoice.json
#
# Works with the bash 3.2 that ships with macOS.

set -euo pipefail

if [ $# -ne 1 ] || [ ! -r "$1" ]; then
  sed -n '3,7p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 1
fi

# the console producer sends one message per line
tr -d '\n' < "$1" | docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic documents-in

echo "Sent $1 to documents-in"
