# HealthSafe

HealthSafe is a Java based hospital operations system built as a collection of independent microservices that communicate through REST APIs and asynchronous messaging.

The project demonstrates system integration through legacy data cleaning, synchronous service to service communication, event driven messaging with Apache ActiveMQ, Docker containerisation, and Kubernetes orchestration.

## Overview

HealthSafe manages hospital ward information, emergency alert levels, staffing requirements, and critical equipment failure notifications.

The system begins with a messy legacy CSV dataset and progressively integrates multiple independently running services.

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

HealthSafe can currently run in three ways:

```text
Local Java Processes
        ↓
Docker Compose
        ↓
Kubernetes with Minikube
```

---

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

Higher values represent increasing emergency severity.

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

Example:

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

HealthSafe uses Apache ActiveMQ when communication does not require an immediate synchronous response.

Two messaging patterns are implemented.

### Staffing Topic

Staffing updates are published to:

```text
staffing-events-topic
```

The Staffing Service acts as the publisher and the Ward Service acts as a subscriber.

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

A topic is used because staffing updates represent events that can be broadcast to interested subscribers.

The latest received event is available through:

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

The producer uses persistent JMS delivery.

The Equipment Alert Service uses client acknowledgement and acknowledges a message only after successful processing.

This allows an alert to remain queued when the Equipment Alert Service is temporarily unavailable.

Example:

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

> Note: the current local Kubernetes setup does not provision persistent storage for the ActiveMQ broker itself. Broker storage persistence across ActiveMQ Pod replacement is a future improvement.

---

## Services

| Service | Port | Responsibility |
|---|---:|---|
| Ingestion Service | 7030 | Cleans and exposes legacy ward data |
| Ward Service | 7031 | Provides ward data and handles integration events |
| Alert Level Service | 7032 | Tracks hospital emergency status |
| Staffing Service | 7033 | Calculates staffing requirements |
| Equipment Alert Service | 7034 | Processes equipment failure alerts |
| ActiveMQ | 61616 | Message broker |
| ActiveMQ Console | 8161 | Broker management interface |

---

## Technology Stack

### Backend

* Java 17+
* Javalin
* Maven
* Jackson
* OpenCSV
* Java HTTP Client

### Messaging

* Apache ActiveMQ Classic
* JMS
* Publish and subscribe topics
* Message queues
* Client acknowledgement

### Infrastructure

* Docker
* Docker Compose
* Kubernetes
* Minikube
* kubectl
* Kubernetes Deployments
* Kubernetes Services
* Readiness probes
* Liveness probes

### Integration

* REST APIs
* JSON
* Environment based configuration
* Kubernetes DNS service discovery

---

## Project Structure

```text
sin-000-healthsafe/
│
├── ingestion-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
├── ward-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
├── alert-level-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
├── staffing-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
├── equipment-alert-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
├── common/
│   └── docker-compose.yml
│
├── k8s/
│   ├── activemq.yaml
│   ├── alert-level.yaml
│   ├── equipment-alert.yaml
│   ├── ingestion.yaml
│   ├── staffing.yaml
│   └── ward.yaml
│
├── docker-compose.yml
└── README.md
```

Each Java service is an independent Maven project.

---

# Running HealthSafe

HealthSafe can be run locally, with Docker Compose, or with Kubernetes.

## Requirements

Depending on the deployment method, install:

* Java 17 or newer
* Maven 3.8 or newer
* Docker
* Docker Compose
* kubectl
* Minikube

Check your tools:

```bash
java -version
mvn -version
docker --version
docker compose version
kubectl version --client
minikube version
```

---

# Option 1: Run Locally

Build each Java service independently.

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

Start ActiveMQ:

```bash
cd common
docker compose up -d
```

Then run each service in its own terminal.

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

Local defaults use:

```text
localhost:7030
localhost:7031
localhost:7032
localhost:7033
localhost:7034
localhost:61616
```

---

# Option 2: Run with Docker Compose

Each service includes its own Dockerfile.

The root `docker-compose.yml` starts the complete backend:

```bash
docker compose up --build -d
```

Check the running containers:

```bash
docker compose ps
```

Health checks:

```bash
curl http://localhost:7030/health
curl http://localhost:7031/health
curl http://localhost:7032/health
curl http://localhost:7033/health
curl http://localhost:7034/health
```

Stop the system:

```bash
docker compose down
```

Inside the Docker network, services communicate using container DNS names such as:

```text
http://ingestion-service:7030
http://ward-service:7031
http://alert-level-service:7032
tcp://activemq:61616
```

---

# Option 3: Run with Kubernetes

HealthSafe includes Kubernetes manifests for all five Java microservices and ActiveMQ.

The local Kubernetes environment uses Minikube with the Docker driver.

## Start Minikube

```bash
minikube start --driver=docker
```

Check the cluster:

```bash
minikube status
kubectl get nodes
```

---

## Build the Docker images

From the repository root:

```bash
docker compose build
```

Load the application images into Minikube:

```bash
minikube image load healthsafe-ingestion:latest
minikube image load healthsafe-ward:latest
minikube image load healthsafe-alert-level:latest
minikube image load healthsafe-staffing:latest
minikube image load healthsafe-equipment-alert:latest
```

Verify:

```bash
minikube image ls | grep healthsafe
```

---

## Create the HealthSafe namespace

```bash
kubectl create namespace healthsafe
```

If it already exists, this can safely be replaced with:

```bash
kubectl create namespace healthsafe \
  --dry-run=client \
  -o yaml | kubectl apply -f -
```

---

## Deploy HealthSafe

Apply every Kubernetes manifest:

```bash
kubectl apply -f k8s/
```

Check the workloads:

```bash
kubectl get all -n healthsafe
```

Expected workloads include:

```text
ActiveMQ
Ingestion Service
Ward Service
Alert Level Service
Staffing Service
Equipment Alert Service
```

Each application Deployment currently runs one replica.

---

## Kubernetes Networking

All internal application Services use `ClusterIP`.

Examples:

```text
ingestion-service:7030
ward-service:7031
alert-level-service:7032
staffing-service:7033
equipment-alert-service:7034
activemq:61616
```

Application Pods communicate using Kubernetes DNS instead of hardcoded Pod IP addresses.

For example:

```text
Staffing Pod
    |
    +---- HTTP ----> ward-service:7031
    |
    +---- HTTP ----> alert-level-service:7032
    |
    +---- JMS -----> activemq:61616
```

The Ward Service uses:

```text
INGESTION_SERVICE_URL=http://ingestion-service:7030
ACTIVEMQ_BROKER_URL=tcp://activemq:61616
```

The Staffing Service uses:

```text
WARD_SERVICE_URL=http://ward-service:7031
ALERT_LEVEL_SERVICE_URL=http://alert-level-service:7032
ACTIVEMQ_BROKER_URL=tcp://activemq:61616
```

---

## Kubernetes Health Probes

The Java services expose:

```http
GET /health
```

Kubernetes uses these endpoints for:

* Readiness checks
* Liveness checks

Readiness determines whether a Pod is ready to receive traffic.

Liveness determines whether the application is still healthy.

ActiveMQ uses TCP probes against port `61616`.

---

## Testing Kubernetes Services Locally

Because the internal services use `ClusterIP`, they are not exposed directly outside the cluster.

For local testing, use `kubectl port-forward`.

Example:

```bash
kubectl port-forward \
  -n healthsafe \
  service/ingestion-service \
  7030:7030
```

Then:

```bash
curl http://localhost:7030/health
curl http://localhost:7030/wards
```

Ward:

```bash
kubectl port-forward \
  -n healthsafe \
  service/ward-service \
  7031:7031
```

Staffing:

```bash
kubectl port-forward \
  -n healthsafe \
  service/staffing-service \
  7033:7033
```

Equipment Alert:

```bash
kubectl port-forward \
  -n healthsafe \
  service/equipment-alert-service \
  7034:7034
```

ActiveMQ Console:

```bash
kubectl port-forward \
  -n healthsafe \
  service/activemq \
  8161:8161
```

Then open:

```text
http://localhost:8161/admin
```

---

## Kubernetes Self Healing

HealthSafe was tested by manually deleting the Ingestion Pod.

The Deployment immediately created a replacement Pod because the desired replica count remained:

```yaml
replicas: 1
```

This demonstrates Kubernetes desired state reconciliation:

```text
Desired replicas: 1
Actual replicas: 0
        ↓
Kubernetes creates replacement Pod
        ↓
Readiness probe succeeds
        ↓
Desired replicas: 1
Actual replicas: 1
```

---

# Example API Flow

Retrieve cleaned ward data:

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

Update the emergency level:

```bash
curl -X PUT \
  -H "Content-Type: application/json" \
  -d '{"level":8}' \
  http://localhost:7032/alert-level
```

Generate a staffing recommendation:

```bash
curl http://localhost:7033/staffing/W-05
```

Example:

```json
{
  "wardId": "W-05",
  "department": "Paediatrics",
  "alertLevel": 8,
  "doctorsRequired": 3
}
```

View the latest staffing event:

```bash
curl http://localhost:7031/staffing-events/latest
```

Report an equipment failure:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"equipment":"Ventilator","description":"Battery failure"}' \
  http://localhost:7031/wards/W-05/equipment-failures
```

Retrieve the processed alert:

```bash
curl http://localhost:7034/alerts/latest
```

---

# Integration Concepts Demonstrated

### Data Transformation

Legacy CSV data is cleaned and converted into a consistent representation before downstream services consume it.

### Synchronous REST Communication

Services communicate directly over HTTP when an immediate response is required.

### Failure Handling

Downstream failures are handled using HTTP responses including `400`, `404`, and `503`.

### Publish and Subscribe Messaging

Staffing events are broadcast asynchronously using an ActiveMQ topic.

### Queue Messaging

Critical equipment failure alerts are sent through an ActiveMQ queue using persistent delivery and client acknowledgement.

### Environment Based Configuration

Service addresses are supplied through environment variables.

The same Java applications can therefore run locally, inside Docker, and inside Kubernetes without changing application code.

### Containerisation

Each Java microservice is packaged into an independent Docker image.

### Docker Service Discovery

Docker Compose provides internal DNS names for service to service communication.

### Kubernetes Deployments

Deployments manage application Pods and maintain the desired replica count.

### Kubernetes Service Discovery

ClusterIP Services provide stable network identities while individual Pod IP addresses can change.

### Health Monitoring

Readiness and liveness probes allow Kubernetes to monitor whether workloads are available and healthy.

### Self Healing

Kubernetes automatically creates replacement Pods when managed Pods are deleted or fail.

---

# Deployment Evolution

HealthSafe was developed progressively:

```text
Stage 1
Java applications running directly on localhost

        ↓

Stage 2
Independent Docker containers

        ↓

Stage 3
Docker Compose orchestration and container DNS

        ↓

Stage 4
Kubernetes Deployments and ClusterIP Services

        ↓

Stage 5
Kubernetes health probes, service discovery and self healing
```

This progression allowed each infrastructure layer to be tested before introducing the next.

---

# Current Project Status

```text
Legacy CSV ingestion                 ✅
Data cleaning                        ✅
Ward REST API                        ✅
Emergency alert level API            ✅
Staffing REST integration            ✅
Downstream failure handling          ✅
ActiveMQ staffing topic              ✅
ActiveMQ equipment queue             ✅
Persistent JMS messages              ✅
Client acknowledgement               ✅
Environment based configuration      ✅
Docker images                        ✅
Docker Compose orchestration         ✅
Container service discovery          ✅
Kubernetes Deployments               ✅
Kubernetes ClusterIP Services        ✅
Kubernetes DNS service discovery     ✅
Readiness and liveness probes        ✅
Kubernetes self healing              ✅
React operations dashboard           ⏳
Hosting                              ⏳
```

---

# Next Steps

The backend and local infrastructure implementation are complete.

The next phase of HealthSafe is focused on:

* React based hospital operations dashboard
* Live ward overview
* Emergency status controls
* Staffing recommendations
* Equipment alert interface
* System architecture and service status view
* Skeleton loading states
* Responsive design
* Frontend and backend hosting

Future infrastructure improvements may include:

* Persistent storage for ActiveMQ
* Kubernetes ConfigMaps and Secrets
* Resource requests and limits
* External container registry
* Kubernetes Ingress
* Cloud hosted Kubernetes deployment
* Automated integration testing
* Authentication and authorization

---

## Author

**Thobeka Nkosi**

Built as part of the WeThinkCode_ System Integration elective and extended as a personal portfolio project.