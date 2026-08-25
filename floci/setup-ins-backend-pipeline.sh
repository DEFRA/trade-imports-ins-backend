#!/usr/bin/env bash
# Floci init script — creates the SNS→SQS notification pipeline for the ins-backend.
# Staged by the workspace stack and run from the amazon/aws-cli floci-init sidecar.
set -euo pipefail

ENDPOINT="${FLOCI_URL:-http://localhost:4566}"
export AWS_REGION="${AWS_REGION:-eu-west-2}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"

aws() { command aws --endpoint-url="$ENDPOINT" --region "$AWS_REGION" "$@"; }

QUEUE_NAME="trade_imports_animals_eu_notifications_ins_backend.fifo"
TOPIC_NAME="trade_imports_animals_eu_notifications.fifo"
ACCOUNT="000000000000"

echo "Creating FIFO queue: ${QUEUE_NAME}"
aws sqs create-queue \
  --queue-name "${QUEUE_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=false || true

QUEUE_ARN="arn:aws:sqs:${AWS_REGION}:${ACCOUNT}:${QUEUE_NAME}"

echo "Creating SNS FIFO topic: ${TOPIC_NAME}"
aws sns create-topic \
  --name "${TOPIC_NAME}" \
  --attributes FifoTopic=true,ContentBasedDeduplication=false || true

TOPIC_ARN="arn:aws:sns:${AWS_REGION}:${ACCOUNT}:${TOPIC_NAME}"

echo "Subscribing queue to topic with raw message delivery"
aws sns subscribe \
  --topic-arn "${TOPIC_ARN}" \
  --protocol sqs \
  --notification-endpoint "${QUEUE_ARN}" \
  --attributes RawMessageDelivery=true

echo "Floci ins-backend pipeline ready"
