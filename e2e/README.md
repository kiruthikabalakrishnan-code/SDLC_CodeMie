# PetClinic UI tests (Playwright + TypeScript)

UI tests for EPMCDMETST-73769 (Find Owners by telephone). Scenarios mirror
`src/test/resources/features/owner-telephone-search.feature`.

## Run

Prerequisites: Node.js 18+, JDK 17 (JAVA_HOME set).

    cd e2e
    npm install
    npx playwright install chromium
    npm test

By default Playwright starts the application with the Maven wrapper (`start-app.js`,
in-memory H2 sample data, port 8080; override with `PETCLINIC_PORT`). To test an instance
that is already running, set `PETCLINIC_BASE_URL` (for example `http://localhost:8080`).
