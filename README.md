# Coffee Journal

A local Kotlin/JVM 21 backend for coffee lots and repeat purchases. Ktor 3.6.0, Kotlin 2.4.21, PostgreSQL 17, direct JDBC, HikariCP and Flyway. One roaster and coffee lot can have any number of purchases; a purchase never changes the coffee description or earlier purchases.

## Run locally

Install JDK 21, Docker with Compose, curl and Python 3. Start Docker, then from the repository:

```sh
cp .env.example .env
# Optionally uncomment overrides in .env; defaults are in application.conf.
set -a
. ./.env
set +a
docker compose up -d --wait
./gradlew check installDist
./build/install/coffee-journal/bin/coffee-journal
```

The server listens at `http://127.0.0.1:8080`. In another terminal:

```sh
./scripts/smoke.sh
```

Interactive API docs are available at [`http://127.0.0.1:8080/swagger`](http://127.0.0.1:8080/swagger). OpenAPI metadata and request/response schemas are generated from the Ktor routes and Kotlin serialization DTOs; operation descriptions live in KDoc comments beside each route.

The script creates a roaster, one mixed-origin lot and two purchases. It checks normalized replay, conflicting retry, preserved lot characteristics and history order. Each run creates separate demo records. Set `BASE_URL` when changing the port.

Stop the server with Ctrl-C. Restarting the same executable retains all PostgreSQL data. `docker compose stop` retains the named database volume; `docker compose down -v` deletes it. To verify persistence, use the `COFFEE_LOT_ID` printed by smoke after restarting:

```sh
curl -fsS "http://127.0.0.1:8080/api/coffee-lots/$COFFEE_LOT_ID/purchases"
```

## Configuration

Defaults live in [`src/main/resources/application.conf`](src/main/resources/application.conf), packaged with the application. Startup loads HOCON with Typesafe `ConfigFactory.load()` and validates the typed settings before opening the database. The existing environment variables override the corresponding file values through `${?ENV}` substitutions:

| HOCON property | Environment override | Default / supported values |
| --- | --- | --- |
| `ktor.deployment.host` | `HOST` | `127.0.0.1` only |
| `ktor.deployment.port` | `PORT` | `8080`, integer 1–65535 |
| `database.url` | `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/coffee_journal` |
| `database.user` | `DB_USER` | `coffee_journal` |
| `database.password` | `DB_PASSWORD` | `local-coffee-journal` (local development) |
| `journal.ownerId` | `OWNER_ID` | `00000000-0000-0000-0000-000000000001`, stable UUID |
| `database.poolSize` | `DB_POOL_SIZE` | `4`, range 1–4 |
| `database.parallelism` | `DB_PARALLELISM` | `4`, range 1–4 |

For example, run on another local port without changing the file:

```sh
PORT=9090 ./build/install/coffee-journal/bin/coffee-journal
```

The executable does not automatically load `.env`. `.env.example` contains commented examples of optional environment overrides; uncomment only the values you want and export them with `set -a; . ./.env; set +a` as shown above. Active overrides take precedence over the matching bundled file values. Compose reads `.env` for its database password. If you override `DB_PASSWORD`, export the same value for the application and recreate the database service with matching credentials; changing an existing PostgreSQL volume's password requires updating the database role too.

To use an external HOCON file without rebuilding, copy the complete configuration, edit it and pass the JVM property through the installed launcher's `JAVA_OPTS`:

```sh
cp src/main/resources/application.conf /tmp/coffee-journal.conf
# Edit /tmp/coffee-journal.conf.
JAVA_OPTS="-Dconfig.file=/tmp/coffee-journal.conf" \
  ./build/install/coffee-journal/bin/coffee-journal
```

`-Dconfig.file` replaces bundled `application.conf`, so the external file must provide all properties in the table. Keep its `${?ENV}` lines if you want the same environment overrides. Standard JVM system properties such as `-Dktor.deployment.port=9090` take priority over file settings. After editing the bundled resource itself, rebuild with `./gradlew installDist`.

Keep `OWNER_ID` constant across restarts to read the same collection. The server chooses the owner and rejects client owner fields. Connection acquisition and PostgreSQL statement timeouts are 5 seconds. Transactions stay on the bounded database executor and do not suspend internally. Pools and executor close when the application stops.

This slice is for local use. It has no authentication or registration, and loopback binding is enforced. Remote hosting, UI, photos, recipes, ratings and editing/archiving are future work.

## HTTP example

All endpoints use JSON. For readable shell examples, replace the shown IDs with the IDs from preceding responses.

```sh
curl -fsS http://127.0.0.1:8080/health
curl -fsS -H 'Content-Type: application/json' \
  -d '{"name":"Example Roaster"}' http://127.0.0.1:8080/api/roasters
curl -fsS -H 'Content-Type: application/json' \
  -d '{"roasterId":"ROASTER_UUID","name":"Blend","originComponents":[{"country":"Colombia"},{"region":"Sidama"}]}' \
  http://127.0.0.1:8080/api/coffee-lots
curl -fsS -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"purchasedOn":"2026-10-01","roastedOn":"2026-10-03","weightGrams":250,"price":{"amount":"12.50","currency":"EUR"}}' \
  http://127.0.0.1:8080/api/coffee-lots/LOT_UUID/purchases
curl -fsS http://127.0.0.1:8080/api/coffee-lots/LOT_UUID/purchases
```

`GET /api/roasters`, `GET /api/coffee-lots` and `GET /api/coffee-lots/{id}` read the personal catalog. Successful creates return 201; matching purchase replay returns 200 with the original record. Each genuine purchase needs a new UUID `Idempotency-Key`. Reusing a key with different normalized data or another accessible lot returns 409, including concurrent requests. History sorts by purchase date descending, then UUID ascending.

Optional lot text may be absent or null. Origins, varieties and descriptors default to empty lists; each list allows 50 entries. Text trims surrounding whitespace, must stay nonblank, contains no NUL and is limited to 200 characters (description: 4000). Roast profile is `FILTER`, `ESPRESSO` or `OMNI`. Dates use `YYYY-MM-DD`; roast date may be after purchase date for preorders. Weight is an optional positive 32-bit integer. Optional `price` requires both a decimal **string** `amount` (0–9999999999.99; at most two decimal places) and three uppercase currency letters. Stored money returns two decimal places.

Errors are `{"code":"...","message":"..."}`: 400 `INVALID_INPUT`, 404 `NOT_FOUND`, 409 `IDEMPOTENCY_CONFLICT`, 500 `INTERNAL_ERROR`, and 503 `DATABASE_UNAVAILABLE` for health. Missing and foreign IDs have the same 404. Invalid JSON, unknown fields, dates, UUIDs, enum values and numeric overflow return 400 without partial writes. SQL, credentials and stack traces are excluded from error responses and application diagnostics. Unknown routes and unsupported methods return JSON errors.

## Tests and build

```sh
./gradlew test                 # Unit tests: validation and configuration
./gradlew integrationTest      # Real PostgreSQL: SQL, transactions, ownership and HTTP
./gradlew check                # Both suites; fails if PostgreSQL cannot start
./gradlew installDist          # Runnable executable + dependencies
```

Integration tests normally start `postgres:17.6-alpine` with Testcontainers. They fail if Docker/PostgreSQL is unavailable and never silently skip or substitute another database. To use a dedicated external PostgreSQL instead:

```sh
TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/coffee_journal_test \
TEST_DB_USER=coffee_journal_test TEST_DB_PASSWORD=local-test \
./gradlew check
```

**Use a dedicated test database:** integration fixtures truncate catalog/purchase tables and run migrations. Never point these variables at the demo database or personal data. CI runs `check installDist` on a Linux runner with Docker.

If the default Gradle cache is unwritable, set `GRADLE_USER_HOME` to a writable directory. Explicitly select a JDK 21 `JAVA_HOME` if multiple Java versions are installed. The checked-in Gradle wrapper uses 8.14.1.
