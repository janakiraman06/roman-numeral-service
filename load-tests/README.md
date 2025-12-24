# Load Testing with k6

This directory contains load testing scripts using [k6](https://k6.io/).

## Prerequisites

Install k6 (choose one):

```bash
# Option 1: Install locally on macOS (recommended)
brew install k6

# Option 2: Use Docker (no installation needed)
# See "Running with Docker" section below
```

## Test Scripts

| Script | Purpose | VUs | Duration |
|--------|---------|-----|----------|
| `smoke-test.js` | Basic verification | 1 | 30s |
| `load-test.js` | Normal traffic | 50 | 5min |
| `stress-test.js` | Find breaking point | 200 | 5min |
| `spike-test.js` | Sudden traffic surge | 100 | 2min |

## Running Tests

### Start the application first:

```bash
# Using Docker Compose
docker-compose up -d roman-numeral-service

# Or using Maven
./mvnw spring-boot:run
```

### Run tests:

```bash
cd load-tests/scripts

# Smoke test (quick verification)
k6 run smoke-test.js

# Load test (normal traffic)
k6 run load-test.js

# Stress test (find limits)
k6 run stress-test.js

# Spike test (sudden surge)
k6 run spike-test.js
```

### Custom base URL:

```bash
k6 run -e BASE_URL=http://myserver:8080 load-test.js
```

### Running with Docker (no k6 installation required):

```bash
# From project root directory
cd /path/to/roman-numeral-service

# Smoke test
docker run --rm -v $(pwd)/load-tests/scripts:/scripts --network="host" grafana/k6 run /scripts/smoke-test.js

# Load test
docker run --rm -v $(pwd)/load-tests/scripts:/scripts --network="host" grafana/k6 run /scripts/load-test.js

# Stress test
docker run --rm -v $(pwd)/load-tests/scripts:/scripts --network="host" grafana/k6 run /scripts/stress-test.js
```

> **Note**: `--network="host"` allows k6 in Docker to access localhost:8080

## Success Criteria

| Metric | Target |
|--------|--------|
| p95 Response Time | < 100ms |
| Error Rate | < 1% |
| Throughput | > 1000 req/s |

## Viewing Results in Grafana

If running with the full Docker Compose stack, results can be viewed in Grafana at http://localhost:3000

