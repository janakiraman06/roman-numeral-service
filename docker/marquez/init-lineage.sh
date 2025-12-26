#!/bin/bash
# ============================================
# Marquez Lineage Initialization Script
# ============================================
# Seeds Marquez with the Roman Numeral Service data lineage graph
# Run automatically by marquez-init container on startup
# ============================================

MARQUEZ_URL="${MARQUEZ_URL:-http://marquez:5000}"
NAMESPACE="rns-data-platform"

echo "Waiting for Marquez to be ready..."
until curl -sf "${MARQUEZ_URL}/api/v1/namespaces" > /dev/null 2>&1; do
  echo "Marquez not ready, retrying in 5s..."
  sleep 5
done
echo "Marquez is ready!"

# Create namespace
echo "Creating namespace: ${NAMESPACE}"
curl -sf -X PUT "${MARQUEZ_URL}/api/v1/namespaces/${NAMESPACE}" \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "data-platform", "description": "Roman Numeral Service Data Platform"}' || true

# Create sources
echo "Creating sources..."
curl -sf -X PUT "${MARQUEZ_URL}/api/v1/sources/kafka" \
  -H "Content-Type: application/json" \
  -d '{"type": "KAFKA", "connectionUrl": "kafka:9092", "description": "Kafka event streaming"}' || true

curl -sf -X PUT "${MARQUEZ_URL}/api/v1/sources/iceberg" \
  -H "Content-Type: application/json" \
  -d '{"type": "ICEBERG", "connectionUrl": "http://iceberg-rest:8181", "description": "Iceberg REST Catalog"}' || true

# Function to send OpenLineage run event
send_lineage_event() {
  local job_name=$1
  local job_namespace=$2
  local inputs=$3
  local outputs=$4
  
  local run_id=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "$(date +%s)-init")
  local event_time=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
  
  # START event
  curl -sf -X POST "${MARQUEZ_URL}/api/v1/lineage" \
    -H "Content-Type: application/json" \
    -d "{
      \"eventType\": \"START\",
      \"eventTime\": \"${event_time}\",
      \"run\": {\"runId\": \"${run_id}\"},
      \"job\": {\"namespace\": \"${job_namespace}\", \"name\": \"${job_name}\"},
      \"inputs\": ${inputs},
      \"outputs\": ${outputs},
      \"producer\": \"marquez-init\"
    }" || true
  
  # COMPLETE event
  curl -sf -X POST "${MARQUEZ_URL}/api/v1/lineage" \
    -H "Content-Type: application/json" \
    -d "{
      \"eventType\": \"COMPLETE\",
      \"eventTime\": \"${event_time}\",
      \"run\": {\"runId\": \"${run_id}\"},
      \"job\": {\"namespace\": \"${job_namespace}\", \"name\": \"${job_name}\"},
      \"inputs\": ${inputs},
      \"outputs\": ${outputs},
      \"producer\": \"marquez-init\"
    }" || true
}

echo "Creating lineage graph..."

# Bronze Ingestion: Kafka → Bronze
echo "  → Bronze Ingestion (Kafka → Bronze)"
send_lineage_event "bronze-ingestion" "${NAMESPACE}" \
  '[{"namespace": "kafka", "name": "roman-numeral-events"}]' \
  '[{"namespace": "iceberg", "name": "bronze.raw_conversion_events"}]'

# Silver ELT: Bronze → Silver
echo "  → Silver ELT (Bronze → Silver)"
send_lineage_event "silver-elt" "${NAMESPACE}" \
  '[{"namespace": "iceberg", "name": "bronze.raw_conversion_events"}]' \
  '[{"namespace": "iceberg", "name": "silver.fact_conversions"}, {"namespace": "iceberg", "name": "silver.dim_users"}]'

# Gold ELT: Silver → Gold
echo "  → Gold ELT (Silver → Gold)"
send_lineage_event "gold-elt" "${NAMESPACE}" \
  '[{"namespace": "iceberg", "name": "silver.fact_conversions"}, {"namespace": "iceberg", "name": "silver.dim_users"}]' \
  '[{"namespace": "iceberg", "name": "gold.daily_conversion_summary"}, {"namespace": "iceberg", "name": "gold.user_metrics"}, {"namespace": "iceberg", "name": "gold.popular_numbers"}]'

echo ""
echo "✅ Marquez lineage initialized!"
echo "   View at: http://localhost:3001"
echo "   Namespace: ${NAMESPACE}"
echo ""

