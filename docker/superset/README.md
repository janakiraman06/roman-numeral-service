# Apache Superset - BI Dashboards

## Overview

Apache Superset provides business intelligence dashboards for the Roman Numeral Service analytics.

## Access

| Component | URL | Credentials |
|-----------|-----|-------------|
| Superset Web UI | http://localhost:8088 | admin / admin |
| SQL Lab | http://localhost:8088/sqllab | Same as above |

## Quick Start

1. Start the services:
   ```bash
   docker-compose up -d superset superset-db superset-redis
   ```

2. Wait for initialization (first startup takes 1-2 minutes):
   ```bash
   docker-compose logs -f superset
   ```

3. Access the UI at http://localhost:8088

## Adding Data Sources

### 1. PostgreSQL (OLTP Data)

Connect to the application database for real-time operational data:

1. Go to **Settings** → **Database Connections** → **+ Database**
2. Select **PostgreSQL**
3. Enter connection details:
   - Host: `postgres`
   - Port: `5432`
   - Database: `romannumeral`
   - Username: `romannumeral`
   - Password: `romannumeral_secret`
4. Click **Test Connection** → **Connect**

**SQLAlchemy URI:**
```
postgresql+psycopg2://romannumeral:romannumeral_secret@postgres:5432/romannumeral
```

### 2. Iceberg/Lakehouse Data (via Trino - Optional)

For querying Gold layer Iceberg tables, add Trino:

1. First, add Trino to docker-compose (not included by default)
2. Install the Trino driver: `pip install trino`
3. Add database connection:
   ```
   trino://trino:8080/iceberg/gold
   ```

## Recommended Dashboards

### Dashboard 1: Conversion Analytics

**Purpose:** Monitor API usage and conversion patterns

**Charts:**
- Daily conversion volume (time series)
- Conversion types breakdown (pie chart)
- Top 10 most converted numbers (bar chart)
- Error rate trend (line chart)

**Sample SQL:**
```sql
-- Daily conversion volume
SELECT 
    DATE(request_timestamp) as date,
    COUNT(*) as conversions
FROM conversion_request
GROUP BY DATE(request_timestamp)
ORDER BY date DESC
LIMIT 30;
```

### Dashboard 2: User Activity

**Purpose:** Understand user behavior

**Charts:**
- Active users per day
- Requests per user distribution
- User activity heatmap
- New vs returning users

**Sample SQL:**
```sql
-- Requests per user
SELECT 
    user_id,
    COUNT(*) as request_count
FROM conversion_request
WHERE user_id IS NOT NULL
GROUP BY user_id
ORDER BY request_count DESC
LIMIT 20;
```

### Dashboard 3: API Health

**Purpose:** Operational monitoring

**Charts:**
- Request latency percentiles
- Error rate by endpoint
- Requests per minute
- Geographic distribution (by client_ip)

## Creating Charts

### Step 1: Create a Dataset

1. Go to **Data** → **Datasets** → **+ Dataset**
2. Select your database connection
3. Choose the table/view
4. Click **Create Dataset and Create Chart**

### Step 2: Build a Chart

1. Select chart type (e.g., Time-series Line Chart)
2. Configure:
   - Time column
   - Metrics (COUNT, SUM, AVG, etc.)
   - Dimensions (GROUP BY)
3. Click **Update Chart**
4. Save the chart

### Step 3: Build a Dashboard

1. Go to **Dashboards** → **+ Dashboard**
2. Name your dashboard
3. Drag charts from the left panel
4. Arrange and resize as needed
5. Save

## SQL Lab

SQL Lab allows ad-hoc querying:

1. Go to **SQL Lab** → **SQL Editor**
2. Select your database
3. Write and execute queries
4. Save queries for reuse
5. Export results to charts

## Configuration

The main configuration file is `superset_config.py`:

| Setting | Description |
|---------|-------------|
| `SECRET_KEY` | Flask secret key (change in production) |
| `SQLALCHEMY_DATABASE_URI` | Superset metadata database |
| `CACHE_CONFIG` | Redis cache settings |
| `FEATURE_FLAGS` | Enable/disable features |

## Troubleshooting

### Container won't start

```bash
# Check logs
docker-compose logs superset

# Reset and reinitialize
docker-compose down -v superset superset-db
docker-compose up -d superset
```

### Can't connect to database

1. Verify the database container is running
2. Check network connectivity:
   ```bash
   docker exec superset ping postgres
   ```
3. Verify credentials in the connection string

### Dashboard loading slowly

1. Check Redis cache is running
2. Increase `CACHE_DEFAULT_TIMEOUT`
3. Add indexes to frequently queried columns

## Production Considerations

1. **Change SECRET_KEY**: Generate a secure random key
2. **Enable HTTPS**: Set `SESSION_COOKIE_SECURE = True`
3. **Add Celery**: For async queries and scheduled reports
4. **Configure LDAP/OAuth**: For enterprise authentication
5. **Set up alerts**: Enable `ALERT_REPORTS` feature flag

