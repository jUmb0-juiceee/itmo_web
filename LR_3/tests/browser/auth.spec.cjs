const { test, expect } = require('@playwright/test');
const api = process.env.E2E_API_URL || 'http://127.0.0.1:18083/api';
const key = 'students.session.v1';
let sequence = 0;
const password = 'browser-test-password';

test.beforeEach(async ({ page }) => {
    await page.route('**/config.js', route => route.fulfill({
        contentType: 'application/javascript', body: `window.APP_CONFIG={apiBaseUrl:${JSON.stringify(api)}};`
    }));
});

async function reader(request) {
    const username = `e2e_reader_${Date.now()}_${sequence++}`;
    const response = await request.post(api + '/auth/register', { data: { username, email: username + '@example.org', password } });
    expect(response.status()).toBe(201);
    return username;
}
async function login(page, username, secret = password) {
    await page.goto('/login.html');
    await page.getByLabel('Имя пользователя:').fill(username);
    await page.getByLabel('Пароль:', { exact: true }).fill(secret);
    await page.locator('#auth-submit').click();
    await expect(page).toHaveURL(/index\.html$/);
    await expect(page.locator('#session-bar')).toContainText(username);
    await expect(page.locator('html')).not.toHaveClass('auth-pending');
}

test('registration, read-only role, direct form protection and API 403', async ({ page }) => {
    const username = `e2e_reader_${Date.now()}_${sequence++}`;
    await page.goto('/index.html');
    await expect(page).toHaveURL(/login\.html/);
    await page.locator('#auth-toggle').click();
    await page.getByLabel('Имя пользователя:').fill(username);
    await page.getByLabel('Электронная почта:').fill(username + '@example.org');
    await page.getByLabel('Пароль:', { exact: true }).fill(password);
    await page.locator('#auth-submit').click();
    await expect(page).toHaveURL(/index\.html$/);
    await expect(page.locator('#session-bar')).toContainText('Просмотр');
    await expect(page.locator('#btn-add')).toBeHidden();
    let refreshes = 0;
    page.on('request', req => { if (req.url().endsWith('/auth/refresh')) refreshes++; });
    const denied = await page.evaluate(async () => {
        const response = await Auth.fetch('/requests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
        return { status: response.status, body: await response.json() };
    });
    expect(denied.status).toBe(403);
    expect(denied.body.error).toBe('Недостаточно прав');
    expect(refreshes).toBe(0);
    await page.goto('/form.html');
    await expect(page).toHaveURL(/index\.html\?notice=read-only/);
    await expect(page.locator('#session-bar')).toContainText('только просматривать');
    await page.locator('#btn-logout').click();
    await expect(page).toHaveURL(/login\.html$/);
    expect(await page.evaluate(key => sessionStorage.getItem(key), key)).toBeNull();
});

test('admin creates, filters via QUERY, views, edits and deletes a student', async ({ page }) => {
    const failures = [];
    page.on('pageerror', error => failures.push(error.message));
    await login(page, process.env.E2E_ADMIN_USER || 'e2e-admin', process.env.E2E_ADMIN_PASSWORD || 'e2e-admin-password');
    await page.locator('#btn-add').click();
    await expect(page.locator('#student-form')).toBeVisible();
    await page.locator('#fio').fill('Иванов Иван Иванович');
    await page.locator('#group').fill('M3301');
    await page.locator('#isu').fill('887755');
    await page.locator('#dorm').fill('1');
    await page.locator('#room').fill('101');
    await page.locator('#date').fill('2026-09-01');
    await page.locator('#notes').fill('Браузерная заметка');
    await page.locator('#btn-save').click();
    await expect(page).toHaveURL(/index\.html$/);
    let row = page.locator('#students-table-body tr').filter({ hasText: '887755' });
    await expect(row).toBeVisible();
    await page.locator('#sort-order').selectOption('isu,desc');
    await page.locator('#filter-group').fill('M3301');
    await page.locator('#filter-dormitory').fill('1');
    await page.locator('#filter-room').fill('101');
    await Promise.all([
        page.waitForResponse(response => response.request().method() === 'QUERY' && response.status() === 200),
        page.locator('#btn-apply-filter').click()
    ]);
    await row.locator('[data-action=view]').click();
    await expect(page.locator('#dossier-content')).toContainText('Браузерная заметка');
    await page.locator('#btn-close-dossier').click();
    await page.locator('#students-table-body tr').filter({ hasText: '887755' }).locator('[data-action=edit]').click();
    await expect(page.locator('#notes')).toHaveValue('Браузерная заметка');
    await page.locator('#isu').fill('887756');
    await page.locator('#notes').fill('После редактирования');
    await page.locator('#btn-save').click();
    await expect(page).toHaveURL(/index\.html$/);
    row = page.locator('#students-table-body tr').filter({ hasText: '887756' });
    await expect(row).toBeVisible();
    await row.locator('[data-action=delete]').click();
    await page.locator('#modal-confirm').click();
    await expect(row).toHaveCount(0);
    expect(failures).toEqual([]);
});

test('expired access triggers one refresh for concurrent requests and survives a page reload', async ({ page, request }) => {
    await login(page, await reader(request));
    const original = await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)), key);
    let refreshes = 0;
    page.on('request', req => { if (req.url().endsWith('/auth/refresh')) refreshes++; });
    // E2E server is started with a 3-second access TTL, independently of production defaults.
    await page.waitForTimeout(3300);
    const statuses = await page.evaluate(async () => Promise.all([
        Auth.fetch('/requests').then(response => response.status),
        Auth.fetch('/auth/me').then(response => response.status)
    ]));
    expect(statuses).toEqual([200, 200]);
    expect(refreshes).toBe(1);
    const updated = await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)), key);
    expect(updated.refreshToken).not.toBe(original.refreshToken);
    const replay = await request.post(api + '/auth/refresh', { data: { refreshToken: original.refreshToken } });
    expect(replay.status()).toBe(401);
    await page.reload();
    await expect(page.locator('#session-bar')).toContainText('Просмотр');
});

test('revoked refresh clears the session and returns to login', async ({ page, request }) => {
    await login(page, await reader(request));
    const session = await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)), key);
    expect((await request.post(api + '/auth/logout', { data: { refreshToken: session.refreshToken } })).status()).toBe(204);
    await page.evaluate(key => {
        const value = JSON.parse(sessionStorage.getItem(key)); value.accessToken = 'invalid';
        sessionStorage.setItem(key, JSON.stringify(value));
    }, key);
    await page.reload();
    await expect(page).toHaveURL(/login\.html.*notice=expired/);
    expect(await page.evaluate(key => sessionStorage.getItem(key), key)).toBeNull();
});

test('temporary refresh network failure keeps credentials for a later retry', async ({ page, request }) => {
    await login(page, await reader(request));
    await page.waitForTimeout(3300);
    await page.route('**/api/auth/refresh', route => route.abort(), { times: 1 });
    const failed = await page.evaluate(async () => {
        try { await Auth.fetch('/requests'); return false; } catch { return true; }
    });
    expect(failed).toBe(true);
    expect(await page.evaluate(key => sessionStorage.getItem(key), key)).not.toBeNull();
    expect(await page.evaluate(async () => (await Auth.fetch('/requests')).status)).toBe(200);
    await expect(page).toHaveURL(/index\.html$/);
});

test('login page fits a narrow screen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/login.html');
    await expect(page.locator('#auth-form')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: 'test-results/login-mobile.png', fullPage: true });
});
