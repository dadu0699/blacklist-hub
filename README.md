# Blacklist Hub

**Spring Boot + Slack Bolt (Socket Mode)** service for managing Indicators of Compromise (IoC) (IPs, Hashes, Domains, URLs) in network environments, with direct Slack integration for command execution and auditing.

## Key Features

- Management of multiple IoC types via Slack commands:
  - **IPs** (`/ip`): `add`, `deactivate`, `reactivate`, `edit`, `list`, `bulk`
  - **Hashes** (`/hash`): `add`, `deactivate`, `reactivate`, `edit`, `list`, `bulk`
  - **Domains** (`/domain`): `add`, `deactivate`, `reactivate`, `edit`, `list`, `bulk`
  - **URLs** (`/url`): `add`, `deactivate`, `reactivate`, `edit`, `list`, `bulk`
- Access control via channel *whitelist*.
- Automatic auditing of executed commands.
- Reactive persistence with **R2DBC + MySQL**.
- Migrations managed with **Flyway**.
- Full **Docker** support and production deployment ready.

## Project Structure

```text
src/
 └── main/java/com/blacklisthub/
      ├── BlacklistHubApplication.java  # Application entry point
      ├── controller/                   # REST Controllers
      ├── entity/                       # JPA Entities (IpEntity, HashEntity, DomainEntity, UrlEntity, etc.)
      ├── repository/                   # R2DBC Repositories
      ├── service/                      # Business Services (IpService, HashService, DomainService, UrlService)
      └── slack/                        # Slack integration modules
           ├── config/                  # Slack configuration (SlackClientsConfig, SlackProps)
           ├── service/                 # Slack command services (IpCommandService, HashCommandService, etc.)
           ├── util/                    # Utilities (AuditHelper, CommandParser, SlackMessageFormatter, etc.)
           └── SlackBoltRunner.java     # Main Slack Bolt runner
```

## Environment Configuration

The `application.yml` file controls the base properties.  
Make sure to include your Slack credentials and keys:

```yaml
app:
  allowed-channels: C08AXL1SYBC,C08EXAMPLECH
slack:
  bot-token: xoxb-xxxxxx
  app-token: xapp-xxxxxx
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/blacklist_hub
    username: root
    password: your_password
  flyway:
    url: jdbc:mysql://localhost:3306/blacklist_hub
    user: root
    password: your_password
```

## Prerequisites

- **Java 25+**
- **Maven 3.9+**
- **MySQL 8.x** (or R2DBC compatible)
- **Slack App** with permissions:
  - `commands`, `chat:write`, `users:read`, `app_mentions:read`
- **Socket Mode** enabled in Slack.

## Database Setup

Before starting the application (either locally or in Docker), **you must manually create the empty database** where Flyway migrations will be applied.

For example, from MySQL:

```bash
mysql -u root -p
CREATE DATABASE blacklist_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Once created, Flyway will automatically apply all migrations on startup.

## Running in Development

```bash
# 1. Build the project
mvn clean package -DskipTests

# 2. Run with "dev" profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

You can also run the JAR directly:

```bash
java -jar target/blacklist-hub-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## Running with Docker

### Build the image

```bash
docker build -t blacklist-hub .
```

### Manual execution (PowerShell compatible)

```powershell
docker run --name blacklist-hub -p 8081:8080 `
  -e SPRING_R2DBC_URL=r2dbc:mysql://192.168.0.9:3306/blacklist_hub `
  -e SPRING_R2DBC_USERNAME=root `
  -e SPRING_R2DBC_PASSWORD=your_password `
  -e SPRING_FLYWAY_URL=jdbc:mysql://192.168.0.9:3306/blacklist_hub `
  -e SPRING_FLYWAY_USER=root `
  -e SPRING_FLYWAY_PASSWORD=your_password `
  -e SPRING_PROFILES_ACTIVE=prod `
  blacklist-hub
```

## docker-compose (optional)

```yaml
name: blacklist-hub-environment

services:
  db:
    image: mysql:lts
    container_name: mysql-dev
    ports:
      - '3306:3306'
    volumes:
      - mysql_data:/var/lib/mysql
    environment:
      MYSQL_ROOT_PASSWORD: root

  api:
    build: .
    container_name: blacklist-hub-api
    depends_on:
      - db
    environment:
      SPRING_R2DBC_URL: r2dbc:mysql://db:3306/blacklist_hub
      SPRING_R2DBC_USERNAME: root
      SPRING_R2DBC_PASSWORD: root
      SPRING_FLYWAY_URL: jdbc:mysql://db:3306/blacklist_hub
      SPRING_FLYWAY_USER: root
      SPRING_FLYWAY_PASSWORD: root
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"

volumes:
  mysql_data:
    driver: local
```

## Slack Usage

### Available Commands

#### 🌎 IP Commands

| Command | Description |
|-|-|
| `/ip add <IP> [reason]` | Adds an IP to the blocklist |
| `/ip deactivate <IP> [reason]` | Deactivates a previously registered IP |
| `/ip reactivate <IP> [reason]` | Reactivates a deactivated IP |
| `/ip edit <IP> <new reason>` | Edits the block reason |
| `/ip list` | Shows active IPs (max. 200) |
| `/ip bulk <IP1,IP2,...> [reason]` | Adds multiple comma-separated IPs |

#### 🔑 Hash Commands

| Command | Description |
|-|-|
| `/hash add <HASH> [reason]` | Adds a HASH (SHA-256, etc.) |
| `/hash deactivate <HASH> [reason]` | Deactivates a HASH |
| `/hash reactivate <HASH> [reason]` | Reactivates a HASH |
| `/hash edit <HASH> <new reason>` | Edits the reason |
| `/hash list` | Shows active HASHes (max. 200) |
| `/hash bulk <H1,H2,...> [reason]` | Adds multiple HASHes |

#### 🖥️ Domain Commands

| Command | Description |
|-|-|
| `/domain add <DOMAIN> [reason]` | Adds a Domain |
| `/domain deactivate <DOMAIN> [reason]` | Deactivates a Domain |
| `/domain reactivate <DOMAIN> [reason]` | Reactivates a Domain |
| `/domain edit <DOMAIN> <new reason>` | Edits the reason |
| `/domain list` | Shows active Domains (max. 200) |
| `/domain bulk <D1,D2,...> [reason]` | Adds multiple Domains |

#### 🔗 URL Commands

| Command | Description |
|-|-|
| `/url add <URL> [reason]` | Adds a URL |
| `/url deactivate <URL> [reason]` | Deactivates a URL |
| `/url reactivate <URL> [reason]` | Reactivates a URL |
| `/url edit <URL> <new reason>` | Edits the reason |
| `/url list` | Shows active URLs (max. 100) |
| `/url bulk <U1,U2,...> [reason]` | Adds multiple URLs |

### Usage Example

```text
/ip add 203.0.113.5 abusive traffic
/hash add ea7dec8fa52d2300350367691ae2fbea13dbd5bf80d6b43b05eedf197529aa77 malware sample C2
```

Commands are also logged to the audit channel defined in the whitelist.

## Recommendations

- Keep the channel whitelist (`app.allowed-channels`) updated.
- Don't use the bot outside of controlled channels: it will ignore commands.
- If you change the Slack token, restart the service to regenerate the Socket Mode connection.
- Use profiles (`dev`, `prod`) to separate your environments.
