# xq-svc-exercice
[![CI](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/ci.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/ci.yml)

Spring Boot microservice and PostgreSQL database for a single-user exercise log book.



## Local development

Prerequisites: Java 21 and Docker with the Compose plugin.

```shell
./gradlew bootRun
```

Flyway applies the schema from `src/main/resources/db/migration`. The service uses PostgreSQL
through `compose.yaml`; set `POSTGRES_PORT=0` to let Docker select a free host port.

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
checks the packaged service health endpoint, and runs the JVM Test Kit E2E suite. Releases are
triggered by semantic-version tags such as `v1.0.0` or manually from GitHub Actions; the release
workflow publishes the GHCR image `ghcr.io/experiencequality/xq-svc-exercice` with an immutable
commit tag and registry-backed provenance and SBOM attestations.

To run the CI-equivalent gates locally:

```shell
./gradlew --no-daemon clean ci
docker compose up -d --wait
./gradlew --no-daemon e2e
docker compose down --volumes --remove-orphans
```
