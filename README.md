# trade-imports-ins-backend

Aggregates notification events into a cross-journey read model for the Import Notification
Service (INS). A Java 25 / Spring Boot 3.5 service backed by MongoDB. It reads notification events
from an SQS FIFO queue, keeps one summary per notification in the `notifications` collection, and
serves them through a read-only REST API.

[docs/architecture.md](docs/architecture.md) explains why the code is shaped as it is.

* [API](#api)
* [Configuration](#configuration)
* [Running](#running)
* [MongoDB](#mongodb)
* [Testing](#testing)
* [SonarCloud](#sonarcloud)
* [Dependabot](#dependabot)

### API

| Method & path | Purpose |
|---|---|
| `GET /notifications` | A page of the notifications owned by the organisation in the `Trade-Imports-Organisation-Id` header. Takes `page` (from 1), `sort` (`arrivalDate,desc` by default, or `arrivalDate,asc`, `lastUpdated,desc`, `lastUpdated,asc`) and `referenceNumber` (exact match). A missing or blank header is a `400` |
| `GET /address-lookup` | Searches the DEFRA address lookup by `postcode` or by free text (`find`). Only in the `dev` and `local` profiles |

The OpenAPI spec (`/v3/api-docs`) and the Swagger UI (`/swagger-ui.html`) are served in the
`local` profile only. Health check: `GET /health`.

### Configuration

The Spring profile comes from `ENVIRONMENT`, which defaults to `local`.

| Variable | Description |
|---|---|
| `PORT` | HTTP port (default: `8090`) |
| `MONGO_URI` | MongoDB connection string (default: `mongodb://localhost:27017/`) |
| `MONGO_DATABASE` | Database name (default: `trade-imports-ins-backend`) |
| `NOTIFICATION_SQS_QUEUE_URL` | URL of the SQS FIFO queue the service reads notification events from |
| `SQS_WAIT_TIME_SECONDS` | SQS long-poll wait time (default: `10`) |
| `SQS_MAX_MESSAGES` | Maximum concurrent SQS messages (default: `10`) |
| `NOTIFICATION_LIST_PAGE_SIZE` | Notifications in a page of `GET /notifications` (default: `25`) |
| `APP_AWS_ENDPOINT_OVERRIDE` | Floci endpoint for local development (leave unset in deployed environments) |
| `ADDRESS_LOOKUP_*` | Address lookup settings, read in the `dev` and `local` profiles only. See `application-local.yml` |

### Running

The workspace stack in
[DEFRA/trade-imports-workspace](https://github.com/DEFRA/trade-imports-workspace) runs this
service with everything it needs. From the workspace root:

```bash
tim docker up                    # full stack from published images
tim docker up -e ins-backend     # everything except this service (run it natively)
tim docker down                  # tear down and wipe volumes
```

The stack runs [floci/setup-ins-backend-pipeline.sh](floci/setup-ins-backend-pipeline.sh) to
create the SNS topic, the SQS queue and the subscription between them.

To run the service natively, start MongoDB and Floci (or the workspace stack with
`-e ins-backend`), then:

```bash
mvn spring-boot:run
```

The `local` profile points at Floci on `localhost:4566` and the
`trade_imports_animals_eu_notifications_ins_backend.fifo` queue. Address lookup in `local` calls
the trade-imports-stub simulators on `localhost:8087` and `localhost:8098`.

### MongoDB

The `notifications` collection uses the compound index `organisation_status_sort_fields`, with
`organisationId` as its leading field. On startup, `AggregatedNotificationIndexMaintenance`:

- drops the legacy `status_sort_fields` index when it is there, so existing databases can take the
  new index without a manual step
- deletes notifications with no `organisationId`, unless
  `notification.delete-missing-organisation-id-at-startup` is `false`

In CDP environments the platform supplies the MongoDB credentials as environment variables. Use
the CDP Terminal to inspect an environment's database. Locally, use `mongosh`.

### Testing

Run the unit tests:

```bash
mvn test
```

Run the full build, including the integration tests:

```bash
mvn clean verify
```

The integration tests run the whole Spring Boot application against MongoDB and Floci in
[Testcontainers](https://testcontainers.com/). Docker must be running.

### SonarCloud

SonarCloud configuration is in the GitHub Actions workflows.

### Dependabot

The Dependabot configuration is in [.github/dependabot.yml](.github/dependabot.yml).

### About the licence

The Open Government Licence (OGL) was developed by the Controller of Her Majesty's Stationery
Office (HMSO) to enable information providers in the public sector to license the use and re-use
of their information under a common open licence.

It is designed to encourage use and re-use of information freely and flexibly, with only a few
conditions.
