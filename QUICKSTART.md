# Quick Start Cheatsheet

## 🚀 Running Locally

### Option 1: API Only (No Docker)

```bash
# Build and run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Test
curl "http://localhost:8080/romannumeral?query=42"
```

### Option 2: Core Services (API + Database + Observability)

```bash
# Start core services
docker-compose up -d postgres kafka roman-numeral-service grafana prometheus loki promtail

# Wait for services to be healthy (about 30 seconds)
docker-compose ps

# Services available:
# - API:        http://localhost:8080
# - Swagger:    http://localhost:8080/swagger-ui/index.html
# - Grafana:    http://localhost:3000 (admin/admin)
# - Prometheus: http://localhost:9090
# - Actuator:   http://localhost:8081/actuator
```

### Option 3: Full Data Platform (All 21 Services)

```bash
# Start everything (first time takes 5-10 minutes for image pulls)
docker-compose up -d

# Wait for all services to be healthy
docker-compose ps

# Core Services:
# - API:        http://localhost:8080
# - Swagger:    http://localhost:8080/swagger-ui/index.html
# - Grafana:    http://localhost:3000 (admin/admin)
# - Prometheus: http://localhost:9090

# Data Platform Services:
# - Airflow:    http://localhost:8093 (airflow/airflow)
# - Superset:   http://localhost:8088 (admin/admin)  
# - Spark UI:   http://localhost:8090
# - Flink UI:   http://localhost:8092
# - MinIO:      http://localhost:9001 (minioadmin/minioadmin123)
# - Marquez:    http://localhost:5050 (API), http://localhost:3001 (Web)
# - Jupyter:    http://localhost:8888 (token: jupyter)
```

---

## 📋 Testing Cheatsheet

### 1. Basic API Tests

```bash
# Single conversion
curl "http://localhost:8080/romannumeral?query=42"
# {"input":"42","output":"XLII"}

# Range conversion (small - no pagination)
curl "http://localhost:8080/romannumeral?min=1&max=10"

# Range conversion (large - with pagination)
curl "http://localhost:8080/romannumeral?min=1&max=1000&offset=0&limit=50"

# Invalid input
curl "http://localhost:8080/romannumeral?query=5000"
# Error: Number must be between 1 and 3999

# Health check
curl http://localhost:8081/actuator/health
```

### 2. API with Authentication (prod profile)

```bash
# Start with prod profile
docker-compose down
SPRING_PROFILES_ACTIVE=prod docker-compose up -d

# Without API key (401)
curl "http://localhost:8080/romannumeral?query=42"
# Error: API Key is missing.

# With API key (header)
curl -H "X-API-Key: rns_demo1234_testkeyforlocaldev" \
     "http://localhost:8080/romannumeral?query=42"

# With Bearer token
curl -H "Authorization: Bearer rns_demo1234_testkeyforlocaldev" \
     "http://localhost:8080/romannumeral?query=42"

# With query param
curl "http://localhost:8080/romannumeral?query=42&apiKey=rns_demo1234_testkeyforlocaldev"
```

### 3. Bulk Testing

```bash
# Generate load (10 requests)
for i in {1..10}; do
  curl -s "http://localhost:8080/romannumeral?query=$i" &
done
wait

# Full range test
curl "http://localhost:8080/romannumeral?min=1&max=3999&limit=100" | jq '.pagination'
```

---

## 🔧 Common Commands

### Docker

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f roman-numeral-service

# View all logs
docker-compose logs -f

# Stop all
docker-compose down

# Clean restart (removes volumes)
docker-compose down -v && docker-compose up -d

# Check service health
docker-compose ps

# Restart single service
docker-compose restart roman-numeral-service
```

### Maven

```bash
# Run tests
./mvnw test

# Run with coverage
./mvnw clean verify

# Build JAR
./mvnw clean package -DskipTests

# Run JAR directly
java -jar target/roman-numeral-service-1.0.0.jar --spring.profiles.active=dev
```

### Database

```bash
# Connect to PostgreSQL
docker exec -it postgres psql -U romannumeral -d romannumeral

# SQL queries
SELECT * FROM app_user;
SELECT * FROM api_key;
SELECT * FROM conversion_request ORDER BY request_timestamp DESC LIMIT 10;
```

---

## 📊 Observability

### Grafana

```bash
# Open dashboard directly
open http://localhost:3000/d/roman-numeral-service

# Login: admin / admin
```

### Prometheus

```bash
# Check targets
open http://localhost:9090/targets

# Query metrics
open http://localhost:9090/graph

# Example queries:
# - http_server_requests_seconds_count
# - api_key_validation_total
# - kafka_message_sent_total
```

### Logs

```bash
# Application logs
docker-compose logs -f roman-numeral-service

# All logs with timestamps
docker-compose logs -f --tail=100 -t
```

---

## 🗄️ Data Platform

### Kafka

```bash
# List topics
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Create topic manually (if needed)
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic roman-numeral-events --partitions 3 --replication-factor 1

# Consume messages (view live)
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic roman-numeral-events \
  --from-beginning

# Consume N messages and exit
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic roman-numeral-events \
  --from-beginning \
  --max-messages 5

# Check message count
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic roman-numeral-events \
  --from-beginning \
  --timeout-ms 3000 2>/dev/null | wc -l
```

### MinIO (S3-Compatible Storage)

```bash
# Open MinIO console
open http://localhost:9001
# Login: minioadmin / minioadmin123

# The 'lakehouse' bucket is created automatically for Iceberg tables
```

### Airflow

```bash
# Open Airflow UI
open http://localhost:8093
# Login: airflow / airflow

# Trigger DAG manually
docker exec airflow airflow dags trigger rns_silver_etl

# List DAGs
docker exec airflow airflow dags list
```

### Spark

```bash
# Open Spark Master UI
open http://localhost:8090

# Open Spark worker logs
open http://localhost:8091

# Submit PySpark job
docker exec spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  /opt/spark-jobs/bronze_kafka_ingestion.py
```

### Flink

```bash
# Open Flink Dashboard
open http://localhost:8092

# Check running jobs
curl -s http://localhost:8092/jobs | jq .
```

### Marquez (Data Lineage)

```bash
# Open Marquez Web UI
open http://localhost:3001

# Check Marquez API
curl -s http://localhost:5050/api/v1/namespaces | jq .
```

### Jupyter

```bash
# Open Jupyter Lab
open http://localhost:8888
# Token: jupyter (or check docker-compose logs jupyter for the URL)
```

---

## 🧪 End-to-End Test Flow

```bash
# 1. Start everything
docker-compose up -d

# 2. Wait for services (check health)
sleep 30 && docker-compose ps

# 3. Generate some conversions
for i in {1..100}; do
  curl -s "http://localhost:8080/romannumeral?query=$((RANDOM % 3999 + 1))" > /dev/null
done
echo "Generated 100 conversion requests"

# 4. Check Kafka messages
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic roman-numeral-events \
  --from-beginning \
  --max-messages 5

# 5. Check Grafana dashboards
open http://localhost:3000/d/roman-numeral-service

# 6. Check database
docker exec -it postgres psql -U romannumeral -d romannumeral \
  -c "SELECT COUNT(*) FROM conversion_request;"

# 7. Open Jupyter for analysis
open http://localhost:8888
```

---

## 🛑 Troubleshooting

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>
```

### Service Not Starting

```bash
# Check logs
docker-compose logs <service-name>

# Restart service
docker-compose restart <service-name>

# Full rebuild
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

### Database Connection Issues

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Test connection
docker exec -it postgres pg_isready -U romannumeral
```

### Kafka Connection Issues

```bash
# Check Kafka is running
docker-compose ps kafka

# Check broker is ready
docker exec -it kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

---

## 📁 Quick Reference: Service Ports

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| **Core Services** ||||
| API | 8080 | http://localhost:8080 | - |
| Actuator | 8081 | http://localhost:8081/actuator | - |
| Swagger | 8080 | http://localhost:8080/swagger-ui/index.html | - |
| PostgreSQL | 5432 | - | romannumeral / romannumeral_secret |
| Kafka | 9092/9094 | - | - |
| **Observability** ||||
| Grafana | 3000 | http://localhost:3000 | admin / admin |
| Prometheus | 9090 | http://localhost:9090 | - |
| Loki | 3100 | - | - |
| **Data Platform** ||||
| Airflow | 8093 | http://localhost:8093 | airflow / airflow |
| Superset | 8088 | http://localhost:8088 | admin / admin |
| Spark Master | 8090 | http://localhost:8090 | - |
| Spark Worker | 8091 | http://localhost:8091 | - |
| Flink | 8092 | http://localhost:8092 | - |
| MinIO | 9001 | http://localhost:9001 | minioadmin / minioadmin123 |
| Marquez API | 5050 | http://localhost:5050 | - |
| Marquez Web | 3001 | http://localhost:3001 | - |
| Jupyter | 8888 | http://localhost:8888 | token: jupyter |

---

## ✅ Verified Working (as of Dec 2024)

The following end-to-end flow has been tested:

```
API Request → Spring Boot → Kafka (roman-numeral-events) → Verified ✅
```

All 21 services start successfully with `docker-compose up -d`.

### Sample Kafka Event

```json
{
  "eventId": "7ad50b2a-f297-41b0-95e2-90395a0264b7",
  "eventTime": "2025-12-26T01:42:50.883Z",
  "eventType": "SINGLE",
  "inputNumber": 42,
  "outputRoman": "XLII",
  "responseTimeNanos": 48292,
  "status": "SUCCESS",
  "clientIp": "172.18.0.1",
  "correlationId": "af31a529"
}

