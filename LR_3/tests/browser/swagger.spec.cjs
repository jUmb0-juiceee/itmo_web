const { test, expect } = require('@playwright/test');
const api = process.env.E2E_API_URL || 'http://127.0.0.1:18083/api';

test('Swagger displays the actual contract and executes a protected GET with Bearer', async ({ page, request }) => {
    const failures = [];
    page.on('pageerror', error => failures.push(error.message));
    const login = await request.post(api + '/auth/login', { data: {
        username: process.env.E2E_ADMIN_USER || 'e2e-admin',
        password: process.env.E2E_ADMIN_PASSWORD || 'e2e-admin-password'
    } });
    expect(login.status()).toBe(200);
    const tokens = await login.json();
    await page.goto(api.replace(/\/api$/, '') + '/swagger-ui.html');
    await expect(page.locator('.info .title')).toContainText('Лесной архив');
    await expect(page.locator('.opblock')).toHaveCount(13);
    await expect(page.locator('.info .description')).toContainText('QUERY');
    await page.screenshot({ path: 'test-results/swagger.png', fullPage: true });
    await page.locator('.auth-wrapper button.authorize').click();
    await page.locator('.modal-ux input').fill(tokens.accessToken);
    await page.locator('.modal-ux').getByRole('button', { name: 'Apply credentials', exact: true }).click();
    await page.locator('.modal-ux').getByRole('button', { name: 'Close', exact: true }).click();
    const list = page.locator('.opblock-get').filter({
        has: page.locator('.opblock-summary-path[data-path="/api/requests"]')
    });
    await list.locator('.opblock-summary').click();
    await list.getByRole('button', { name: 'Try it out', exact: true }).click();
    const pending = page.waitForResponse(response => response.request().method() === 'GET'
        && response.url().includes('/api/requests'));
    await list.getByRole('button', { name: 'Execute', exact: true }).click();
    const result = await pending;
    expect(result.status()).toBe(200);
    expect(result.request().headers().authorization).toBe('Bearer ' + tokens.accessToken);
    expect(failures).toEqual([]);
    expect((await request.post(api + '/auth/logout', { data: { refreshToken: tokens.refreshToken } })).status()).toBe(204);
});
