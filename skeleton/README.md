# {{ values.name }}

{{ values.description }}

Owned by: **{{ values.owner }}**

## Run locally

```bash
mvn spring-boot:run
```

Then check:

```bash
curl http://localhost:8080/hello
curl http://localhost:8080/actuator/health
```

## Run with Docker

```bash
docker build -t {{ values.name }} .
docker run -p 8080:8080 {{ values.name }}
```

## What's in this repo

- `src/` — minimal Spring Boot app with one `/hello` endpoint and Actuator health checks
- `Dockerfile` — multi-stage build for a small runtime image
- `.github/workflows/ci.yaml` — builds and tests on every push (swap for a Harness CI stage if your org uses Harness instead)
- `catalog-info.yaml` — registers this service in the IDP software catalog
