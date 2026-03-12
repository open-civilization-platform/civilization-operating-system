# 🌍 Civilization Operating System

> A **Resource-Based Economy** platform built with Spring Boot 4, Spring Framework 7, and Java 25.  
> Inspired by [The Zeitgeist Movement](https://www.thezeitgeistmovement.com/) and [The Venus Project](https://www.thevenusproject.com/).

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📋 Overview

Civilization OS is a monolithic MVC application that models a **post-scarcity, science-based civilization**. It provides modules for global resource management, human needs fulfillment, automated production, scientific governance, and AI-driven simulation — all coordinated through a digital operating system.

### Core Modules

| Module | Description |
|--------|------------|
| 🌍 **Biosphere** | Environmental monitoring with metrics and critical event alerts |
| ⚡ **Resources** | Global resource mapping with geospatial data (PostGIS) |
| 🧬 **Needs** | Human needs tracking by region and priority |
| 📊 **Strategy** | Resource balance analysis (supply vs demand) |
| 🏭 **Production** | Automated facility management and output tracking |
| 🚚 **Logistics** | Supply chain and shipment coordination |
| 📜 **Constitution** | Digital governance rules with scientific validation |
| 💬 **Interaction** | Community interaction and participation log |
| 🎯 **Purpose** | Human contribution system with reputation scoring |
| 🤝 **Social** | Community stability, incidents, and case management |
| 🧠 **Cortex** | AI simulation engine for automated decision-making |

---

## 🛠️ Tech Stack

- **Java 25** + **Spring Boot 4.0.3** + **Spring Framework 7**
- **Spring MVC** (Servlet) + **Thymeleaf** (SSR)
- **Spring Data JPA** + **Hibernate 7** + **Hibernate Spatial**
- **PostgreSQL 17** + **PostGIS 3.5** (geospatial)
- **H2** (dev/test fallback)
- **Reaktor** (`reaktor-spring-mvc-starter`) — MVC starter
- **SpringDoc OpenAPI** — Swagger UI
- **Docker** + **Docker Compose**

---

## 🚀 Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker & Docker Compose

### Option 1: Docker Compose (Recommended)

```bash
docker compose up --build
```

App starts at [http://localhost:8080](http://localhost:8080)  
Swagger UI at [http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

### Option 2: Local Development

**1. Start PostgreSQL:**

```bash
docker compose up postgres
```

**2. Build & Run:**

```bash
mvn clean spring-boot:run
```

### Option 3: H2 In-Memory (No Docker)

```bash
mvn clean spring-boot:run -Dspring.profiles.active=h2
```

H2 Console at [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

## 📁 Project Structure

```
civilization-operating-system/
├── src/main/java/io/github/opencivilizationplatform/
│   ├── web/
│   │   ├── CivilizationOsApplication.java      # Main entry point
│   │   └── controller/
│   │       └── PageController.java              # MVC routes (12 views)
│   ├── config/
│   │   └── SeedDataConfig.java                  # Sample data seeding
│   ├── core/event/                              # Domain events
│   ├── dto/                                     # Data transfer objects
│   └── modules/
│       ├── contribution/   # Purpose & contribution system
│       ├── execution/      # Automation units
│       ├── governance/     # Scientific committees
│       ├── logistics/      # Shipment management
│       ├── monitoring/     # Biosphere metrics
│       ├── needs/          # Human needs tracking
│       ├── participation/  # Rules & interactions
│       ├── production/     # Facility management
│       ├── resources/      # Global resource mapping
│       ├── simulation/     # Cortex AI engine
│       ├── social/         # Social stability
│       └── strategy/       # Balance analysis
├── src/main/resources/
│   ├── application.yml           # PostgreSQL config
│   ├── application-h2.yml        # H2 dev profile
│   ├── templates/                # Thymeleaf views (11 templates)
│   └── static/css/main.css       # Dark glassmorphism design system
├── Dockerfile                    # Multi-stage build (JDK 25 → JRE 25)
├── docker-compose.yml            # PostGIS + App
└── pom.xml                       # Standalone Spring Boot POM
```

---

## 🔌 REST API

All REST endpoints are under `/api/v1/`:

| Method | Endpoint | Module |
|--------|---------|--------|
| GET | `/api/v1/strategy/balance` | Strategy |
| GET/POST | `/api/v1/resources` | Resources |
| GET/POST | `/api/v1/needs` | Needs |
| GET | `/api/v1/needs/{region}` | Needs |
| GET/POST | `/api/v1/facilities` | Production |
| GET/POST | `/api/v1/shipments` | Logistics |
| GET/POST | `/api/v1/biosphere` | Monitoring |
| GET/POST | `/api/v1/rules` | Constitution |
| POST | `/api/v1/rules/{id}/vote` | Constitution |
| GET/POST | `/api/v1/interactions` | Interaction |
| GET/POST | `/api/v1/automation` | Execution |
| GET/POST | `/api/v1/governance` | Governance |
| GET/POST | `/api/v1/purpose/contribute` | Contribution |
| GET | `/api/v1/purpose/citizens` | Contribution |
| GET | `/api/v1/purpose/projects` | Contribution |
| GET/POST | `/api/v1/social/incidents` | Social |
| GET | `/api/v1/social/cases` | Social |
| GET | `/api/v1/simulation/status` | Cortex |

> Full interactive docs: [/swagger-ui](http://localhost:8080/swagger-ui)

---

## 🧠 Cortex Simulation Engine

The Cortex engine runs a **15-second cycle** that:

1. Fetches all scientifically validated rules
2. Analyzes the resource balance report
3. Checks each rule's logic (e.g., `RESERVE_CHECK`)
4. Triggers automated decisions when deficiencies are detected
5. Responds to `BiosphereCriticalEvent` in real-time

---

## 🎨 Frontend

Server-side rendered with **Thymeleaf** and a **dark glassmorphism** design:

- Inter + Outfit typography (Google Fonts)
- Glass-morphism cards with blur effects
- Cyan/blue accent gradient
- Responsive grid layouts
- Status badges with semantic colors

**Views:** Dashboard, Biosphere, Resources, Needs, Strategy, Production, Logistics, Constitution, Purpose, Social, Cortex

---

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/openciv` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | DB password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | Schema strategy |
| `SPRING_PROFILES_ACTIVE` | — | Set to `h2` for in-memory DB |

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

<p align="center">
  <strong>Built for a Resource-Based Economy 🌍</strong><br>
  <em>Open Civilization Platform</em>
</p>
