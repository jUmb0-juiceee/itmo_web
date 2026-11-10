// api.js
const API_BASE_URL = window.APP_CONFIG.apiBaseUrl;

// ==========================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// ==========================================

// Функция для экранирования HTML-сущностей (защита от XSS)
function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    
    const str = String(text);
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    
    return str.replace(/[&<>"']/g, char => map[char]);
}

// Универсальная функция для извлечения текста ошибки из JSON-ответа сервера
function extractErrorMessage(errorData) {
    if (!errorData) return 'Не удалось выполнить запрос.';
    if (typeof errorData === 'string') return errorData;
    
    if (errorData.details && typeof errorData.details === 'object') {
        const messages = [];
        for (const [field, message] of Object.entries(errorData.details)) {
            if (field === 'server') continue;
            if (Array.isArray(message)) messages.push(...message);
            else if (typeof message === 'string') messages.push(message);
        }
        if (messages.length > 0) return messages.join('\n');
    }
    
    if (errorData.error && typeof errorData.error === 'string') return errorData.error;
    
    if (typeof errorData === 'object' && !errorData.error) {
        const messages = [];
        for (const [field, message] of Object.entries(errorData)) {
            if (Array.isArray(message)) messages.push(...message);
            else if (typeof message === 'string') messages.push(message);
        }
        if (messages.length > 0) return messages.join('\n');
    }
    
    return 'Неизвестная ошибка сервера';
}

// Функция для определения типа ошибки и возврата понятного сообщения (сеть, таймаут и т.д.)
function getErrorMessage(error) {
    // Ошибка соединения с Java API.
    if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
        return 'Не удалось подключиться к серверу. Проверьте подключение к интернету или убедитесь, что бэкенд запущен.';
    }
    
    // Ошибка таймаута (если будете настраивать AbortController в будущем)
    if (error.name === 'AbortError') {
        return 'Превышено время ожидания ответа от сервера. Попробуйте позже.';
    }
    
    // Если ошибка уже отформатирована функцией extractErrorMessage
    if (error.message) {
        return error.message;
    }
    
    // Неизвестная ошибка
    return 'Произошла неизвестная ошибка. Попробуйте обновить страницу.';
}

// ==========================================
// API ФУНКЦИИ
// ==========================================

async function fetchStudents(filters = {}, page = 1, limit = 20, search = '', sort = '') {
    const params = new URLSearchParams();
    
    // Добавляем параметры пагинации
    params.append('page', page);
    params.append('limit', limit);
    if (sort) params.append('sort', sort);
    
    // Добавляем поиск
    if (search && search.trim()) {
        params.append('search', search.trim());
    }
    
    // Добавляем фильтры
    for (const [key, value] of Object.entries(filters)) {
        if (value !== null && value !== undefined && value !== '') {
            params.append(key, value);
        }
    }
    
    try {
        const response = await Auth.fetch(`/requests?${params.toString()}`);
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        return await response.json();
    } catch (error) {
        throw new Error(getErrorMessage(error));
    }
}

async function fetchStudent(isu) {
    try {
        const response = await Auth.fetch(`/requests/${isu}`);
        
        if (response.status === 404) return null;
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        return await response.json();
    } catch (error) {
        throw new Error(getErrorMessage(error));
    }
}

async function createStudent(studentData) {
    try {
        const response = await Auth.fetch('/requests', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(studentData)
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        return await response.json();
    } catch (error) {
        throw new Error(getErrorMessage(error));
    }
}

async function updateStudent(isu, studentData) {
    try {
        const response = await Auth.fetch(`/requests/${isu}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(studentData)
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        return await response.json();
    } catch (error) {
        throw new Error(getErrorMessage(error));
    }
}

async function deleteStudent(isu) {
    try {
        const response = await Auth.fetch(`/requests/${isu}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        return true;
    } catch (error) {
        throw new Error(getErrorMessage(error));
    }
}
