# IP Blocklist API

Servicio **Spring Boot + Slack Bolt (Socket Mode)** para la gestión de IPs bloqueadas en entornos de red, con integración directa a Slack para ejecución y auditoría de comandos.

## Características principales

- Gestión de IPs mediante comandos `/ip` desde Slack:
  - `add`, `deactivate`, `reactivate`, `edit`, `list`
- Control de acceso mediante *whitelist* de canales.
- Auditoría automática de comandos ejecutados.
- Persistencia reactiva con **R2DBC + MySQL**.
- Migraciones gestionadas con **Flyway**.
- Soporte completo para **Docker** y despliegue en entornos productivos.
- Tests unitarios con **JUnit 5 + Mockito**.

## Estructura principal del proyecto

```text
src/
 ├── main/java/com/ipblocklist/api
 │    ├── slack/                 # Módulos de integración con Slack
 │    ├── service/               # Servicios de negocio (IPs, usuarios, canales)
 │    ├── repository/            # Repositorios R2DBC
 │    ├── model/                 # Entidades y DTOs
 │    └── config/                # Configuración (Beans, AppProperties, etc.)
 └── test/java/...               # Tests unitarios
```

## Configuración de entorno

El archivo `application.yml` controla las propiedades base.  
Asegúrate de incluir tus credenciales y claves de Slack:

```yaml
app:
  allowed-channels: C08AXL1SYBC,C08EXAMPLECH
slack:
  bot-token: xoxb-xxxxxx
  app-token: xapp-xxxxxx
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/ip_blocklist
    username: root
    password: tu_password
  flyway:
    url: jdbc:mysql://localhost:3306/ip_blocklist
    user: root
    password: tu_password
```

## Requisitos previos

- **Java 21+**
- **Maven 3.9+**
- **MySQL 8.x** (o compatible con R2DBC)
- **Slack App** con permisos:
  - `commands`, `chat:write`, `users:read`, `app_mentions:read`
- **Socket Mode** habilitado en Slack.

## Preparación de la base de datos

Antes de iniciar la aplicación (ya sea localmente o en Docker), **debes crear manualmente la base de datos vacía** donde se aplicarán las migraciones de Flyway.

Por ejemplo, desde MySQL:

```bash
mysql -u root -p
CREATE DATABASE ip_blocklist CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Una vez creada, Flyway se encargará de aplicar automáticamente todas las migraciones en el arranque.

## Ejecución en desarrollo

```bash
# 1. Compilar el proyecto
mvn clean package -DskipTests

# 2. Ejecutar con perfil "dev"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

También puedes ejecutar directamente el JAR:

```bash
java -jar target/ip-blocklist-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## Ejecución en Docker

### Build de la imagen

```bash
docker build -t ip-blocklist-api .
```

### Ejecución manual (PowerShell compatible)

```powershell
docker run --name ip-blocklist-api -p 8081:8080 `
-e SPRING_R2DBC_URL=r2dbc:mysql://192.168.0.9:3306/ip_blocklist `
  -e SPRING_R2DBC_USERNAME=root `
-e SPRING_R2DBC_PASSWORD=tu_password `
  -e SPRING_FLYWAY_URL=jdbc:mysql://192.168.0.9:3306/ip_blocklist `
-e SPRING_FLYWAY_USER=root `
  -e SPRING_FLYWAY_PASSWORD=tu_password `
-e SPRING_PROFILES_ACTIVE=prod `
  ip-blocklist-api
```

## docker-compose (opcional)

```yaml
name: blocklist-dev-environment

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
    container_name: ipblocklist-api
    depends_on:
      - db
    environment:
      SPRING_R2DBC_URL: r2dbc:mysql://db:3306/ip_blocklist
      SPRING_R2DBC_USERNAME: root
      SPRING_R2DBC_PASSWORD: root
      SPRING_FLYWAY_URL: jdbc:mysql://db:3306/ip_blocklist
      SPRING_FLYWAY_USER: root
      SPRING_FLYWAY_PASSWORD: root
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"

volumes:
  mysql_data:
    driver: local
```

## Uso en Slack

### Comandos disponibles

| Comando | Descripción |
|-|-|
| `/ip add <IP> [reason]` | Agrega una IP a la lista bloqueada |
| `/ip deactivate <IP> [reason]` | Desactiva una IP previamente registrada |
| `/ip reactivate <IP> [reason]` | Reactiva una IP desactivada |
| `/ip edit <IP> <new reason>` | Edita la razón de bloqueo |
| `/ip list` | Muestra las IPs activas (máx. 200) |

### Ejemplo de uso

```text
/ip add 203.0.113.5 abusive traffic
```

El comando también se registra en el canal de auditoría definido en la whitelist.

## Tests

Para ejecutar todos los tests:

```bash
mvn test
```

## Recomendaciones

- Mantén la whitelist de canales (`app.allowed-channels`) actualizada.
- No uses el bot fuera de canales controlados: ignorará los comandos.
- Si cambias el token de Slack, reinicia el servicio para regenerar la conexión Socket Mode.
- Usa perfiles (`dev`, `prod`) para separar tus entornos.
- Implementa alertas de auditoría si lo integras con herramientas como Grafana o Loki.
