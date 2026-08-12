import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:5173'
const webServerCommand = process.env.PLAYWRIGHT_WEB_SERVER_COMMAND || 'pnpm dev --host 127.0.0.1'
const browserChannel = process.env.PLAYWRIGHT_CHANNEL
const reuseExistingServer =
  process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === undefined
    ? !process.env.CI
    : process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === 'true'

export default defineConfig({
  testDir: './e2e',
  outputDir: 'test-results',
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], channel: browserChannel },
    },
  ],
  webServer: {
    command: webServerCommand,
    url: baseURL,
    reuseExistingServer,
    timeout: 120000,
  },
})
