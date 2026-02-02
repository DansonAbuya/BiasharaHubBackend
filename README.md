# BiasharaHub Backend

Spring Boot REST API for the BiasharaHub multi-tenant SME commerce platform.

## Tech Stack

- **Java 17** / **Spring Boot 3.2**
- **PostgreSQL** with schema-per-tenant isolation
- **Liquibase** for database migrations
- **JWT** for authentication
- **Spring Security**

## Prerequisites

- Java 17+
- PostgreSQL 14+
- Maven 3.8+

## Setup

1. **Initialize the database** (creates `biasharahub` if it doesn't exist):
   - **Windows (CMD):** `scripts\init-database.bat`
   - **Windows (PowerShell):** `.\scripts\init-database.ps1`
   - **Linux/Mac:** `./scripts/init-database.sh`
   - **Maven:** `mvn validate -Pinit-db` or `mvn spring-boot:run -Pinit-db` (runs init before start)
   - **Manual:** `psql -U postgres -h localhost -f scripts/init-database.sql`

2. **Configure environment** (required if your PostgreSQL password is not `postgres`):
   ```bash
   # Windows (PowerShell)
   $env:DB_USERNAME="postgres"
   $env:DB_PASSWORD="your_postgres_password"

   # Windows (CMD)
   set DB_USERNAME=postgres
   set DB_PASSWORD=your_postgres_password

   # Linux/Mac
   export DB_USERNAME=postgres
   export DB_PASSWORD=your_postgres_password
   ```
   The app auto-creates the `biasharahub` database on startup if it doesn't exist. Set `DB_SKIP_BOOTSTRAP=true` to disable this.

3. **Add logo and favicon:**
   Copy `logo.png` and `favicon.png` to `src/main/resources/static/`
   (Uses BiasharaHub logo: dark green briefcase with bar chart, orange location pin)

4. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

5. **API base URL:** `http://localhost:8080/api`

## Multi-Tenancy (Schema per Tenant)

- Each tenant has an isolated PostgreSQL schema (e.g., `tenant_default`)
- Set `X-Tenant-ID` header with tenant UUID to switch tenant context
- Default tenant: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | Login (email + password) |
| POST | `/auth/verify-code` | Verify 2FA code |
| GET | `/products` | List products |
| GET | `/orders` | List orders (auth required) |
| GET | `/shipments` | List shipments (auth required) |
| GET | `/analytics` | Dashboard analytics (owner/staff) |
| GET | `/public/tenants/{id}/branding` | Tenant branding (logo, colors) |

## Demo Users (tenant_default)

| Email | Password | Role |
|-------|----------|------|
| admin@biashara.com | password123 | super_admin |
| owner@biashara.com | password123 | owner |
| staff@biashara.com | password123 | staff |
| customer@biashara.com | password123 | customer |

## Migrations (Liquibase)

Migrations are in `src/main/resources/db/changelog/`:

- **001**: `public.tenants` table
- **002**: `create_tenant_schema()` function
- **003**: Default tenant + schema creation
- **004**: Demo users seed

Liquibase runs automatically on application startup.

## License

Proprietary - BiasharaHub
