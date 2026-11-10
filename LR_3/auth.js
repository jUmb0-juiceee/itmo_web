/* Shared authentication for the three static pages. Passwords are never stored. */
window.Auth = (() => {
    const key = 'students.session.v1';
    const base = window.APP_CONFIG.apiBaseUrl.replace(/\/$/, '');
    let refreshPromise = null;
    let redirecting = false;
    let loggingOut = false;

    function session() {
        try {
            const value = JSON.parse(sessionStorage.getItem(key));
            return value && typeof value.accessToken === 'string' && value.accessToken.length
                && typeof value.refreshToken === 'string' && value.refreshToken.length ? value : null;
        } catch { return null; }
    }
    function save(value) { sessionStorage.setItem(key, JSON.stringify(value)); }
    function clear() { sessionStorage.removeItem(key); }
    function currentUser() { return session()?.user || null; }
    function isAdmin() { return currentUser()?.role === 'ADMIN'; }

    function message(data) {
        const details = data?.details && Object.values(data.details).filter(value => typeof value === 'string');
        return details?.length ? details.join('\n') : data?.error || 'Не удалось выполнить запрос.';
    }
    async function read(response) {
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) {
            const error = new Error(message(data));
            error.status = response.status;
            if (response.status === 429) error.message += ` Повторите через ${response.headers.get('Retry-After') || 'несколько'} сек.`;
            throw error;
        }
        return data;
    }
    async function publicPost(path, data) {
        return read(await fetch(base + path, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data), credentials: 'omit'
        }));
    }
    function nextPage(value) {
        return /^(index|form|dossier)\.html(?:\?isu=[0-9]+)?$/.test(value || '') ? value : 'index.html';
    }
    function goLogin() {
        if (redirecting || location.pathname.endsWith('/login.html')) return;
        redirecting = true;
        const filename = location.pathname.split('/').pop() || 'index.html';
        const next = nextPage(filename + location.search);
        location.replace('login.html?next=' + encodeURIComponent(next) + '&notice=expired');
    }
    async function refresh() {
        if (!refreshPromise) {
            refreshPromise = (async () => {
                const previous = session();
                if (!previous) throw Object.assign(new Error('Войдите в аккаунт.'), { status: 401 });
                const replacement = await publicPost('/auth/refresh', { refreshToken: previous.refreshToken });
                save(replacement);
            })().finally(() => { refreshPromise = null; });
        }
        return refreshPromise;
    }
    async function authorizedFetch(path, options = {}, retry = true) {
        if (loggingOut) throw new Error('Выполняется выход из аккаунта.');
        const previous = session();
        if (!previous) { goLogin(); throw Object.assign(new Error('Войдите в аккаунт.'), { status: 401 }); }
        const headers = new Headers(options.headers || {});
        headers.set('Authorization', 'Bearer ' + previous.accessToken);
        const response = await fetch(base + path, { ...options, headers, credentials: 'omit' });
        if (response.status !== 401) return response;
        if (loggingOut) throw new Error('Выполняется выход из аккаунта.');
        if (!retry) {
            clear(); goLogin();
            throw Object.assign(new Error('Сессия завершилась. Войдите снова.'), { status: 401 });
        }
        try {
            // A concurrent request may already have replaced the token that was sent.
            if (session()?.accessToken === previous.accessToken) await refresh();
            return await authorizedFetch(path, options, false);
        } catch (error) {
            if (error.status === 401) { clear(); goLogin(); }
            throw error;
        }
    }
    function sessionBar() {
        let bar = document.getElementById('session-bar');
        if (!bar) {
            bar = document.createElement('div');
            bar.id = 'session-bar'; bar.className = 'session-bar';
            document.querySelector('.site-header').after(bar);
        }
        return bar;
    }
    function renderSession() {
        const bar = sessionBar();
        bar.replaceChildren();
        const text = document.createElement('span');
        text.textContent = `${currentUser().username} · ${isAdmin() ? 'Администратор' : 'Просмотр'}`;
        const button = document.createElement('button');
        button.type = 'button'; button.className = 'btn-secondary'; button.textContent = 'Выйти';
        button.id = 'btn-logout'; button.addEventListener('click', logout);
        bar.append(text, button);
        document.querySelectorAll('[data-admin-only]').forEach(element => { element.hidden = !isAdmin(); });
    }
    async function requireSession({ admin = false } = {}) {
        if (!session()) { goLogin(); return false; }
        sessionBar().textContent = 'Проверяем сессию…';
        try {
            const user = await read(await authorizedFetch('/auth/me'));
            save({ ...session(), user });
            if (admin && !isAdmin()) { location.replace('index.html?notice=read-only'); return false; }
            renderSession();
            if (new URLSearchParams(location.search).get('notice') === 'read-only') {
                const notice = document.createElement('span');
                notice.textContent = 'Ваш аккаунт позволяет только просматривать студентов.';
                sessionBar().append(notice);
            }
            document.documentElement.classList.remove('auth-pending');
            return true;
        } catch (error) {
            if (error.status !== 401) {
                const bar = sessionBar();
                bar.textContent = error instanceof TypeError ? 'Не удалось подключиться к серверу.' : error.message;
                const retry = document.createElement('button');
                retry.textContent = 'Повторить'; retry.addEventListener('click', () => location.reload());
                bar.append(retry);
            }
            return false;
        }
    }
    async function login(username, password) { save(await publicPost('/auth/login', { username, password })); }
    async function register(username, email, password) { return publicPost('/auth/register', { username, email, password }); }
    async function logout() {
        if (loggingOut) return;
        loggingOut = true;
        document.getElementById('btn-logout')?.setAttribute('disabled', '');
        try {
            if (refreshPromise) await refreshPromise.catch(() => {});
            const active = session();
            if (active) await publicPost('/auth/logout', { refreshToken: active.refreshToken });
        } catch { /* Local credentials must still be removed if the server is unreachable. */ }
        finally { clear(); location.replace('login.html'); }
    }
    return { fetch: authorizedFetch, read, login, register, logout, requireSession, isAdmin, currentUser, nextPage };
})();
