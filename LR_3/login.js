(() => {
    let registering = false;
    const form = document.getElementById('auth-form');
    const toggle = document.getElementById('auth-toggle');
    const submit = document.getElementById('auth-submit');
    const error = document.getElementById('auth-error');
    const params = new URLSearchParams(location.search);
    const notice = document.getElementById('auth-notice');
    if (params.get('notice') === 'expired') {
        notice.textContent = 'Войдите в аккаунт, чтобы продолжить работу.'; notice.hidden = false;
    }
    toggle.addEventListener('click', () => {
        registering = !registering;
        document.getElementById('auth-title').textContent = registering ? 'Новый аккаунт' : 'Вход в архив';
        document.getElementById('auth-description').textContent = registering
            ? 'Создайте аккаунт для просмотра студентов.' : 'Войдите, чтобы открыть список студентов.';
        document.getElementById('email-group').hidden = !registering;
        document.getElementById('email').disabled = !registering;
        document.getElementById('email').required = registering;
        document.getElementById('password-hint').hidden = !registering;
        document.getElementById('password').autocomplete = registering ? 'new-password' : 'current-password';
        submit.textContent = registering ? 'Зарегистрироваться' : 'Войти';
        toggle.textContent = registering ? 'У меня есть аккаунт' : 'Создать аккаунт';
        error.hidden = true;
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        error.hidden = true;
        submit.disabled = true; toggle.disabled = true;
        let created = false;
        try {
            const username = form.username.value.trim();
            const password = form.password.value;
            if (registering) {
                if ([...password].length < 8 || new TextEncoder().encode(password).length > 72)
                    throw new Error('Пароль должен содержать минимум 8 символов и не превышать 72 байта UTF-8.');
                await Auth.register(username, form.email.value.trim(), password);
                created = true;
            }
            await Auth.login(username, password);
            form.password.value = '';
            let next = Auth.nextPage(params.get('next'));
            if (!Auth.isAdmin() && next.startsWith('form.html')) next = 'index.html';
            location.replace(next);
        } catch (failure) {
            const text = failure instanceof TypeError ? 'Не удалось подключиться к серверу. Повторите попытку позже.' : failure.message;
            error.textContent = (created ? 'Аккаунт создан. Переключитесь на вход. ' : '') + text;
            error.hidden = false;
        } finally { submit.disabled = false; toggle.disabled = false; }
    });
})();
