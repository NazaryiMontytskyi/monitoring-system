# Microservice Monitoring System

A self-hosted monitoring platform for Spring Boot microservices built as a diploma project.
The system collects technical metrics (response time, CPU, heap memory, JVM threads, GC pauses),
detects anomalies in real time, fires configurable alerts with email notifications, tracks SLA
compliance, and renders a live web dashboard — all without external dependencies beyond PostgreSQL.

---

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
  - [Monitoring Server](#monitoring-server-configuration)
  - [Monitored Microservice (Starter)](#monitored-microservice-starter-configuration)
- [Integrating the Starter](#integrating-the-starter)
- [REST API Overview](#rest-api-overview)
- [Web UI Pages](#web-ui-pages)
- [Alerting](#alerting)
- [Anomaly Detection](#anomaly-detection)
- [SLA Tracking](#sla-tracking)
- [PDF Reports](#pdf-reports)
- [Data Retention](#data-retention)
- [Demo Services](#demo-services)
- [Running Tests](#running-tests)
- [Known Limitations](#known-limitations)
- [API Documentation](#api-documentation)

---

> 📖 **Full API Documentation (JavaDoc):** [https://nmontytskyi.github.io/monitoring-system/javadoc/](https://nmontytskyi.github.io/monitoring-system/javadoc/)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Monitoring Server                          │
│                         (port 8080)                             │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │ REST API │  │  Web UI  │  │ Alerting │  │ Anomaly Det.  │  │
│  │ 25+ eps  │  │Thymeleaf │  │  Email   │  │   Z-score     │  │
│  └──────────┘  └──────────┘  └──────────┘  └───────────────┘  │
│                          │                                      │
│                     PostgreSQL                                  │
└─────────────────────────────────────────────────────────────────┘
         ▲  PUSH (batch)              ▲  PULL (every 30 s)
         │                            │ /actuator/health
         │                            │ /actuator/metrics
┌────────┴───────────┐    ┌───────────┴──────────┐
│  order-service     │    │  inventory-service   │  ...
│  @MonitoredMicro-  │    │  @MonitoredMicro-    │
│  service           │    │  service             │
│                    │    │                      │
│  AOP aspects       │    │  AOP aspects         │
│  Metrics buffer    │    │  Metrics buffer      │
└────────────────────┘    └──────────────────────┘
          monitoring-spring-boot-starter
```

**Two metric collection modes run simultaneously:**

- **PUSH** — AOP aspects in the starter intercept endpoint calls and flush buffered metrics to the
  server every 5 seconds (configurable). Captures precise per-request data including response time
  and HTTP status.
- **PULL** — The server polls each registered service's Spring Boot Actuator endpoints
  (`/actuator/health`, `/actuator/metrics`) every 30 seconds to collect JVM-level metrics
  (heap, threads, GC, CPU) independently of request traffic.

---

## Features

| Category | Detail |
|---|---|
| **Metric collection** | Response time, HTTP status, CPU usage (system + process), heap memory (used / max), non-heap memory, JVM live / daemon threads, GC pause time |
| **Collection modes** | PUSH via AOP starter · PULL via Actuator polling |
| **Anomaly detection** | Z-score algorithm per service, configurable threshold (default 3σ), anomaly flag and score persisted per record |
| **Alerting** | 5 metric types · 5 comparators · per-rule cooldown · email notifications |
| **SLA tracking** | Uptime %, max response time, max error rate — configurable per service, reports for DAY / WEEK / MONTH windows |
| **Live dashboard** | System-wide charts (Avg RT, Services Status, Avg CPU, Anomalies/min) · per-service detail page with 6 real-time charts |
| **Historical view** | Windowed 10-point view on dashboard cards · full 60-point horizontally-scrollable modal on click |
| **PDF reports** | Service SLA report, full service report, system-wide report — with generation history |
| **Data retention** | Scheduled cleanup with configurable retention windows for metrics, alerts, and report history |
| **Settings UI** | Email notifications, SLA thresholds, retention policy, dashboard refresh rate — managed at runtime, no restart required |
| **API documentation** | Swagger UI available at `/swagger-ui.html` |
| **Tests** | 45+ test classes including integration tests with Testcontainers + WireMock |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Build | Maven (multi-module) |
| Database | PostgreSQL 15+ |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Template engine | Thymeleaf |
| Charts | Chart.js 4.4 |
| CSS utilities | Tailwind CSS (CDN) |
| Reactive UI | Alpine.js 3 |
| Email | Spring Mail (SMTP) |
| Code generation | Lombok |
| Testing | JUnit 5, AssertJ, Testcontainers, WireMock |

---

## Project Structure

```
monitoring-system/
├── monitoring-core/                   # Pure Java — no Spring dependency
│   ├── model/                         # HealthStatus, MetricSnapshot, SlaReport, ...
│   ├── detector/                      # AnomalyDetector, PercentileCalculator
│   ├── collector/                     # MetricsCollector, MetricsReporter interfaces
│   └── annotation/                    # @MonitoredEndpoint, @TrackMetric,
│                                      # @TrackBusinessMetric, @Sla
│
├── monitoring-spring-boot-starter/    # Auto-configuration for target microservices
│   ├── aspect/                        # MonitoredEndpointAspect, AllEndpointsAspect
│   ├── buffer/                        # MetricsBuffer (async flush queue)
│   ├── client/                        # MonitoringServerClient (REST push)
│   ├── registration/                  # ServiceRegistrationBean
│   └── config/                        # MonitoringAutoConfiguration, MonitoringProperties
│
├── monitoring-server/                 # Central monitoring server
│   ├── controller/                    # 7 REST controllers + 1 MVC controller
│   ├── service/                       # AlertEvaluationService, MetricsPersistenceService,
│   │                                  # PdfReportService, SlaCalculationService,
│   │                                  # AppSettingsService, RetentionService
│   ├── alert/                         # AlertNotificationService, AlertCooldownManager
│   ├── repository/                    # 8 JPA repositories
│   ├── entity/                        # RegisteredServiceEntity, MetricRecordEntity,
│   │                                  # AlertRuleEntity, AlertEventEntity,
│   │                                  # SlaDefinitionEntity, AppSettingsEntity,
│   │                                  # ReportHistoryEntity
│   ├── scheduler/                     # MetricsPollingScheduler, ActuatorClient
│   └── resources/
│       ├── static/js/                 # system-charts.js, service-charts.js, charts.js
│       ├── templates/                 # Thymeleaf pages
│       └── db/migration/             # Flyway V1–V11 SQL migrations
│
└── demo/                              # Three pre-built demo microservices
    ├── demo-order-service/            # port 8082
    ├── demo-inventory-service/        # port 8083
    └── demo-payment-service/          # port 8084
```

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+ (default: `localhost:5433`, database `monitoring`)
- SMTP credentials — optional, required only for email alerts

### 1. Create the database

```sql
CREATE DATABASE monitoring;
```

Flyway migrations run automatically on first start and create all tables and indexes.

### 2. Build all modules

```bash
# From the repository root
mvn clean install -DskipTests
```

### 3. Start the monitoring server

```bash
cd monitoring-server
mvn spring-boot:run
```

With explicit environment variables:

```bash
DB_URL=jdbc:postgresql://localhost:5433/monitoring \
DB_USERNAME=postgres \
DB_PASSWORD=secret \
MAIL_HOST=smtp.gmail.com \
MAIL_USERNAME=you@gmail.com \
MAIL_PASSWORD=your-app-password \
mvn spring-boot:run
```

Open **http://localhost:8080** — the dashboard is ready.

### 4. Start the demo services (optional)

```bash
# In separate terminals
cd demo/demo-order-service     && mvn spring-boot:run   # port 8082
cd demo/demo-inventory-service && mvn spring-boot:run   # port 8083
cd demo/demo-payment-service   && mvn spring-boot:run   # port 8084
```

Each demo service registers itself with the monitoring server automatically on startup and begins
pushing metrics within seconds.

---

## Configuration

### Monitoring Server Configuration

```yaml
# application.yml — shown with defaults

server:
  port: 8080

spring:
  datasource:
    url:      ${DB_URL:jdbc:postgresql://localhost:5433/monitoring}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

  mail:
    host:     ${MAIL_HOST:smtp.gmail.com}
    port:     ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties.mail.smtp:
      auth:             true
      starttls.enable:  true

monitoring:
  polling:
    enabled:                 true
    interval-seconds:        30    # actuator pull interval
    timeout-seconds:         5
    connect-timeout-seconds: 3
  alert:
    enabled:                    true
    evaluation-window-minutes:  60
    notification-from: ${ALERT_FROM_EMAIL:monitoring@example.com}
    notification-to:   ${ALERT_TO_EMAIL:admin@example.com}
```

Runtime settings (email recipient, SLA thresholds, retention periods, dashboard refresh rate) are
managed through the **Settings** page and persisted in the database — no restart required.

---

### Monitored Microservice (Starter) Configuration

```yaml
# application.yml of the target microservice

monitoring:
  server-url:               http://localhost:8080   # monitoring server address
  service-name:             my-service              # logical name (auto-detected if blank)
  service-host:             localhost
  service-port:             8081
  actuator-url:             http://localhost:8081/actuator
  base-url:                 http://localhost:8081
  enabled:                  true
  track-all-endpoints:      false   # true = auto-intercept all @RestController methods
  buffer-flush-interval-ms: 5000    # push interval in milliseconds
  buffer-max-size:          100     # immediate flush when queue reaches this size
  sla-response-time-ms:     1000    # SLA: max acceptable average response time
  sla-uptime-percent:       99.9    # SLA: minimum required uptime
  sla-error-rate-percent:   5.0     # SLA: maximum acceptable error rate
```

---

## Integrating the Starter

### Step 1 — Add the Maven dependency

```xml
<dependency>
    <groupId>com.nmontytskyi</groupId>
    <artifactId>monitoring-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2 — Annotate the main application class

```java
@SpringBootApplication
@MonitoredMicroservice(
    name                  = "payment-service",
    trackAllEndpoints     = true,       // auto-monitor all REST endpoints
    bufferFlushIntervalMs = 5000
)
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

### Step 3 — (Optional) Fine-grained control

```java
// Override the display name for a specific endpoint
@GetMapping("/orders/{id}")
@MonitoredEndpoint(name = "Get Order by ID")
public Order getOrder(@PathVariable Long id) { ... }

// Manually track an arbitrary code block
@TrackMetric(name = "inventory-check")
public boolean checkStock(Long productId) { ... }
```

### What happens on startup

1. The starter registers the service with the monitoring server (name, host, port, SLA thresholds).
2. AOP aspects intercept configured endpoints and buffer metric snapshots in memory.
3. Every `bufferFlushIntervalMs` milliseconds, all buffered snapshots are sent in a single batch
   `POST /api/metrics/batch` request to the monitoring server.
4. The server independently polls `/actuator/health` and `/actuator/metrics` every 30 seconds
   to collect JVM-level metrics regardless of traffic.

---

## REST API Overview

**Base URL:** `http://localhost:8080/api`  
**Interactive docs:** `http://localhost:8080/swagger-ui.html`

### Services

| Method | Path | Description |
|---|---|---|
| `POST` | `/services` | Register a new microservice |
| `GET` | `/services` | List all registered services |
| `GET` | `/services/{id}` | Get service details and current status |
| `DELETE` | `/services/{id}` | Unregister a service |

### Metrics

| Method | Path | Description |
|---|---|---|
| `POST` | `/metrics/endpoint` | Push a single metric snapshot |
| `POST` | `/metrics/batch` | Push a batch of metric snapshots |
| `GET` | `/metrics/{serviceId}/latest` | Get the latest metric record |
| `GET` | `/metrics/{serviceId}/aggregate` | Get aggregated metrics (configurable window) |
| `GET` | `/metrics/{serviceId}/history` | Time-series history (default: last 30 min, 60 points) |
| `GET` | `/metrics/system/history` | System-wide aggregated time series |
| `GET` | `/metrics/anomalies` | Recent anomaly records (default: last 30 min, 100 records) |

### Alerts

| Method | Path | Description |
|---|---|---|
| `POST` | `/alerts/rules` | Create an alert rule |
| `GET` | `/alerts/rules` | List rules (optional `?serviceId=`) |
| `DELETE` | `/alerts/rules/{id}` | Delete a rule |
| `GET` | `/alerts/events` | Paginated alert event log |

### SLA

| Method | Path | Description |
|---|---|---|
| `GET` | `/services/{id}/sla` | Get SLA compliance report (`?window=DAY\|WEEK\|MONTH`) |
| `PUT` | `/services/{id}/sla` | Update SLA thresholds for a service |

### Reports

| Method | Path | Description |
|---|---|---|
| `GET` | `/reports/{serviceId}/sla` | Download SLA report as PDF |
| `GET` | `/reports/{serviceId}/full` | Download full service report as PDF |
| `GET` | `/reports/system` | Download system-wide report as PDF (`?from=&to=`) |
| `GET` | `/reports/{serviceId}/history` | List report generation history |

### Settings

| Method | Path | Description |
|---|---|---|
| `GET` | `/settings` | Retrieve all application settings |
| `POST` | `/settings/email` | Update the notification email address |

---

## Web UI Pages

| URL | Page | Description |
|---|---|---|
| `/` | **Dashboard** | System stat cards (total / up / degraded / down), 4 live system charts, service status table, recent alert log |
| `/services/{id}` | **Service Detail** | 6 live per-service charts: Response Time, CPU, Heap memory, Non-Heap, JVM Threads, GC Pause · anomaly banner · current status |
| `/services/{id}/sla` | **SLA Report** | Actual uptime %, avg response time, and error rate vs. configured thresholds for DAY / WEEK / MONTH |
| `/services/{id}/reports` | **Reports** | PDF generation with date-range picker · generation history (type, date, size) |
| `/alerts` | **Alerts** | Alert rules management (create, enable/disable, delete) · paginated event log |
| `/settings` | **Settings** | Email notifications · SLA thresholds per service · data retention policy · dashboard auto-refresh interval |

### Chart interaction

Every chart card is clickable. Clicking opens a full-screen modal that shows:

- **All 60 data points** — not just the 10-point window visible on the small card
- **Horizontal scrolling** for dense time-series data (28 px/point for bar charts, 20 px/point
  for line charts; the canvas is as wide as needed)
- Automatically scrolled to the **rightmost (most recent)** position when opened
- `Escape` key or backdrop click to close

The Services Status chart has its own dedicated modal (triggered by clicking the chart card)
with stacked bars showing Up / Degraded / Down counts over time, also with full horizontal scroll.

---

## Alerting

### Alert rule fields

| Field | Options | Description |
|---|---|---|
| `metricType` | `RESPONSE_TIME_AVG`, `STATUS_DOWN`, `CPU_USAGE`, `UPTIME_PERCENT`, `ERROR_RATE` | Metric to evaluate |
| `comparator` | `GT`, `LT`, `GTE`, `LTE`, `EQ` | Comparison operator |
| `threshold` | numeric | Value to compare against |
| `cooldownMinutes` | integer | Minimum gap between repeated firings of this rule |
| `enabled` | boolean | Disable without deleting |

### Notification flow

1. `MetricsPollingScheduler` collects fresh actuator data every 30 seconds.
2. `AlertEvaluationService` evaluates all enabled rules against the aggregated data for the
   configured evaluation window (default: last 60 minutes).
3. `AlertCooldownManager` suppresses re-firing within the cooldown period.
4. A new `AlertEventEntity` is written to the database.
5. If email notifications are enabled, `AlertNotificationService` dispatches an HTML email
   via the configured SMTP server.

All fired events appear in the **Alerts** page and in the **Recent Alerts** section on the dashboard.

---

## Anomaly Detection

The system applies a **Z-score algorithm** to every incoming metric record:

```
z = (current_response_time − mean) / stddev
```

| Parameter | Value |
|---|---|
| Sample window | 100 most recent records for the service |
| Minimum sample size | 10 records (detection skipped below this) |
| Default threshold | \|z\| > 3.0 (approximately 3 standard deviations) |
| Edge case (stddev = 0) | Any deviation from the constant mean is flagged |

Each `MetricRecordEntity` row stores both the `anomaly` flag and the `zScore`. Anomaly
records surface in three places:

- **Anomalies/min** bar chart on the dashboard (count of distinct anomalous services per
  30-second bucket)
- **Anomalies** list modal (LIST button) — service name, status, response time, z-score
- **Response Time** chart on the Service Detail page — anomalous points rendered as oversized
  red dots with a tooltip showing the z-score

---

## SLA Tracking

SLA thresholds are stored per service and compared against real measured values:

| Threshold | Description | Default |
|---|---|---|
| `uptimePercent` | Minimum required uptime percentage | 99.9 % |
| `maxResponseTimeMs` | Maximum acceptable average response time | 1 000 ms |
| `maxErrorRatePercent` | Maximum acceptable error rate | 5.0 % |

The SLA report page computes actual values over the selected window (DAY / WEEK / MONTH) and
displays a pass / fail status for each threshold. Thresholds can be updated at any time via
the **Settings** page or `PUT /api/services/{id}/sla`.

---

## PDF Reports

| Report | Endpoint | Contents |
|---|---|---|
| **SLA Report** | `GET /api/reports/{id}/sla` | SLA compliance summary for a service |
| **Full Service Report** | `GET /api/reports/{id}/full` | Complete metrics history, anomaly log, and SLA for a service |
| **System Report** | `GET /api/reports/system?from=&to=` | All services aggregated over a chosen date range |

Generation history (timestamp, report type, file size) is persisted and accessible from the
**Reports** page per service.

---

## Data Retention

Automatic scheduled cleanup prevents unbounded database growth.

| Setting | Default | Description |
|---|---|---|
| `retention.enabled` | `true` | Master on/off switch |
| `retention.metric_records.days` | 30 | Days to keep metric records |
| `retention.alert_events.days` | 90 | Days to keep alert events |
| `retention.report_history.days` | 180 | Days to keep report history entries |
| `retention.frequency` | `daily` | `daily` or `weekly` |
| `retention.time` | `03:00` | Time of day the cleanup job runs |

All settings are configurable at runtime through the **Settings** page.

---

## Demo Services

Three demo microservices illustrate end-to-end integration with the starter:

| Service | Port | Sample endpoints |
|---|---|---|
| `demo-order-service` | 8082 | `GET /orders`, `POST /orders`, `GET /orders/{id}` |
| `demo-inventory-service` | 8083 | `GET /inventory`, `GET /inventory/{productId}` |
| `demo-payment-service` | 8084 | `POST /payments`, `GET /payments/{id}` |

Each service uses `@MonitoredMicroservice(trackAllEndpoints = true)` and connects to the
monitoring server via the `monitoring.server-url` property. On startup they self-register and
begin pushing metrics within seconds; the server starts pulling Actuator data within the
first 30-second polling cycle.

---

## Running Tests

```bash
# Run all tests across all modules
mvn test

# Run tests for a single module
cd monitoring-server && mvn test

# Run integration tests (requires Docker for Testcontainers)
mvn verify
```

### Test infrastructure

| Type | Tools | Coverage |
|---|---|---|
| Unit tests | JUnit 5, AssertJ | Anomaly detector, percentile calculator, alert evaluation, SLA calculation, metric persistence, cooldown manager |
| Integration tests | Testcontainers (PostgreSQL), WireMock | Full metric ingestion pipeline, polling scheduler, alert firing, PDF generation, MVC controllers |
| Starter tests | Spring Boot Test, WireMock | AOP aspects, metrics buffer, service registration, server client |

---

## Known Limitations

| Area | Limitation |
|---|---|
| **Security** | No authentication or authorization. All endpoints and UI pages are publicly accessible. Intended for deployment in trusted internal networks only. |
| **Alert channels** | Email only. No webhook, Slack, PagerDuty, or other integration. |
| **Maintenance windows** | No built-in alert silencing. Rules must be manually disabled during planned downtime. |
| **Service organization** | Flat service list — no grouping by team, namespace, or domain. |
| **Cross-service analysis** | Each service is shown independently. No dependency map or correlated-failure view. |

---

## API Documentation

Full JavaDoc documentation for all modules is published via GitHub Pages:

**[https://nmontytskyi.github.io/monitoring-system/javadoc/](https://nmontytskyi.github.io/monitoring-system/javadoc/)**

The documentation covers all public classes across three modules:

| Module | Description |
|---|---|
| `monitoring-core` | Shared model classes, metric collection interfaces, aggregation logic |
| `monitoring-spring-boot-starter` | AOP aspects, autoconfiguration, registration beans |
| `monitoring-server` | REST controllers, services, repositories, entities, DTOs, alert pipeline |

To regenerate the documentation locally:

```bash
mvn javadoc:aggregate
# Output: docs/javadoc/index.html
```

To enable GitHub Pages, push the `docs/javadoc/` directory and configure GitHub Pages to serve from the `docs` folder on your main branch (Settings → Pages → Source: `main` / `docs`).

---

## License

Academic project — Diploma thesis, 2025.  
Author: Nazar Montytskyi
