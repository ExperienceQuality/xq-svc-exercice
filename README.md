# xq-svc-exercice
[![Build](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/build.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/build.yml)
[![Test](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/test.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/test.yml)
[![Publish](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/publish.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/publish.yml)
[![Deploy](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy.yml)
[![Deployment Smoke Test](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy-smoke.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy-smoke.yml)

Spring Boot microservice and PostgreSQL database for a single-user exercise log book.



## Local development

Prerequisites: Java 21 and Docker (for the GHCR `xq-db` Postgres image used by tests and CI).

```shell
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The `local` profile supplies localhost datasource defaults for database `xq`, user `exercise_app`,
and Hikari schema `exercise`. Point that profile at a running Postgres from the pinned
`xq-db` image in `src/main/resources/xq-db.properties`. Schema migrations are owned by `xq-db`;
this service does not run Flyway. Integration tests and CI start that same GHCR image via
Testcontainers / `docker run`.

## API

```text
POST /api/v1/exercise-logs
GET  /api/v1/exercise-logs/{exerciseLogId}
GET  /api/v1/exercise-logs?exerciseName=Bench%20Press&sort=highestSetVolume&limit=2
GET  /api/v1/exercise-logs/exercises
```

The service calculates each set's volume as `weightKg * reps` and ranks logs by their highest
single-set volume. The distinct-exercises endpoint is a read projection from `exercise_logs`;
there is no exercise catalog table yet.

## Verification

```shell
./gradlew test
```

The CI workflow runs unit and integration checks, packages the service JAR, starts PostgreSQL,
checks the packaged service health endpoint, and runs the JVM Test Kit E2E suite. The Publish
workflow builds and pushes the GHCR image `ghcr.io/experiencequality/xq-svc-exercice` after a
successful main-branch Test run, or when dispatched manually; it tags the image with an immutable
`sha-<commit>` tag and `latest`.

To run the CI-equivalent gates locally, run `./gradlew --no-daemon clean ci`, start the pinned
`xq-db` Postgres image (see `.github/workflows/test.yml`), start the packaged jar with the `e2e`
profile and datasource overrides, then `./gradlew --no-daemon e2e`.

## Render and Neon deployment

GitHub Actions runs separate Build, Test, Publish, and Deploy workflows. Publish builds a Linux
container image with Dockerfile and publishes it to GHCR after successful main-branch Test, or
when dispatched manually. Deploy then triggers an image-backed Render service with the immutable
`sha-<commit>` image tag. The image targets `linux/amd64` and uses bounded JVM memory for Render's
512 MB plan. Configure the
service health check as `/actuator/health/readiness`; the service binds to Render's `PORT` value
and uses production profile settings when `SPRING_PROFILES_ACTIVE=production`.

Create a Render workspace registry credential named `ghcr` with a GitHub token that has
`read:packages` permission. Attach it to the image-backed service. Render does not automatically
redeploy when a registry tag changes; the deploy hook in GitHub Actions triggers each image deploy.

Use one Neon project and its protected `main` branch only. Configure these Render environment
variables with the Neon `main` connection and runtime role; keep values in Render, never Git:

```text
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=<Neon main pooled connection URL>
SPRING_DATASOURCE_USERNAME=<runtime role>
SPRING_DATASOURCE_PASSWORD=<runtime role password>
DB_SCHEMA=exercise
```

Production uses a 30-second database connection timeout by default to tolerate Neon compute
cold start. Override `DB_CONNECTION_TIMEOUT_MS` in Render only when measured startup behavior
requires a different value.

Schema migrations are owned by `xq-db`. After cutover, Flyway will not run in this service;
do not rely on classpath migrations here for production schema changes.

Create GitHub environment `production`, restrict it to `main`, and set repository secret
`RENDER_SERVICE_URL` to the Render service URL. The deployment smoke workflow checks liveness,
readiness, and the read-only distinct-exercises endpoint. Run it only after Render deploys.

Create repository secret `RENDER_DEPLOY_HOOK_URL` from the Render service Settings → Deploy Hook.
The deploy workflow appends the immutable GHCR image tag to this hook. Keep the hook secret; it
can trigger production deploys. [Render deploy hooks](https://render.com/docs/deploy-hooks)

Build the deployment image locally with:

```shell
docker build -t xq-svc-exercise:test .
```
