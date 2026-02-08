# Laboratorio Clínico Renteria - LIMS

Clinical Laboratory Information Management System

## Architecture

- **Backend**: Spring Boot 3.4.x, Java 21, PostgreSQL 17
- **Frontend**: Angular (future implementation)
- **Security**: JWT with refresh token rotation, BCrypt, RBAC

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21
- Maven

### Development Setup

```bash
# Start infrastructure
docker-compose up -d

# Run backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Services

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| MailHog UI | http://localhost:8025 |
| PostgreSQL | localhost:5432 |

## API Documentation

### Auth Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Refresh token |
| POST | `/api/v1/auth/logout` | Logout |
| POST | `/api/v1/auth/password` | Change password |
| POST | `/api/v1/auth/password-reset/request` | Request password reset |
| POST | `/api/v1/auth/password-reset/confirm` | Confirm password reset |

## Security

- JWT access tokens (15 min expiry)
- Refresh token rotation with family tracking
- BCrypt password hashing (cost 12)
- Rate limiting on auth endpoints
- Account lockout after 5 failed attempts

## License

Private - Laboratorio Clínico Renteria
