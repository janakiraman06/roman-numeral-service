# Load Testing with k6

This directory contains load testing scripts using [k6](https://k6.io/).

## Prerequisites

Install k6:

```bash
# macOS
brew install k6

# Docker
docker run --rm -i grafana/k6 run - <script.js
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

## Success Criteria

| Metric | Target |
|--------|--------|
| p95 Response Time | < 100ms |
| Error Rate | < 1% |
| Throughput | > 1000 req/s |

## Viewing Results in Grafana

If running with the full Docker Compose stack, results can be viewed in Grafana at http://localhost:3000

