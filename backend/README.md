# Laboratorio Clínico Renteria - Backend

Sistema de Gestión de Información de Laboratorio Clínico (LIMS) - Backend API

## Tecnologías

- **Java 21** (LTS)
- **Spring Boot 3.4.2**
- **Spring Security** (JWT + BCrypt)
- **Spring Data JPA**
- **PostgreSQL 17**
- **Flyway** (migraciones)
- **Maven**

## Requisitos

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (para PostgreSQL y MailHog)

## Quick Start

### 1. Clonar y entrar al proyecto

```bash
git clone git@github.com:manuelestebanpr/laboratorio-renteria-backend.git
cd laboratorio-renteria-backend
```

### 2. Levantar dependencias (PostgreSQL + MailHog)

```bash
docker-compose up -d
```

Esto levanta:
- PostgreSQL en `localhost:5432`
- MailHog en `http://localhost:8025`

### 3. Configurar variables de entorno

Crear archivo `.env` en la raíz:

```bash
# Base de datos
SPRING_DATASOURCE_USERNAME=lims_user
SPRING_DATASOURCE_PASSWORD=lims_password

# JWT
APP_JWT_SECRET=U2VjcmV0LTM0LWJ5dGVzLXN0cmluZy0xMjM0IQ==
APP_JWT_ACCESS_TOKEN_EXPIRY_MS=900000
APP_JWT_REFRESH_TOKEN_EXPIRY_MS=604800000

# Email (MailHog para desarrollo)
SPRING_MAIL_HOST=localhost
SPRING_MAIL_PORT=1025
APP_EMAIL_FROM=noreply@laboratoriorenteria.com
APP_EMAIL_FRONTEND_URL=http://localhost:4200

# Seguridad
APP_SECURITY_MAX_LOGIN_ATTEMPTS=5
APP_SECURITY_LOCKOUT_DURATION_MS=900000
APP_SECURITY_PASSWORD_RESET_EXPIRY_MS=3600000
APP_SECURITY_MAX_RESET_TOKENS_PER_USER=3
```

### 4. Compilar y correr

```bash
# Compilar
mvn clean install

# Correr
mvn spring-boot:run
```

La API estará disponible en `http://localhost:8080`

## Documentación API

Una vez levantado, accedé a:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

## Endpoints principales

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Login con email/password |
| POST | `/api/v1/auth/refresh` | Refrescar access token |
| POST | `/api/v1/auth/logout` | Cerrar sesión |
| POST | `/api/v1/auth/password` | Cambiar password |
| POST | `/api/v1/auth/password-reset/request` | Solicitar reset de password |
| POST | `/api/v1/auth/password-reset/confirm` | Confirmar reset de password |

## Tests

```bash
# Correr todos los tests
mvn clean test

# Tests + coverage
mvn clean verify
```

## Estructura del proyecto

```
src/
├── main/
│   ├── java/
│   │   └── com/renteria/lims/
│   │       ├── auth/          # Autenticación (JWT, login, refresh)
│   │       ├── common/        # Utilidades y excepciones
│   │       ├── config/        # Configuraciones Spring
│   │       ├── email/         # Servicio de email
│   │       └── user/          # Usuarios, roles, permisos
│   └── resources/
│       ├── db/migration/      # Flyway migrations
│       ├── templates/         # Email templates (Thymeleaf)
│       └── application.yml
└── test/                      # Tests unitarios e integración
```

## Usuarios de prueba

El sistema crea automáticamente usuarios de prueba al iniciar (perfil `dev`):

| Rol | Email | Password |
|-----|-------|----------|
| ADMIN | admin@renteria.com | admin123 |
| EMPLOYEE | employee@renteria.com | employee123 |
| PATIENT | patient@renteria.com | patient123 |

## Desarrollo

### Perfiles de Spring

- `dev`: Desarrollo local (logs debug, usuarios de prueba)
- `test`: Tests (H2 in-memory)
- `prod`: Producción

### Ver emails en desarrollo

MailHog captura todos los emails enviados:
- Web UI: http://localhost:8025

## Docker

```bash
# Construir imagen
docker build -t renteria-lims-backend .

# Correr con docker-compose completo
docker-compose up -d
```

## Seguridad

- **JWT**: Access tokens (15 min) + Refresh tokens (7 días, rotación)
- **BCrypt**: Cost factor 12 para passwords
- **Rate limiting**: 5 intentos login / 15 min, 3 reset / 1 hora
- **CORS**: Configurado para frontend Angular
- **Cookies**: HttpOnly, Secure, SameSite=Strict

## Licencia

Proyecto privado - Laboratorio Clínico Renteria
