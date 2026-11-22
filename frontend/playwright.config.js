// Playwright end-to-end tests for the storefront. They run against the RUNNING stack (`make up` first).
// Guide: docs/13-playwright-e2e.md
import { defineConfig, devices } from '@playwright/test';
import { APP_URL } from './e2e/support/env.js';

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e-results/test-results',
  // The checkout saga needs a few seconds per order; one test may wait up to this long.
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  workers: process.env.CI ? 1 : 2,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { outputFolder: './e2e-results/report', open: 'never' }]],
  use: {
    baseURL: APP_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    // 1. Checks the stack is reachable and signs in once per role, saving each session to e2e/.auth/.
    { name: 'setup', testMatch: /.*\.setup\.js/ },
    // 2. The tests themselves, which reuse the saved sessions. 1400 px wide shows the desktop navigation bar.
    {
      name: 'chromium',
      testMatch: /.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 1000 } },
      dependencies: ['setup'],
    },
  ],
});
