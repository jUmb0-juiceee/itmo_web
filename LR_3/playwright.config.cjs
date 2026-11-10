const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
    testDir: './tests/browser',
    workers: 1,
    timeout: 30000,
    use: { baseURL: 'http://127.0.0.1:5500', channel: 'chrome', headless: true },
    webServer: {
        command: 'python -m http.server 5500 --bind 127.0.0.1',
        url: 'http://127.0.0.1:5500/login.html',
        reuseExistingServer: false
    }
});
