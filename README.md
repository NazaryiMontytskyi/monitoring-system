# Monitoring System

> Diploma project — "Software for monitoring the state of microservices based on the Spring Framework"

A self-contained monitoring stack for Spring Boot microservices. Provides a reusable
Spring Boot Starter that embeds into any target service with a single annotation, a central
server that collects, stores and visualises metrics, and a demo suite that illustrates
end-to-end usage.

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Solution Overview](#solution-overview)
3. [Functional Requirements](#functional-requirements)
4. [Architecture](#architecture)
5. [Module Structure](#module-structure)
6. [How It Works — Data Flow](#how-it-works--data-flow)
7. [Key Concepts](#key-concepts)
   - [Annotations](#annotations)
   - [Metrics Collection — Two Parallel Streams](#metrics-collection--two-parallel-streams)
   - [Anomaly Detection (Z-score)](#anomaly-detection-z-score)
   - [Percentiles P50 / P95 / P99](#percentiles-p50--p95--p99)
   - [SLA Tracking](#sla-tracking)
   - [Business Metrics](#business-metrics)
   - [Alerting](#alerting)
8. [Web Interface](#web-interface)
9. [REST API](#rest-api)
10. [Tech Stack](#tech-stack)
11. [Implementation Phases](#implementation-phases)
12. [Getting Started](#getting-started)

---

## Problem Statement

In a microservice architecture, individual services fail, slow down, or degrade silently.
Without centralised observability a team has no visibility into which service is the source
of a problem, whether SLA commitments are being met, or which business operations are
affected. Existing solutions (Prometheus + Grafana) require significant infrastructure
setup. This project provides a lightweight, Spring-native alternative that can be adopted
with a single dependency and one annotation.

---

## Solution Overview

The system consists of two reusable artefacts and a demo suite:

| Artefact | Role |
|---|---|
| `monitoring-spring-boot-starter` | JAR library. Add as a dependency — the service auto-registers and starts reporting metrics. |
| `monitoring-server` | Runnable JAR / Docker image. Deploy once in the infrastructure. Collects, stores, visualises, and alerts. |
| `demo/*` | Minimal Spring Boot apps that show how to integrate the starter. Not part of the product. |

A developer in a different project needs to do exactly two things:

**Step 1** — add the starter dependency:
```xml
<dependency>
    <groupId>com.nmontytskyi</groupId>
    <artifactId>monitoring-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 2** — annotate the main class and point to the server:
```java
@MonitoredMicroservice(
    name = "order-service",
    serverUrl = "http://monitoring.company.com:8080",
    sla = @Sla(uptimePercent = 99.9, maxResponseTimeMs = 300)
)
@SpringBootApplication
public class OrderServiceApplication { ... }
```

That is all. The service registers itself, the dashboard shows it, alerts fire automatically.

---

## Functional Requirements

| # | Requirement |
|---|---|
| FR-1 | Collect and aggregate technical metrics from microservices: availability, response time, CPU, heap memory. |
| FR-2 | Provide a REST API for receiving and querying monitoring data, usable by external systems. |
| FR-3 | Implement a visualisation subsystem — a web dashboard with real-time charts. |
| FR-4 | Implement an alerting mechanism — notify via email when thresholds are exceeded or a service goes down. |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        monitoring-server                              │
│                                                                       │
│  ┌───────────────┐  ┌─────────────────┐  ┌────────────────────────┐ │
│  │   REST API    │  │   Scheduler     │  │    Alert Engine        │ │
│  │ (Controllers) │  │ pull /actuator  │  │ evaluates rules →      │ │
│  │ Swagger UI    │  │ every 30s       │  │ sends email via        │ │
│  └──────┬────────┘  └────────┬────────┘  │ JavaMailSender         │ │
│         │                   │            └────────────────────────┘ │
│  ┌──────▼───────────────────▼──────────────────────────────────────┐ │
│  │                      PostgreSQL                                  │ │
│  │  registered_services │ metric_records │ alert_rules │ alert_events│ │
│  └─────────────────────────────────────────────────────────────────┘ │
│         │                                                             │
│  ┌──────▼──────────────────────────────────────────────────────────┐ │
│  │     Web Interface  (Thymeleaf + Bootstrap 5 + Chart.js)         │ │
│  │  Dashboard │ Service Detail │ SLA Report │ Alert Rules │ Events  │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTP
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                  ▼
   ┌─────────────────┐  ┌────────────┐  ┌────────────────┐
   │monitoring-spring│  │  order-    │  │  inventory-    │
   │ -boot-starter   │  │  service   │  │  service       │
   │                 │  │            │  │                │
   │ embedded in     │  │ /actuator  │  │ /actuator      │
   │ every service   │  │ @Monitored │  │ @Monitored     │
   │ auto-registers  │  │ Microserv. │  │ Microserv.     │
   └─────────────────┘  └────────────┘  └────────────────┘
```

**Pull stream** — `monitoring-server` scheduler calls `/actuator/health` and
`/actuator/metrics` on every registered service every 30 seconds.

**Push stream** — the AOP aspect inside the starter intercepts every annotated method call,
measures duration and status, and sends a `MetricSnapshot` to the server asynchronously.

---

## Module Structure

```
monitoring-system/                         ← root Maven POM
│
├── monitoring-core/                       ← Phase 1 ✅
│   Pure Java, no Spring dependencies.
│   Shared vocabulary used by all other modules.
│   │
│   ├── annotation/
│   │   ├── @MonitoredEndpoint             selective endpoint monitoring
│   │   ├── @TrackMetric                   execution time of any method
│   │   ├── @TrackBusinessMetric           business event counting
│   │   └── @Sla                           SLA threshold definition
│   │
│   ├── model/
│   │   ├── HealthStatus (enum)            UP / DEGRADED / DOWN / UNKNOWN
│   │   ├── ServiceInfo                    registered service descriptor
│   │   ├── MetricSnapshot                 single point-in-time measurement
│   │   ├── SlaDefinition                  SLA parameters + factory methods
│   │   ├── SlaReport                      compliance report with percentiles
│   │   └── BusinessMetric                 named business event
│   │
│   ├── collector/
│   │   ├── MetricsCollector (interface)   contract: collect(ServiceInfo)
│   │   └── MetricsReporter (interface)    contract: report / reportBatch / reportBusiness
│   │
│   └── detector/
│       ├── AnomalyDetector                Z-score, configurable threshold (default 3σ)
│       └── PercentileCalculator           P50 / P95 / P99, Nearest Rank Method
│
├── monitoring-spring-boot-starter/        ← Phase 5 (planned)
│   Spring Boot auto-configuration library.
│   Embedded into each monitored microservice.
│   │
│   ├── @MonitoredMicroservice             meta-annotation — activates everything
│   ├── MonitoringAutoConfiguration        reads annotation attributes via ImportAware
│   ├── MonitoringAspect (AOP)             intercepts @RestController / @MonitoredEndpoint
│   ├── ServiceRegistrationBean            POST /api/services on application startup
│   ├── HttpMetricsReporter                implements MetricsReporter via HTTP POST
│   ├── MetricsBuffer                      buffers snapshots, sends in batches
│   └── MonitoringProperties               spring config: server-url, service-name, enabled
│
├── monitoring-server/                     ← Phases 2-4, 6-7 (planned)
│   Central Spring Boot application.
│   Deployed once, serves the entire infrastructure.
│   │
│   ├── entity/                            JPA entities (PostgreSQL)
│   ├── repository/                        Spring Data JPA repositories
│   ├── service/                           business logic layer
│   ├── controller/                        REST API + MVC web pages
│   ├── scheduler/                         pull-based health check scheduler
│   ├── alert/                             threshold evaluation + email dispatch
│   └── resources/
│       ├── application.yml
│       ├── templates/                     Thymeleaf HTML templates
│       └── static/                        Bootstrap 5, Chart.js
│
└── demo/                                  ← Phase 8 (planned)
    Standalone Maven project, NOT part of the product.
    Shows how to integrate the starter.
    │
    ├── demo-order-service
    └── demo-inventory-service
        Each has: @MonitoredMicroservice, /actuator, simulation endpoints
        (/simulate/slow, /simulate/error, /simulate/normal)
```

---

## How It Works — Data Flow

### Service registration (once at startup)
```
@MonitoredMicroservice detected by Spring
        │
        ▼
MonitoringAutoConfiguration reads: name, serverUrl, sla, trackAllEndpoints
        │
        ▼
ServiceRegistrationBean.run()  →  POST /api/services  →  monitoring-server
        │                         { name, host, port, actuatorUrl, sla }
        ▼
RegisteredServiceEntity saved to PostgreSQL
Dashboard shows the new service with status UNKNOWN
```

### Push metrics (every HTTP request to an annotated endpoint)
```
Client  →  GET /orders  →  MonitoringAspect intercepts
                                  │
                          measures: start time
                                  │
                          calls:  pjp.proceed()  →  OrderController.getOrders()
                                  │
                          measures: end time, catches exceptions
                                  │
                          builds: MetricSnapshot { endpoint, responseTimeMs, status }
                                  │
                          MetricsBuffer.add(snapshot)
                                  │  (every 5 seconds, batch send)
                                  ▼
                          POST /api/metrics/endpoint  →  monitoring-server
                                  │
                          MetricRecordEntity saved to PostgreSQL
                          AnomalyDetector evaluates against history
                          AlertEvaluationService checks all AlertRules
```

### Pull metrics (every 30 seconds, server-side)
```
@Scheduled HealthCheckScheduler
        │
        ▼
For each RegisteredServiceEntity:
        │
        ├── GET {actuatorUrl}/health   →  { status: "UP", components: {...} }
        ├── GET {actuatorUrl}/metrics/jvm.memory.used
        └── GET {actuatorUrl}/metrics/system.cpu.usage
        │
        ▼
ActuatorMetricsCollector builds MetricSnapshot
        │
        ▼
MetricsPersistenceService saves MetricRecordEntity
AnomalyDetector evaluates { responseTimeMs } against last 100 records
AlertEvaluationService checks all AlertRules for this service
```

### Alert flow
```
After every MetricRecord save:
        │
        ▼
AlertEvaluationService checks rules:
  response_time_avg > threshold_ms  ?
  status == DOWN                    ?
  cpu_usage > threshold_%           ?
  uptime_percent < threshold_%      ?
        │
        ▼ (rule violated)
AlertCooldownManager: has 15 min passed since last alert for this rule?
        │
        ▼ (yes)
AlertNotificationService sends HTML email via JavaMailSender:
  Subject: [ALERT] order-service — Response time exceeded threshold
  Body:    service name, status, metric value, threshold, timestamp, dashboard link
        │
        ▼
AlertEventEntity saved to PostgreSQL → visible in event log UI
```

---

## Key Concepts

### Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@MonitoredMicroservice` | Main class | Activates the entire monitoring stack for this service |
| `@MonitoredEndpoint` | Method | Marks a specific REST method for individual monitoring |
| `@TrackMetric` | Method | Records execution time of any Spring component method |
| `@TrackBusinessMetric` | Method | Records a business event counter on successful completion |
| `@Sla` | Attribute of `@MonitoredMicroservice` | Defines numeric SLA thresholds for the service |

### Metrics Collection — Two Parallel Streams

| | Push (starter → server) | Pull (server → Actuator) |
|---|---|---|
| **Initiated by** | Starter AOP aspect after each request | Server scheduler every 30s |
| **Granularity** | Per-endpoint (GET /orders, POST /payments) | Per-service (overall health) |
| **Data** | Response time, status, error message | CPU, heap, disk, uptime |
| **Dependency** | Only our starter | Requires Spring Boot Actuator |

### Anomaly Detection (Z-score)

Instead of hard thresholds (`response_time > 1000ms`), the system learns each service's
own normal behaviour and detects deviations from it:

```
Historical data (last 100 measurements): mean = 120ms, stdDev = 15ms
Current value: 280ms
Z = (280 - 120) / 15 = 10.67  →  |Z| > 3.0  →  ANOMALY
```

- Service A with baseline 800ms → anomaly above ~1200ms
- Service B with baseline 50ms  → anomaly above ~130ms

Requires at least 10 historical samples. Returns `insufficient()` otherwise.
Default threshold: 3σ (configurable). Implemented in `AnomalyDetector`.

### Percentiles P50 / P95 / P99

Average response time hides problems. Percentiles reveal tail latency:

```
1000 requests per minute:
P50 =   95ms  ← half of requests are faster
P95 =  340ms  ← 95% of requests are faster
P99 = 1200ms  ← 1% of requests are slower than this
```

Average = 110ms looks fine. But P99 = 1200ms means every 100th user waits 1.2 seconds.
Implemented in `PercentileCalculator` using the Nearest Rank Method.

### SLA Tracking

Each service defines an SLA contract. The system automatically evaluates compliance
over time windows (hour, day, week, month):

```
order-service — SLA report for April 2026:
  Uptime:         99.7%   ✗  (target: 99.9%)  ← BREACH
  Response time:  187ms   ✓  (target: < 300ms)
  Error rate:     0.3%    ✓  (target: < 1%)
  Compliance:     66.7%
```

Defined via `@Sla`, stored as `SlaDefinition`, reported as `SlaReport`.

### Business Metrics

The starter can track domain-level events alongside technical metrics:

```java
@TrackBusinessMetric(name = "orders.created", unit = "count")
public Order createOrder(OrderRequest request) { ... }
```

Dashboard shows business activity correlated with technical performance:
when CPU spikes to 90%, the order creation count drops simultaneously.

### Alerting

Alert rules are configurable per service:

| Trigger | Example |
|---|---|
| Response time exceeded | `avg response time > 1000ms` |
| Service unavailable | `status == DOWN` |
| CPU overload | `cpu_usage > 80%` |
| Uptime below threshold | `uptime_percent < 99.0` |

Cooldown period (default 15 min) prevents alert spam.
Notifications are sent as HTML emails via `JavaMailSender`.
All fired alerts are persisted as `AlertEvent` and visible in the UI.

---

## Web Interface

Built with Thymeleaf + Bootstrap 5 + Chart.js. No separate frontend build required.
Charts refresh automatically every 15 seconds via `fetch()` + Chart.js.

| Page | URL | Contents |
|---|---|---|
| Dashboard | `GET /` | Cards for all services: status badge, avg response time, uptime %, CPU |
| Service detail | `GET /services/{id}` | Response time line chart (P50/P95/P99), error rate bar chart, endpoint breakdown table |
| SLA report | `GET /services/{id}/sla` | Compliance % over time windows, breach history |
| Alert rules | `GET /alerts/rules` | Rule list, create/delete form |
| Event log | `GET /alerts/events` | Paginated table of all fired alerts |

---

## REST API

Documented via Swagger UI at `/swagger-ui.html`.

```
/api/services
  POST    register a service
  GET     list all services
  DELETE  /{id}

/api/metrics/{serviceId}
  GET     full metric history
  GET     /latest
  GET     /aggregate?from=&to=   (avg, min, max, uptime %)

/api/metrics/endpoint
  POST    receive endpoint snapshot from starter (push stream)

/api/alerts/rules
  GET     list rules
  POST    create rule
  DELETE  /{id}

/api/alerts/events
  GET     ?serviceId=&from=&to=

/api/dashboard
  GET     summary state of all services (used by UI auto-refresh)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Build | Maven (multi-module) |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Email | Spring Boot Mail (JavaMailSender) |
| Web UI | Thymeleaf + Bootstrap 5 + Chart.js |
| API docs | SpringDoc OpenAPI (Swagger UI) |
| AOP | Spring AOP (AspectJ weaving) |
| Utilities | Lombok |
| Tests | JUnit 5 + AssertJ |

---

## Implementation Phases

| Phase | Scope | Status |
|---|---|---|
| 1 | `monitoring-core` — domain model, annotations, analysis utilities, 74 unit tests | ✅ Done |
| 2 | `monitoring-server` — JPA entities, repositories, `application.yml`, Flyway | ⬜ Next |
| 3 | `monitoring-server` — REST API controllers, DTOs, Swagger | ⬜ Planned |
| 4 | `monitoring-server` — metrics collection: Actuator client, scheduler, aggregation | ⬜ Planned |
| 5 | `monitoring-spring-boot-starter` — `@MonitoredMicroservice`, AOP aspect, HTTP reporter | ⬜ Planned |
| 6 | `monitoring-server` — alert evaluation, email dispatch, cooldown, event log | ⬜ Planned |
| 7 | `monitoring-server` — Thymeleaf web UI, Chart.js dashboard, SLA report page | ⬜ Planned |
| 8 | `demo/*` — two minimal Spring Boot apps with simulation endpoints | ⬜ Planned |

---

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL 15+ (or Docker)

### Run monitoring-server
```bash
# Start PostgreSQL
docker run -d --name monitoring-pg \
  -e POSTGRES_DB=monitoring \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:15

# Build and run
mvn clean install -pl monitoring-core
mvn spring-boot:run -pl monitoring-server
```

Dashboard: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Add to your microservice
```xml
<dependency>
    <groupId>com.nmontytskyi</groupId>
    <artifactId>monitoring-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
@MonitoredMicroservice(
    name = "your-service-name",
    serverUrl = "http://localhost:8080",
    sla = @Sla(uptimePercent = 99.9, maxResponseTimeMs = 500)
)
@SpringBootApplication
public class YourApplication { ... }
```

```yaml
# application.yml — optional overrides
monitoring:
  enabled: true
  service-name: your-service-name
  server-url: http://localhost:8080
```
