# Internship Tracker — Project Overview

> Live at: [internship-tracker.tirsansapkota.com](https://internship-tracker.tirsansapkota.com)

---
Look at this project of mine. I did this a long while ago and now forgot a lot of thigns. Give me a rundown. This is live now at internship-tracker.tirsansapkota.com . Next, I plan to build a personal…
## What It Does

A full-stack web app that lets users track internship applications — status, company, role, dates, notes, links. Supports guest usage (no account needed) with seamless migration to a real account on login.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Templating | Thymeleaf (server-side HTML) |
| Security | Spring Security (sessions, CSRF, BCrypt) |
| Database | PostgreSQL via Spring Data JPA |
| Email | Spring Mail (SMTP / Gmail) |
| Build | Maven (`./mvnw`) |
| Hosting | Oracle Cloud VM (Ubuntu, systemd) |

There is **no separate frontend framework** — everything is server-rendered HTML with custom CSS for theming.

---

## Project Structure

```
src/main/java/com/tirsansapkota/internshiptracker/
├── config/         # Security config, login success handler, demo data seeder
├── controller/     # HTTP request handlers (render Thymeleaf views)
├── dto/            # Form input objects (validation)
├── model/          # JPA entities (database tables)
├── repository/     # Spring Data JPA interfaces
├── service/        # Business logic
└── web/            # Global controller advice (injects shared UI state)

src/main/resources/
├── templates/      # Thymeleaf HTML pages
├── static/css/     # Stylesheets (layout, theme, per-page)
├── application-demo.properties
└── application-prod.properties
```

---

## Database Tables

### `app_users`
Stores registered users.
- `id`, `username` (unique), `password_hash`
- `email` (optional, unique), `email_verified`, `pending_email`
- `role` (USER), `demo_user` flag

### `internship_application`
One row per application.
- `id`, `company`, `role`, `status` (APPLIED | INTERVIEW | OFFER | REJECTED)
- `applied_date`, `link`, `location`, `notes`
- `user_id` (FK → app_users)
- `deleted_at` — soft-delete; null means active, timestamp means deleted

### `user_preferences`
One row per user.
- `user_id` (PK, FK), `theme` (LIGHT | DARK), `default_status`

### `verification_tokens`
One-time tokens for email flows.
- `token` (UUID), `type` (EMAIL_VERIFY | EMAIL_CHANGE | PASSWORD_RESET)
- `user_id`, `expires_at`, `used`, `payload` (stores the email being verified)

---

## Key Features

### Application Management
- Create, edit, delete, restore internship entries
- Filter by status (APPLIED, INTERVIEW, OFFER, REJECTED) or search by company/role
- Soft-delete with a "Recently Deleted" view and restore option
- Export all applications to CSV

### Guest Mode
- Anyone can add applications without an account
- Guest apps live in the HTTP session
- On register or login → prompted to import all or select which to keep

### Authentication
- Register with username + optional email
- Login with either username or email
- BCrypt password hashing
- Session fixation protection, CSRF enabled

### Email Flows
- Email verification (60-min expiring token link)
- Password reset (15-min expiring token link)
- Email change confirmation
- Anti-enumeration: login errors don't reveal if an email exists

### Preferences
- Light / Dark theme toggle
- Default status for new applications
- Requires verified email to access

### Demo Account
- Seeded via `DEMO_SEED_ENABLED` env var
- Blocked from: email changes, account deletion

---

## Request Flow (How a Page Renders)

```
Browser → Controller → Service → Repository → DB
                    ↓
              Thymeleaf template
                    ↓
              HTML response → Browser
```

The `ControllerAdvice` in `/web/` runs on every request and injects shared state into the model: current user, theme, guest app count.

---

## Controllers at a Glance

| Controller | Paths | Purpose |
|---|---|---|
| `AuthController` | `/login`, `/register` | Auth forms |
| `HomeController` | `/` | Redirects to `/apps` |
| `InternshipApplicationController` | `/apps/**` | Full CRUD + import/discard |
| `AccountController` | `/account/**` | Email, account deletion |
| `EmailVerificationController` | `/account/verify-email/**` | Token-based verification |
| `PasswordResetController` | `/forgot-password`, `/reset-password` | Password reset flow |
| `PreferencesController` | `/preferences` | Theme/preferences |
| `ExportController` | `/export`, `/export/apps.csv` | CSV download |

---

## Services at a Glance

| Service | Responsibility |
|---|---|
| `UserService` | Register, password check, email management, delete account |
| `InternshipApplicationService` | CRUD, soft-delete, restore, filtering |
| `VerificationService` | Generate/validate one-time tokens |
| `EmailService` | Send SMTP emails |
| `GuestApplicationStore` | Session-based guest app storage |
| `EmailGateService` | Check if email-gated features are accessible |
| `DbUserDetailsService` | Spring Security integration |
| `LoginSuccessHandler` | Migrate guest apps to DB after login |

---

## Environment Variables (Required in Prod)

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC connection string |
| `DB_USER` | DB username |
| `DB_PASS` | DB password |
| `MAIL_USER` | SMTP username |
| `MAIL_PASS` | SMTP password |
| `APP_BASE_URL` | Public URL for email token links |
| `MAIL_FROM` | Sender address in emails |
| `DEMO_SEED_ENABLED` | (optional) seed demo user on startup |
| `DEMO_PASSWORD` | (optional) demo account password |

---

## Deployment

**Script:** `deploy.private.sh`

Steps it runs:
1. `./mvnw clean package -DskipTests` — builds fat JAR
2. SCP the JAR to `ubuntu@129.80.110.249:/opt/interntrack/app.jar`
3. `systemctl restart interntrack.service` — restarts the app
4. Health-check loop hitting the live URL (60s timeout)

The app runs as a systemd service on an Oracle Cloud Linux VM behind a reverse proxy.

---

## Frontend Notes

- No JavaScript framework — vanilla JS for minor interactivity (modals, etc.)
- CSS is split by page: `layout.css`, `theme.css`, `navbar.css`, `auth.css`, `apps-list.css`, etc.
- Light/Dark mode is toggled by a `data-theme` attribute on `<html>` driven by user preferences
- Thymeleaf fragments (`fragments/`) are reused for `<head>`, navbar, breadcrumb, logout modal

---

## Future Plans (Notes)

- UI overhaul: move toward a minimalist, less boxy design
- Personal website at `tirsansapkota.com` as a separate project
  - Will link to this app but live in its own codebase
  - No shared code — connection is just a hyperlink or embed
