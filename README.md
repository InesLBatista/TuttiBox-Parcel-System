# Hw1-Tqs - Inês Batista, 124877

## Requirements

- Java 21
- Docker and Docker Compose for tests using Testcontainers and for running local services.
- Playwright Chromium for E2E tests (installation command explained below).

All Maven commands must be executed from the `hw1/` directory, using the Maven Wrapper included in the project.

## Running the Application

Start the PostgreSQL database defined in `hw1/compose.yaml`. By default, the container exposes port `5433` on the host machine to avoid conflicts with an existing local PostgreSQL installation using port `5432`. The application is configured to use this port.

```bash
cd hw1
docker compose up -d postgres
./mvnw spring-boot:run
```

The application will be available at <http://localhost:8080>.

To stop the database:

```bash
docker compose stop postgres
```

## Swagger / OpenAPI

With the application running, interactive documentation for the REST endpoints is available through Swagger UI:

<http://localhost:8080/swagger-ui.html>

The OpenAPI specification is also available in JSON at:

<http://localhost:8080/v3/api-docs>

And in YAML at:

<http://localhost:8080/v3/api-docs.yaml>

Swagger UI allows you to explore endpoints, inspect parameters and data models, and execute requests directly from the browser.

The application does not implement authentication, so Swagger UI should only be used in a local development environment.

If a different host port is required, set `POSTGRES_HOST_PORT` when starting Docker Compose and adjust `SPRING_DATASOURCE_URL` accordingly when starting the application.

For example, to use port `5432`:

```bash
POSTGRES_HOST_PORT=5432 docker compose up -d postgres

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tuttibox \
./mvnw spring-boot:run
```

## Testing

Tests that start PostgreSQL using Testcontainers require Docker to be running.

To execute the complete test suite, including Cucumber, API contracts, integration tests, Playwright, and the minimum coverage verification, run:

```bash
cd hw1
./mvnw clean verify
```

### Running Individual Test Suites

The following commands allow each test group to be executed independently:

**Unit tests** (excluding integration, Cucumber, contract, and browser tests):

```bash
./mvnw -Dtest='**/*Test.java,!**/*IntegrationTest.java,!**/CucumberTest.java,!**/ApiContractTest.java,!**/PlaywrightEndToEndTest.java,!**/WebInterfaceUsabilityTest.java' test
```

**Integration and concurrency tests:**

```bash
./mvnw '-Dtest=**/*IntegrationTest.java,**/*AcceptanceTest.java' test
```

**API contract tests:**

```bash
./mvnw -Dtest=ApiContractTest test
```

**BDD tests with Cucumber:**

```bash
./mvnw -Dtest=CucumberTest test
```

**Browser-based E2E tests** (install Chromium once per machine):

```bash
./mvnw -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args='install --with-deps chromium' \
  -Dexec.classpathScope=test exec:java

./mvnw -Dtest=PlaywrightEndToEndTest test
```

**UI usability tests without a browser:**

```bash
./mvnw -Dtest=WebInterfaceUsabilityTest test
```

Test suite classes and directories are located in:

`hw1/src/test/java/tuttibox/hw1/`

Cucumber scenarios are located in:

`hw1/src/test/resources/features/`

## Load Testing with k6

The load testing scenario is defined in:

`hw1/performance/k6/tuttibox.js`

It combines requests to the five HTML pages, station searches, parcel registration and retrieval, and operator readings.

The scenario uses an in-memory geocoder and demonstration adapters. Therefore, it does not send requests to Nominatim or depend on physical hardware.

Install k6 on your machine and verify the installation:

```bash
k6 version
```

The test runs against a disposable database to avoid mixing load test records with shared local data.

The following commands start PostgreSQL on a dedicated port, the application, and the load test in three separate terminals.

**Terminal 1 — from `hw1/`:**

```bash
POSTGRES_HOST_PORT=55432 docker compose -p tuttibox-k6 up -d postgres
```

**Terminal 2 — from `hw1/`:**

```bash
TUTTIBOX_GEOCODING_PROVIDER=memory \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/tuttibox \
./mvnw spring-boot:run
```

**Terminal 3 — from `hw1/`:**

```bash
k6 run performance/k6/tuttibox.js
```

The test profile runs for approximately 55 seconds and reaches:

- Six virtual users in parcel-related workflows.
- Five station searches per second.
- Two page requests per second at peak load.

Each parcel workflow cancels the parcel at the end to release the locker compartment, but audit records are retained.

Do not run this scenario against a production environment or a shared database.

The final summary includes latency metrics, request rates, checks, errors, and dropped iterations.

Section 3.6 of the project report documents the load profile, observed results, and their limitations.

To stop the isolated database container:

```bash
docker compose -p tuttibox-k6 stop postgres
```

## Test and Coverage Reports

Generate the reports from the `hw1/` directory:

```bash
cd hw1
./mvnw clean verify
```

### JaCoCo Coverage Report

To open the complete JaCoCo HTML report in a Linux browser:

```bash
xdg-open target/site/jacoco/index.html
```

The XML report used by SonarQube is available at:

`hw1/target/site/jacoco/jacoco.xml`

### Test Reports

Surefire reports for individual test classes are generated in:

`hw1/target/surefire-reports/`

Detailed Cucumber output is displayed in the terminal. The corresponding summary is available at:

`hw1/target/surefire-reports/TEST-tuttibox.hw1.bdd.CucumberTest.xml`

### CI Reports

In the CI pipeline, the `surefire-reports` and `jacoco-coverage` artifacts are available on the corresponding GitHub Actions workflow run page.

## Local SonarQube

The project includes the Maven scanner and an optional Docker Compose configuration for SonarQube Community Build.

Open a terminal in the `hw1/` directory and start the SonarQube server:

```bash
cd hw1
docker compose --profile sonar up -d sonarqube
```

Once the container has started, open:

<http://localhost:9000>

On the first login, use the default credentials:

- Username: `admin`
- Password: `admin`

You will be prompted to change the password.

In the SonarQube interface, generate an authentication token under:

**My Account → Security → Generate Tokens**

From the `hw1/` directory, generate the coverage report and run the SonarQube analysis:

```bash
./mvnw clean verify
./mvnw sonar:sonar -Dsonar.token=YOUR_TOKEN_HERE
```

Alternatively, to avoid exposing the token in your shell history, define `SONAR_TOKEN` as an environment variable and run:

```bash
./mvnw sonar:sonar
```

After the analysis completes successfully, open the project dashboard:

<http://localhost:9000/dashboard?id=tuttibox-hw1>

The dashboard provides an overview of:

- Bugs
- Vulnerabilities
- Code smells
- Code quality metrics
- Test coverage imported from JaCoCo

To stop the SonarQube server:

```bash
docker compose --profile sonar stop sonarqube
```

Data is persisted in the `sonarqube-data` Docker volume. To start the server again, simply repeat the `up -d` command.

The initial startup may take a few minutes.

This configuration is intended for local development and code quality analysis.

## Playwright

E2E tests use Playwright Java with Chromium and Testcontainers with PostgreSQL.

The browser must be installed locally using the command provided in the Testing section.

In the CI pipeline, the GitHub Actions workflow automatically installs Chromium before executing `clean verify`.
