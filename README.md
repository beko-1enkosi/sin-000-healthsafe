# HealthSafe

HealthSafe is a Java based hospital operations system built as a collection of independent services that communicate through REST APIs and asynchronous messaging.

The project demonstrates system integration through data cleaning, synchronous service to service communication, event driven messaging with ActiveMQ, and reliable queue based alert delivery.

## Overview

HealthSafe manages hospital ward information, emergency alert levels, staffing requirements, and equipment failure notifications.

The system begins with a messy legacy CSV dataset and progressively integrates multiple independent services.

```text
Legacy CSV
    |
    v
Ingestion Service :7030
    |
    | REST
    v
Ward Service :7031
    ^
    |
    | REST
    |
Staffing Service :7033 ------> Alert Level Service :7032
       |
       | ActiveMQ Topic
       v
staffing-events-topic
       |
       v
Ward Service

Ward Service
       |
       | ActiveMQ Queue
       v
equipment-failure-queue
       |
       v
Equipment Alert Service :7034
```

## Features

### Data ingestion and cleaning

The Ingestion Service reads the legacy `wards-outdated.csv` file and normalizes the data before exposing it to other services.

Cleaning includes:

* Normalizing ward IDs such as `w-05` to `W-05`
* Trimming leading and trailing whitespace
* Collapsing repeated spaces
* Normalizing text casing
* Converting missing values such as `N/A`, `TBD`, `unknown`, `NaN`, and blanks to `null`
* Handling invalid or non numeric bed counts without crashing
* Rejecting negative and unrealistic bed counts
* Normalizing department naming such as `Pediatrics` to `Paediatrics`
* Detecting duplicate ward IDs

Cleaned ward data is exposed through:

```http
GET /wards
```

---

### Ward Service

The Ward Service consumes cleaned data from the Ingestion Service and exposes hospital ward information.

Endpoints include:

```http
GET /wards
GET /wards/{id}
GET /departments
GET /staffing-events/latest
POST /wards/{id}/equipment-failures
```

Unknown wards return `404 Not Found`.

If the Ingestion Service is unavailable, the Ward Service responds with `503 Service Unavailable` instead of crashing.

---

### Emergency Alert Level Service

The Alert Level Service maintains the current hospital emergency level.

Valid levels range from:

```text
0 to 8
```

where higher values represent increasing emergency severity.

Endpoints:

```http
GET /alert-level
PUT /alert-level
```

Example request:

```json
{
  "level": 5
}
```

Values outside the range `0–8` return `400 Bad Request`.

---

### Staffing Service

The Staffing Service integrates with both the Ward Service and Alert Level Service.

When a staffing request is made, it:

1. Validates the ward through the Ward Service.
2. Retrieves the current emergency level from the Alert Level Service.
3. Calculates the required number of doctors.
4. Returns the staffing recommendation.
5. Publishes a staffing event to ActiveMQ.

Endpoint:

```http
GET /staffing/{wardId}
```

Current staffing rules:

| Alert level | Doctors required |
|---|---:|
| 0–2 | 1 |
| 3–5 | 2 |
| 6–8 | 3 |

Example response:

```json
{
  "wardId": "W-05",
  "department": "Paediatrics",
  "alertLevel": 8,
  "doctorsRequired": 3
}
```

---

## Asynchronous Messaging

HealthSafe uses Apache ActiveMQ for communication that does not require a direct synchronous response.

Two different messaging patterns are demonstrated.

### Staffing Topic

Staffing updates are published to:

```text
staffing-events-topic
```

The Staffing Service acts as the producer and the Ward Service acts as a subscriber.

```text
Staffing Service
       |
       | publish
       v
staffing-events-topic
       |
       | subscribe
       v
Ward Service
```

This uses a **topic** because staffing updates represent events that can be broadcast to interested consumers.

The latest received event can be viewed through:

```http
GET /staffing-events/latest
```

Example:

```json
{
  "event": "W-05,Paediatrics,8,3"
}
```

---

### Equipment Failure Queue

Critical equipment failures are published by the Ward Service to:

```text
equipment-failure-queue
```

and consumed by the Equipment Alert Service.

```text
Ward Service
       |
       | persistent message
       v
equipment-failure-queue
       |
       v
Equipment Alert Service
```

A **queue** is used because equipment failure alerts must not be lost if the consumer is temporarily offline.

Messages are published using persistent delivery.

The Equipment Alert Service uses client acknowledgement and acknowledges messages only after successfully processing them.

An alert can therefore be queued while the Equipment Alert Service is offline and processed when it starts again.

Example equipment failure:

```json
{
  "equipment": "Ventilator",
  "description": "Battery failure"
}
```

The latest processed alert is available from:

```http
GET /alerts/latest
```

Example:

```json
{
  "wardId": "W-05",
  "department": "Paediatrics",
  "equipment": "Ventilator",
  "description": "Battery failure"
}
```

---

## Services

| Service | Port | Responsibility |
|---|---:|---|
| Ingestion Service | 7030 | Cleans and exposes legacy ward data |
| Ward Service | 7031 | Provides wards and departments and handles integration events |
| Alert Level Service | 7032 | Tracks hospital emergency status |
| Staffing Service | 7033 | Calculates staffing requirements |
| Equipment Alert Service | 7034 | Processes critical equipment failure alerts |
| ActiveMQ | 61616 | Message broker connections |
| ActiveMQ Console | 8161 | Broker management interface |

---

## Technology Stack

* Java 17+
* Javalin
* Maven
* Jackson
* OpenCSV
* Java HTTP Client
* Apache ActiveMQ Classic
* JMS
* Docker
* Docker Compose
* REST APIs
* JSON

---

## Project Structure

```text
sin-000-healthsafe/
│
├── ingestion-service/
│   ├── pom.xml
│   └── src/
│
├── ward-service/
│   ├── pom.xml
│   └── src/
│
├── alert-level-service/
│   ├── pom.xml
│   └── src/
│
├── staffing-service/
│   ├── pom.xml
│   └── src/
│
├── equipment-alert-service/
│   ├── pom.xml
│   └── src/
│
├── common/
│   └── docker-compose.yml
│
└── README.md
```

Each service is an independent Maven project.

---

## Requirements

Before running HealthSafe, install:

* Java 17 or newer
* Maven 3.8 or newer
* Docker Desktop

Verify:

```bash
java -version
mvn -version
docker --version
```

---

## Build

Each service can be built independently.

Example:

```bash
cd ingestion-service
mvn clean package
```

Repeat for:

```text
ward-service
alert-level-service
staffing-service
equipment-alert-service
```

---

## Run ActiveMQ

From the project root:

```bash
cd common
docker compose up -d
```

Check the broker:

```bash
docker compose ps
```

ActiveMQ uses:

```text
tcp://localhost:61616
```

The web console is available at:

```text
http://localhost:8161
```

---

## Run the Services

Open a separate terminal for each service.

### Ingestion

```bash
cd ingestion-service
java -jar target/ingestion-service.jar
```

### Ward

```bash
cd ward-service
java -jar target/ward-service.jar
```

### Alert Level

```bash
cd alert-level-service
java -jar target/alert-level-service.jar
```

### Staffing

```bash
cd staffing-service
java -jar target/staffing-service.jar
```

### Equipment Alert

```bash
cd equipment-alert-service
java -jar target/equipment-alert-service.jar
```

---

## Example API Flow

Check cleaned ward data:

```bash
curl http://localhost:7030/wards
```

Retrieve a ward:

```bash
curl http://localhost:7031/wards/W-05
```

Retrieve the emergency level:

```bash
curl http://localhost:7032/alert-level
```

Generate staffing:

```bash
curl http://localhost:7033/staffing/W-05
```

View the latest staffing event:

```bash
curl http://localhost:7031/staffing-events/latest
```

### Report an equipment failure with PowerShell

```powershell
$body = @{
    equipment = "Ventilator"
    description = "Battery failure"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:7031/wards/W-05/equipment-failures" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

Retrieve the processed alert:

```bash
curl http://localhost:7034/alerts/latest
```

---

## Integration Concepts Demonstrated

HealthSafe demonstrates several system integration concepts:

**Data transformation**

Legacy data is cleaned and converted into a consistent representation before other systems consume it.

**Synchronous REST communication**

Services make direct HTTP requests when an immediate response is required.

**Failure handling**

Downstream failures return appropriate HTTP responses such as `404`, `400`, and `503`.

**Publish and subscribe messaging**

Staffing events are broadcast asynchronously through an ActiveMQ topic.

**Reliable queue messaging**

Critical equipment alerts use a persistent ActiveMQ queue so messages can survive temporary consumer downtime.

**Service independence**

Each service is independently buildable and runnable with its own Maven configuration.

---

## Future Improvements

Possible future enhancements include:

* React based hospital operations dashboard
* Automated JUnit integration tests
* Structured JSON staffing events
* Centralized application configuration
* Service discovery
* Persistent storage
* Authentication and authorization
* Containerizing all services
* Docker Compose orchestration for the complete system
* CI/CD

---

## Project Status

HealthSafe currently supports:

```text
Legacy CSV ingestion              ✅
Data cleaning                     ✅
Ward REST API                     ✅
Emergency alert level API         ✅
Staffing REST integration         ✅
Downstream failure handling       ✅
ActiveMQ staffing topic           ✅
ActiveMQ equipment queue          ✅
Persistent equipment alerts       ✅
Client acknowledgement            ✅
```

The core System Integration implementation is complete.

---

## Author

**Thobeka Nkosi**

Built as part of the WeThinkCode_ System Integration elective.