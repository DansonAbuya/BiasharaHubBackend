# Jenkins Test stage – use UAT test DB and Redis

The Test stage runs `mvn test` with profile `test`. Tests start the full Spring context and need a **real PostgreSQL database** and **Redis** (same as UAT).

## Required: set environment variables in Jenkins

Configure the Jenkins **job** so the Test stage can reach your UAT test DB and Redis.

1. Open the job: **biasharahub-backend-uat** → **Configure**.
2. Under **Pipeline** or **Build Environment**, add **Environment variables** (exact name depends on your Jenkins/plugins):
   - If using **Environment Injector Plugin**: under "Inject environment variables", set **Properties Content** or a file with the variables below.
   - If your pipeline supports it, you can also define them in the job’s "Environment variables" list.

Use the **same values as your EB UAT environment** (RDS endpoint for DB, ElastiCache or Redis host for Redis):

| Variable        | Example / description |
|----------------|------------------------|
| `DB_URL`       | `jdbc:postgresql://your-uat-db.xxxx.eu-west-1.rds.amazonaws.com:5432/biasharahub_test` |
| `DB_USERNAME`  | UAT DB user |
| `DB_PASSWORD`  | UAT DB password (use Jenkins Credentials and reference by ID if possible) |
| `REDIS_HOST`   | UAT Redis host (e.g. ElastiCache endpoint) |
| `REDIS_PORT`   | `6379` (or your UAT Redis port) |

- **DB**: Use the test database you already created for the EB UAT environment (same RDS instance and DB name you set in EB env config).
- **Redis**: Use the same Redis instance you use for UAT (e.g. ElastiCache or managed Redis). Jenkins must be able to reach it (same VPC or allowed network).

## Security

- Prefer **Jenkins Credentials** for `DB_PASSWORD` (and optionally `DB_USERNAME` / `DB_URL`), then bind them in the pipeline with `withCredentials` and pass them into the `mvn test` step.
- Ensure Jenkins (or its agents) has **network access** to the UAT RDS and Redis (security groups / firewall).

## Summary

- No H2; tests use the **real UAT test DB** and **Redis**.
- Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` in the Jenkins job so `mvn test -Dspring.profiles.active=test` can connect.
