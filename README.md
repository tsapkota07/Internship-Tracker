# Internship Tracker

A full-stack Spring Boot web application designed to help users organize and manage internship applications in a structured, secure environment.

The application supports authenticated users and guest sessions, includes secure email verification and password reset flows, and is deployed to a Linux-based cloud server.

**Live Demo:**  
https://internship-tracker.tirsansapkota.com

---

## Why I Built This

Applying to internships can quickly become disorganized across spreadsheets, notes, and emails. I built this project to create a centralized tracking system while strengthening my skills in backend architecture, authentication flows, database design, and real-world cloud deployment.

This project reflects my ability to build, secure, deploy, and maintain a production-style application independently.

---

## Core Features

### Authentication & Account Management
- User registration and login
- BCrypt password hashing
- Email verification with expiring, one-time tokens
- Secure password reset workflow
- Session fixation protection
- CSRF protection

### Internship Application Management
- Create, edit, and delete applications
- Track application status
- Soft-delete support
- Export application data

### Guest Mode
- Use the application without creating an account
- Applications stored in session
- Import guest applications after registration

### Email System
- SMTP-based email delivery
- Token-based verification links
- Token expiration and invalidation logic
- Protection against reuse of expired or used tokens

---

## Architecture Overview

The application follows a layered architecture:

- **Controller Layer** — Handles HTTP requests and routing
- **Service Layer** — Business logic and security workflows
- **Repository Layer** — Data persistence using Spring Data JPA
- **Model Layer** — Entity definitions and domain models
- **DTO Layer** — Form data validation and transfer objects
- **Configuration Layer** — Security and application configuration

Security is implemented using Spring Security with custom success handlers and token validation logic.

---

## Tech Stack

**Backend**
- Java
- Spring Boot
- Spring Security
- Spring Data JPA (Hibernate)
- Thymeleaf (server-side rendering)

**Database**
- Relational database (configured via environment variables)

**Infrastructure & Deployment**
- Oracle Cloud Infrastructure (OCI)
- Linux VM
- systemd service management
- Reverse proxy configuration
- Packaged as an executable JAR

**Build Tool**
- Maven Wrapper

---

## Security Practices

- No hardcoded credentials
- Environment variable–based configuration
- BCrypt password hashing
- CSRF protection enabled
- Session fixation protection
- Expiring, one-time verification tokens
- Token invalidation on new request

---

## Environment Configuration

Sensitive configuration is handled through environment variables.

Required variables:

- `DB_URL`
- `DB_USER`
- `DB_PASS`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USER`
- `MAIL_PASS`
- `APP_BASE_URL`

---

## Running Locally

1. Clone the repository  
2. Set the required environment variables  
3. Run the application:

```bash
./mvnw spring-boot:run
