# Deployment Guide

## Docker Compose (Production-like)

```bash
# Build and start everything
docker compose up --build -d

# View logs
docker compose logs -f app

# Stop
docker compose down

# Stop and remove volumes (reset DB)
docker compose down -v
```

## Manual Deployment

### 1. Build the JAR

```bash
mvn clean package -DskipTests
```

### 2. Run

```bash
java -jar target/civilization-operating-system-0.1.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://your-host:5432/openciv \
  --spring.datasource.username=your-user \
  --spring.datasource.password=your-pass
```

## Environment Variables

All Spring properties can be overridden via environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/openciv
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=secret
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
export SERVER_PORT=8080
```

## Database Setup

### PostgreSQL with PostGIS

```bash
# Using Docker
docker run -d \
  --name civos-postgres \
  -e POSTGRES_DB=openciv \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgis/postgis:17-3.5
```

### Schema

The application uses `spring.jpa.hibernate.ddl-auto=update` by default, which auto-creates/updates tables.

For production, set to `validate` and manage migrations with Flyway or Liquibase.

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Ports

| Service | Port | URL |
|---------|------|-----|
| App | 8080 | http://localhost:8080 |
| Swagger | 8080 | http://localhost:8080/swagger-ui |
| API Docs | 8080 | http://localhost:8080/api-docs |
| PostgreSQL | 5432 | — |
| H2 Console | 8080 | http://localhost:8080/h2-console (h2 profile) |
