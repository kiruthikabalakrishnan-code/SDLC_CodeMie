import { defineConfig, devices } from '@playwright/test';

// The application must already be running (for example `./mvnw spring-boot:run`).
// Override the target with E2E_BASE_URL if it is not on http://localhost:8080.
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
