const { defineConfig } = require('@playwright/test');
const frontendPort = Number(process.env.E2E_FRONTEND_PORT || 15500);
const apiPort = Number(new URL(process.env.E2E_API_URL || 'http://127.0.0.1:18083/api').port || 80);

module.exports = defineConfig({
    testDir: './tests/browser',
    workers: 1,
    timeout: 30000,
    use: { baseURL: `http://127.0.0.1:${frontendPort}`, channel: 'chrome', headless: true },
    webServer: {
        command: `python -X utf8 scripts/static_server.py --port ${frontendPort} --api-port ${apiPort}`,
        url: `http://127.0.0.1:${frontendPort}/login.html`,
        reuseExistingServer: false
    }
});
