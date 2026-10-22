# Civilization Operating System

A resource-based civilization platform with an interactive world map. The repository contains a React web client, a Spring Boot application, and a separately runnable Cortex worker.

## Features

- Interactive world map and resource regions
- Civilizations, citizens, governance, elections, and migration
- Resource production, needs, logistics, and trade
- Nexus mesh, events, and simulation services
- Cortex worker for automated decisions
- Live updates through WebSocket and server-sent events where supported

## Architecture

- **Frontend:** React, TypeScript, and Vite. The production frontend container serves the compiled app with Nginx.
- **Backend:** Java 25 and Spring Boot 4. The root Maven project is a modular monolith with REST controllers and GraphQL; Nginx also routes live connections to the backend.
- **Worker:** Cortex runs as a separate process/container using the same root Maven project and its worker profile.
- **Data and messaging:** PostgreSQL 17 with PostGIS and Redis are used by the application. The full Compose stack also includes Kafka and Debezium.
- **Service scaffolding:** `services/` contains a separate Maven reactor/scaffold. Root CI and the Dockerfiles build the root application, not those scaffold modules.

## Quick start

### Prerequisites

- Docker and Docker Compose for the containerized stack
- Java 25 for backend development
- Node.js 24 and npm for frontend development

### Core Compose services

```bash
make docker-up
```

This builds and starts PostgreSQL, the Spring Boot `app` and `app-primary` instances, and dependencies required by the application. It does not start the frontend, Cortex worker, Kafka, or Debezium.

### Full Compose stack

```bash
make docker-up-all
```

This builds and starts all services in `docker-compose.yml`, including the frontend, Nginx, Cortex worker, Kafka, and Debezium. The frontend is exposed on port 3000 and the Nginx entry point on port 80; the application is exposed on port 8080 and the worker on port 9090.

To stop the stack:

```bash
make docker-down
```

### Local development

Run the backend with the development profile (H2):

```bash
make run
```

Run the frontend dev server in a separate terminal:

```bash
make frontend-dev
```

The production frontend image currently uses Node 20 to build its assets, while frontend CI uses Node 24. Keep those toolchains aligned in a follow-up change.

### Build and checks

```bash
make build
make test
make build-frontend
```

Pull requests run the Java validation/test workflow and the frontend lint, Vitest, and production build checks. The root workflow does not build the `services/` scaffold.

## API entry points

When the backend is running directly or through Compose:

- REST API routes are rooted at `/api/v1/`.
- GraphQL is available at `/graphql`.
- Spring Boot health is available at `/actuator/health`.
- OpenAPI JSON and Swagger UI are configured by the active Spring profile; check `src/main/resources/application.yml` and `src/main/resources/application-prod.yml` for the exact paths.
- The Nexus module exposes a server-sent event stream at `/api/v1/nexus/stream`.

## Configuration

Compose accepts these commonly used environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CIVILIZATION_SCALE` | `LOCAL` | Seed scale (`LOCAL`, `REGIONAL`, `CONTINENTAL`, or `GLOBAL`) |
| `CIVILIZATION_ADMIN_USER` | `admin` | Admin username |
| `CIVILIZATION_ADMIN_PASSWORD` | `admin` | Admin password |
| `POSTGRES_USER` | `postgres` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `postgres` | PostgreSQL password |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile used by the app container |

The defaults are intended for local development. Set credentials appropriate to your environment before exposing the stack beyond your machine.

## Repository layout

```text
frontend/   React/Vite application and frontend container
src/        Root Spring Boot application and Cortex worker
services/   Separate service scaffold/reactor
debezium/   CDC connector setup
```

## License

MIT
