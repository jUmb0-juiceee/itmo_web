// api.js
const API_BASE_URL = 'http://127.0.0.1:8000/api';

// Универсальная функция для извлечения текста ошибки из ответа сервера
function extractErrorMessage(errorData) {
    // Если errorData - строка, возвращаем её
    if (typeof errorData === 'string') {
        return errorData;
    }
    
    // Если есть поле details с ошибками по полям
    if (errorData.details && typeof errorData.details === 'object') {
        const details = errorData.details;
        const messages = [];
        
        for (const [field, message] of Object.entries(details)) {
            // Пропускаем серверные ошибки
            if (field === 'server') continue;
            
            if (Array.isArray(message)) {
                messages.push(...message);
            } else if (typeof message === 'string') {
                messages.push(message);
            }
        }
        
        if (messages.length > 0) {
            return messages.join('\n');
        }
    }
    
    // Если есть поле error и оно строка
    if (errorData.error && typeof errorData.error === 'string') {
        return errorData.error;
    }
    
    // Если errorData - объект без поля error
    if (typeof errorData === 'object' && !errorData.error) {
        const messages = [];
        for (const [field, message] of Object.entries(errorData)) {
            if (Array.isArray(message)) {
                messages.push(...message);
            } else if (typeof message === 'string') {
                messages.push(message);
            }
        }
        if (messages.length > 0) {
            return messages.join('\n');
        }
    }
    
    // Если ничего не подошло
    return 'Неизвестная ошибка сервера';
}

async function fetchStudents(filters = {}) {
    const params = new URLSearchParams();
    
    // Используем правильные названия полей из LR_1
    if (filters.group) params.append('group', filters.group);
    if (filters.dorm) params.append('dorm', filters.dorm);
    if (filters.room) params.append('room', filters.room);
    if (filters.isForeigner !== undefined && filters.isForeigner !== null) {
        params.append('isForeigner', filters.isForeigner);
    }
    
    const url = params.toString() ? `${API_BASE_URL}/requests?${params.toString()}` : `${API_BASE_URL}/requests`;
    
    const response = await fetch(url);
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(extractErrorMessage(errorData));
    }
    return await response.json();
}

async function fetchStudent(isu) {
    const response = await fetch(`${API_BASE_URL}/requests/${isu}`);
    if (response.status === 404) return null;
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(extractErrorMessage(errorData));
    }
    return await response.json();
}

async function createStudent(studentData) {
    const response = await fetch(`${API_BASE_URL}/requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(studentData)
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(extractErrorMessage(errorData));
    }
    return await response.json();
}

async function updateStudent(isu, studentData) {
    const response = await fetch(`${API_BASE_URL}/requests/${isu}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(studentData)
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(extractErrorMessage(errorData));
    }
    return await response.json();
}

async function deleteStudent(isu) {
    const response = await fetch(`${API_BASE_URL}/requests/${isu}`, {
        method: 'DELETE'
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(extractErrorMessage(errorData));
    }
    return true;
}