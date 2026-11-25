import { defineConfig, devices } from '@playwright/test';

const port = process.env.PETCLINIC_PORT ?? '8080';
const baseURL = process.env.PETCLINIC_BASE_URL ?? `http://localhost:${port}`;

/**
 * UI tests for Spring PetClinic. By default the application is started with the Maven
 * wrapper (in-memory H2 sample data). Set PETCLINIC_BASE_URL to test an already running
 * instance instead (the web server is then not started).
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.PETCLINIC_BASE_URL
    ? undefined
    : {
        command: 'node start-app.js',
        url: `${baseURL}/owners/find`,
        reuseExistingServer: true,
        timeout: 240_000,
      },
});
