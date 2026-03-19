# Internship Tracker — Deployment Report

**Live URL:** https://internship-tracker.tirsansapkota.com/
**Last updated:** 2026-03-18

---

## Table of Contents
1. [Overview](#overview)
2. [SSH / VM Access](#ssh--vm-access)
3. [Deploy (standard)](#deploy-standard)
4. [What the deploy script does](#what-the-deploy-script-does)
5. [Remote paths](#remote-paths)
6. [systemd service](#systemd-service)
7. [Environment & config](#environment--config)
8. [Required env vars](#required-env-vars)
9. [Database](#database)
10. [Health check](#health-check)
11. [Rollback](#rollback)
12. [Logs](#logs)
13. [Troubleshooting](#troubleshooting)

---

## Overview

Spring Boot 3.2 monolith deployed as a fat JAR on an Oracle Cloud VM running Ubuntu.
Served via a systemd service behind a reverse proxy (Nginx or Caddy) with TLS.
No Docker. No CI/CD pipeline — manual deploy via shell script.

---

## SSH / VM Access

| Field    | Value                                          |
|----------|------------------------------------------------|
| Host     | `129.80.110.249`                               |
| User     | `ubuntu`                                       |
| Key      | `~/Documents/cloud-keys/private.pem`           |

```bash
ssh -i ~/Documents/cloud-keys/private.pem ubuntu@129.80.110.249
```

---

## Deploy (standard)

```bash
./deploy.private.sh
```

That's it. Run from the project root. The script handles everything end-to-end.

> **Prerequisite:** Java / Maven Wrapper must work locally (`./mvnw -v`).

---

## What the deploy script does

`deploy.private.sh` executes these steps in order:

1. **Build** — `./mvnw clean package -DskipTests` → produces `target/*SNAPSHOT.jar`
2. **Upload** — `scp` the jar to the VM at `/tmp/app.jar`
3. **Backup** — SSH into VM, copies current `/opt/interntrack/app.jar` to:
   - `/opt/interntrack/app.jar.bak` (always-overwritten latest backup)
   - `/opt/interntrack/app.jar.bak.YYYYMMDD-HHMMSS` (timestamped snapshot)
4. **Replace** — moves `/tmp/app.jar` → `/opt/interntrack/app.jar`
5. **Restart** — `sudo systemctl restart interntrack.service`
6. **Health check** — polls `https://internship-tracker.tirsansapkota.com/` every 2 s
   for up to 60 s, accepting HTTP 200/301/302 as success.
   On failure: prints last 120 lines of `journalctl` and exits non-zero.

---

## Remote paths

| Purpose          | Path                          |
|------------------|-------------------------------|
| Running JAR      | `/opt/interntrack/app.jar`    |
| Latest backup    | `/opt/interntrack/app.jar.bak` |
| Env file         | `/etc/interntrack.env`        |
| Upload staging   | `/tmp/app.jar`                |

---

## systemd service

**Service name:** `interntrack.service`

```bash
# Check status
sudo systemctl status interntrack.service

# Restart
sudo systemctl restart interntrack.service

# Stop
sudo systemctl stop interntrack.service

# Start
sudo systemctl start interntrack.service

# Follow logs live
sudo journalctl -u interntrack.service -f

# Last 200 lines
sudo journalctl -u interntrack.service -n 200 --no-pager
```

The unit file should set `EnvironmentFile=/etc/interntrack.env` so all secrets are
loaded from the env file rather than baked into the unit.

---

## Environment & config

Active Spring profile is controlled by the env var:

```
SPRING_PROFILES_ACTIVE=prod
```

For the demo/seed profile (dev data loaded on startup):

```
SPRING_PROFILES_ACTIVE=demo
```

This is set in `/etc/interntrack.env` on the VM.

Config file used: `src/main/resources/application-prod.properties`

---

## Required env vars

All of these must be present in `/etc/interntrack.env` on the VM.
The app will fail to start if any required var is missing.

| Env var                  | Used for                                         | Example / default |
|--------------------------|--------------------------------------------------|-------------------|
| `DB_URL`                 | JDBC URL for PostgreSQL                          | `jdbc:postgresql://localhost:5432/interntrack` |
| `DB_USER`                | Database username                                | `postgres` |
| `DB_PASS`                | Database password                                | *(secret)* |
| `APP_BASE_URL`           | Base URL in email links                          | `https://internship-tracker.tirsansapkota.com` |
| `MAIL_USER`              | SMTP username (Gmail)                            | Gmail address |
| `MAIL_PASS`              | SMTP password / App Password                     | *(secret)* |
| `MAIL_FROM`              | From address on outbound emails                  | `Internship Tracker <no-reply@internship-tracker.tirsansapkota.com>` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile                            | `prod` |

Optional (have defaults in `application-prod.properties`):

| Env var         | Default              | Notes                  |
|-----------------|----------------------|------------------------|
| `MAIL_HOST`     | `smtp.gmail.com`     |                        |
| `MAIL_PORT`     | `587`                | STARTTLS               |
| `MAIL_SMTP_AUTH`| `true`               |                        |
| `MAIL_STARTTLS` | `true`               |                        |
| `MAIL_DEBUG`    | `false`              | Set to `true` to debug |

### `/etc/interntrack.env` template

```bash
SPRING_PROFILES_ACTIVE=prod

DB_URL=jdbc:postgresql://localhost:5432/interntrack
DB_USER=postgres
DB_PASS=

APP_BASE_URL=https://internship-tracker.tirsansapkota.com

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USER=
MAIL_PASS=
MAIL_FROM=Internship Tracker <no-reply@internship-tracker.tirsansapkota.com>
```

---

## Database

| Field    | Value                                       |
|----------|---------------------------------------------|
| Engine   | PostgreSQL                                  |
| Host     | `localhost` (same VM)                       |
| Port     | `5432`                                      |
| Database | `interntrack`                               |
| User     | `postgres`                                  |
| JDBC URL | `jdbc:postgresql://localhost:5432/interntrack` |

Schema is managed by **Flyway**. Migrations live in:
`src/main/resources/db/migration/V*.sql`

Flyway runs automatically on startup (`spring.flyway.enabled=true`).
`ddl-auto=validate` — Hibernate validates schema against entities but does not modify it.

---

## Health check

```bash
curl -I https://internship-tracker.tirsansapkota.com/
```

Expected: `HTTP/2 200` (or 302 redirect to login).

The deploy script polls this URL automatically and will print service logs + exit 1
if the site doesn't respond within 60 seconds.

---

## Rollback

A timestamped backup is created on every deploy:

```bash
# SSH in
ssh -i ~/Documents/cloud-keys/private.pem ubuntu@129.80.110.249

# List backups
ls -lh /opt/interntrack/

# Restore the most recent backup
sudo cp /opt/interntrack/app.jar.bak /opt/interntrack/app.jar
sudo systemctl restart interntrack.service
```

---

## Logs

```bash
# Follow live
ssh -i ~/Documents/cloud-keys/private.pem ubuntu@129.80.110.249 \
  "sudo journalctl -u interntrack.service -f"

# Last 200 lines (one-shot)
ssh -i ~/Documents/cloud-keys/private.pem ubuntu@129.80.110.249 \
  "sudo journalctl -u interntrack.service -n 200 --no-pager"
```

---

## Troubleshooting

### App won't start

1. Check logs: `sudo journalctl -u interntrack.service -n 200 --no-pager`
2. Verify all required env vars are set in `/etc/interntrack.env`
3. Confirm PostgreSQL is running: `sudo systemctl status postgresql`
4. Check Flyway migration errors (look for `FlywayException` in logs)

### 502 / site unreachable after deploy

1. Service may still be starting — wait 10–15 s and retry health check
2. Check service status: `sudo systemctl status interntrack.service`
3. If service is `failed`, check logs and roll back

### DB connection refused

- Confirm Postgres is listening: `sudo ss -tlnp | grep 5432`
- Confirm `DB_URL`, `DB_USER`, `DB_PASS` in `/etc/interntrack.env` are correct
- Test manually: `psql -U postgres -d interntrack`

### Email not sending

- Set `MAIL_DEBUG=true` in `/etc/interntrack.env` and restart service
- For Gmail: ensure the SMTP password is an **App Password** (not account password)
- Check that "Less secure app access" or App Passwords are enabled on the Gmail account

### Flyway migration failure on startup

- Check migration files in `src/main/resources/db/migration/`
- If a migration was edited after it ran, Flyway checksum will fail —
  create a new `V{n}__fix.sql` rather than modifying existing ones
- To repair checksum (if you must): `flyway repair` (requires Flyway CLI on VM)
