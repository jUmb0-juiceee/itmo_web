// ==========================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// ==========================================

// Чтение параметров из URL (например, ?isu=123456)
function getUrlParam(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

// Красивое извлечение текста ошибки из ответа сервера
function extractErrorMessage(errorData) {
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
    
    return 'Неизвестная ошибка сервера';
}

// ==========================================
// ГЛОБАЛЬНОЕ СОСТОЯНИЕ
// ==========================================

let currentPage = 1;
const itemsPerPage = 20;
let currentFilters = {};
let currentSearch = '';
let currentUseQuery = false;

// ==========================================
// ОТРИСОВКА
// ==========================================

function renderStudents(students) {
    const tbody = document.getElementById('students-table-body');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    if (students.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #777;">Список студентов пуст.</td></tr>';
        return;
    }

    students.forEach(student => {
        const dateStr = student.date ? new Date(student.date).toLocaleDateString('ru-RU') : '-';
        const foreignerStr = student.isForeigner ? 'Да' : 'Нет';
        const dormStr = student.dorm ? student.dorm : '-';
        const roomStr = student.room ? student.room : '-';

        // ЭКРАНИРОВАНИЕ: защищаем все пользовательские данные от XSS
        const safeFio = escapeHtml(student.fio);
        const safeGroup = escapeHtml(student.group);
        const safeDorm = escapeHtml(dormStr);
        const safeRoom = escapeHtml(roomStr);
        const safeDate = escapeHtml(dateStr);
        const safeForeigner = escapeHtml(foreignerStr);

        const row = `
            <tr>
                <td>${safeFio}</td>
                <td>${safeGroup}</td>
                <td>${student.isu}</td>
                <td>${safeDorm}</td>
                <td>${safeRoom}</td>
                <td>${safeDate}</td>
                <td>${safeForeigner}</td>
                <td class="actions-cell">
                    <button class="action-btn btn-view" title="Посмотреть досье" data-action="view" data-isu="${student.isu}">👁️</button>
                    <button class="action-btn btn-edit" title="Редактировать" data-action="edit" data-isu="${student.isu}">✏️</button>
                    <button class="action-btn btn-delete" title="Удалить" data-action="delete" data-isu="${student.isu}">🗑️</button>
                </td>
            </tr>
        `;
        tbody.innerHTML += row;
    });
}

function renderDossier(student) {
    const contentDiv = document.getElementById('dossier-content');
    if (!contentDiv) return;

    const dateStr = student.date ? new Date(student.date).toLocaleDateString('ru-RU') : 'Не указано';
    const foreignerStr = student.isForeigner ? 'Да' : 'Нет';
    const dormStr = student.dorm ? `Общежитие №${student.dorm}, комната ${student.room}` : 'Не проживает в общежитии';
    const notesStr = student.notes ? student.notes : 'Заметок нет';

    // ЭКРАНИРОВАНИЕ: защищаем все пользовательские данные от XSS
    const safeFio = escapeHtml(student.fio);
    const safeGroup = escapeHtml(student.group);
    const safeDorm = escapeHtml(dormStr);
    const safeDate = escapeHtml(dateStr);
    const safeForeigner = escapeHtml(foreignerStr);
    const safeNotes = escapeHtml(notesStr);

    contentDiv.innerHTML = `
        <h3>${safeFio}</h3>
        <div class="dossier-row">
            <span class="dossier-label">Группа:</span>
            <span class="dossier-value">${safeGroup}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">ИСУ ID:</span>
            <span class="dossier-value">${student.isu}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Проживание:</span>
            <span class="dossier-value">${safeDorm}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Срок заселения:</span>
            <span class="dossier-value">${safeDate}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Иностранный студент:</span>
            <span class="dossier-value">${safeForeigner}</span>
        </div>
        <div class="dossier-row" style="flex-direction: column; border: none; margin-top: 15px;">
            <span class="dossier-label" style="width: 100%; margin-bottom: 5px;">Заметки:</span>
            <span class="dossier-value" style="background: #f9f9f9; padding: 10px; border-radius: 4px; display: block;">${safeNotes}</span>
        </div>
    `;
}

function renderPagination(pagination, loadPage) {
    const paginationDiv = document.getElementById('pagination-controls');
    if (!paginationDiv) return;
    
    paginationDiv.innerHTML = '';
    
    if (pagination.total_pages <= 1) return;
    
    // Кнопка "Назад"
    if (pagination.has_prev) {
        const prevBtn = document.createElement('button');
        prevBtn.textContent = '← Назад';
        prevBtn.className = 'pagination-btn';
        prevBtn.addEventListener('click', () => loadPage(pagination.page - 1));
        paginationDiv.appendChild(prevBtn);
    }
    
    // Информация о странице
    const pageInfo = document.createElement('span');
    pageInfo.className = 'pagination-info';
    pageInfo.textContent = `Страница ${pagination.page} из ${pagination.total_pages} (всего: ${pagination.total})`;
    paginationDiv.appendChild(pageInfo);
    
    // Кнопка "Вперёд"
    if (pagination.has_next) {
        const nextBtn = document.createElement('button');
        nextBtn.textContent = 'Вперёд →';
        nextBtn.className = 'pagination-btn';
        nextBtn.addEventListener('click', () => loadPage(pagination.page + 1));
        paginationDiv.appendChild(nextBtn);
    }
}

// ==========================================
// ЗАГРУЗКА ДАННЫХ
// ==========================================

async function loadStudents(filters = {}, page = 1, search = currentSearch) {
    const tbody = document.getElementById('students-table-body');
    if (!tbody) return;
    
    currentFilters = filters;
    currentSearch = search;
    currentUseQuery = false;
    currentPage = page;
    
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center;">Загрузка...</td></tr>';
    
    try {
        const result = await fetchStudents(filters, page, itemsPerPage, search);
        currentPage = result.pagination.page;
        
        renderStudents(result.data);
        renderPagination(result.pagination, (targetPage) => loadStudents(filters, targetPage, search));
    } catch (error) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="error-message">
                    <div class="error-icon">⚠️</div>
                    <div class="error-text">${error.message}</div>
                    <button class="retry-btn" onclick="reloadCurrentList()">Повторить попытку</button>
                </td>
            </tr>
        `;
    }
}

async function loadStudentsViaQuery(filters, search = currentSearch, page = 1) {
    const tbody = document.getElementById('students-table-body');
    if (!tbody) return;
    
    currentFilters = filters;
    currentSearch = search;
    currentUseQuery = true;
    currentPage = page;
    
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center;">Загрузка...</td></tr>';
    try {
        const response = await fetch(`${API_BASE_URL}/requests`, {
            method: 'QUERY', 
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                ...filters,
                search: search,
                page: page,
                limit: itemsPerPage
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(extractErrorMessage(errorData));
        }
        
        const result = await response.json();
        currentPage = result.pagination.page;
        
        renderStudents(result.data);
        renderPagination(result.pagination, (targetPage) => loadStudentsViaQuery(filters, search, targetPage));
    } catch (error) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="error-message">
                    <div class="error-icon">⚠️</div>
                    <div class="error-text">${error.message}</div>
                    <button class="retry-btn" onclick="reloadCurrentList()">Повторить попытку</button>
                </td>
            </tr>
        `;
    }
}

// Перезагружает текущий список тем же способом, которым он был получен
function reloadCurrentList() {
    if (currentUseQuery) {
        loadStudentsViaQuery(currentFilters, currentSearch, currentPage);
    } else {
        loadStudents(currentFilters, currentPage, currentSearch);
    }
}

async function loadStudentForEdit(isu) {
    try {
        const student = await fetchStudent(isu);
        if (!student) {
            alert('Студент не найден');
            window.location.href = 'index.html';
            return;
        }
        
        document.getElementById('fio').value = student.fio;
        document.getElementById('group').value = student.group;
        document.getElementById('isu').value = student.isu;
        document.getElementById('dorm').value = student.dorm || '';
        document.getElementById('room').value = student.room || '';
        document.getElementById('date').value = student.date || '';
        document.getElementById('foreigner').checked = student.isForeigner;
        document.getElementById('notes').value = student.notes || '';
        
        document.getElementById('btn-save').textContent = 'Обновить данные';
    } catch (error) {
        alert('Ошибка загрузки: ' + error.message);
        window.location.href = 'index.html';
    }
}

async function loadDossier(isu) {
    const contentDiv = document.getElementById('dossier-content');
    if (!contentDiv) return;
    
    contentDiv.innerHTML = '<p>Загрузка...</p>';
    try {
        const student = await fetchStudent(isu);
        if (!student) {
            contentDiv.innerHTML = '<p>Студент не найден</p>';
            return;
        }
        renderDossier(student);
    } catch (error) {
        contentDiv.innerHTML = `<p style="color:red;">Ошибка: ${error.message}</p>`;
    }
}

// ==========================================
// ИНИЦИАЛИЗАЦИЯ ПРИ ЗАГРУЗКЕ СТРАНИЦЫ
// ==========================================

document.addEventListener('DOMContentLoaded', () => {
    const currentIsu = getUrlParam('isu');

    // 1. Мы на главной странице (index.html)
    if (document.getElementById('students-table-body')) {
        loadStudents({}, 1, '');
        initSearchHandlers();
    }
    // 2. Мы на странице формы (form.html)
    else if (document.getElementById('student-form')) {
        if (currentIsu) {
            loadStudentForEdit(currentIsu);
        } else {
            document.getElementById('btn-save').textContent = 'Сохранить';
        }
    }
    // 3. Мы на странице досье (dossier.html)
    else if (document.getElementById('dossier-content')) {
        if (currentIsu) {
            loadDossier(currentIsu);
        }
    }
});

// ==========================================
// ОБРАБОТЧИКИ СТРОКИ ПОИСКА
// ==========================================

function initSearchHandlers() {
    const searchInput = document.getElementById('search-input');
    const searchClear = document.getElementById('search-clear');
    
    if (!searchInput) return;
    
    let searchTimeout;
    
    searchInput.addEventListener('input', (e) => {
        const value = e.target.value;
        
        // Показываем/скрываем кнопку очистки
        if (searchClear) {
            searchClear.style.display = value ? 'block' : 'none';
        }
        
        // Debounce: ждём 300мс после последнего ввода перед запросом
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            loadStudents(currentFilters, 1, value); // Сбрасываем на первую страницу
        }, 300);
    });
    
    if (searchClear) {
        searchClear.addEventListener('click', () => {
            searchInput.value = '';
            searchClear.style.display = 'none';
            loadStudents(currentFilters, 1, '');
        });
    }
}

// ==========================================
// ОБРАБОТЧИКИ СОБЫТИЙ (только если элементы есть на странице)
// ==========================================

// --- Обработчики для index.html ---
const btnAdd = document.getElementById('btn-add');
if (btnAdd) {
    btnAdd.addEventListener('click', () => {
        window.location.href = 'form.html';
    });
}

const tableBody = document.getElementById('students-table-body');
if (tableBody) {
    tableBody.addEventListener('click', (event) => {
        const button = event.target.closest('button');
        if (!button) return;

        const action = button.dataset.action;
        const isu = Number(button.dataset.isu);

        if (action === 'view') {
            window.location.href = `dossier.html?isu=${isu}`;
        } else if (action === 'edit') {
            window.location.href = `form.html?isu=${isu}`;
        } else if (action === 'delete') {
            // Показываем модальное окно
            const modal = document.getElementById('delete-modal');
            if (!modal) return;
            
            modal.style.display = 'flex';
            
            const confirmBtn = document.getElementById('modal-confirm');
            const cancelBtn = document.getElementById('modal-cancel');
            
            // Удаляем старые обработчики (если есть)
            const newConfirmBtn = confirmBtn.cloneNode(true);
            const newCancelBtn = cancelBtn.cloneNode(true);
            confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
            cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
            
            // Добавляем новые обработчики
            newConfirmBtn.addEventListener('click', async () => {
                modal.style.display = 'none';
                try {
                    await deleteStudent(isu);
                    reloadCurrentList();
                } catch (error) {
                    alert(`Ошибка: ${error.message}`);
                }
            });
            
            newCancelBtn.addEventListener('click', () => {
                modal.style.display = 'none';
            });
            
            // Закрытие по клику вне модального окна
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.style.display = 'none';
                }
            });
        }
    });
}

const btnApplyFilter = document.getElementById('btn-apply-filter');
if (btnApplyFilter) {
    btnApplyFilter.addEventListener('click', () => {
        const group = document.getElementById('filter-group').value.trim();
        const dorm = document.getElementById('filter-dormitory').value;
        const room = document.getElementById('filter-room').value;
        const foreigner = document.getElementById('filter-foreigner').value;
        
        const filters = {};
        if (group) filters.group = group;
        if (dorm) filters.dorm = Number(dorm);
        if (room) filters.room = Number(room);
        if (foreigner !== '') filters.isForeigner = foreigner === 'true';
        
        const filterCount = Object.keys(filters).length;
        if (filterCount <= 2) {
            loadStudents(filters, 1, currentSearch); // Сбрасываем на первую страницу, сохраняя поиск
        } else {
            loadStudentsViaQuery(filters, currentSearch);
        }
    });
}

const btnClearFilter = document.getElementById('btn-clear-filter');
if (btnClearFilter) {
    btnClearFilter.addEventListener('click', () => {
        document.getElementById('filter-group').value = '';
        document.getElementById('filter-dormitory').value = '';
        document.getElementById('filter-room').value = '';
        document.getElementById('filter-foreigner').value = '';
        loadStudents({}, 1, currentSearch); // Сбрасываем фильтры, сохраняя поиск
    });
}

// --- Обработчики для form.html ---
const studentForm = document.getElementById('student-form');
if (studentForm) {
    studentForm.addEventListener('submit', async function(event) {
        event.preventDefault();

        const studentData = {
            isu: Number(document.getElementById('isu').value),
            fio: document.getElementById('fio').value.trim(),
            group: document.getElementById('group').value.trim(),
            dorm: document.getElementById('dorm').value ? Number(document.getElementById('dorm').value) : null,
            room: document.getElementById('room').value ? Number(document.getElementById('room').value) : null,
            date: document.getElementById('date').value || null,
            isForeigner: document.getElementById('foreigner').checked,
            notes: document.getElementById('notes').value.trim()
        };

        const currentIsu = getUrlParam('isu');

        try {
            if (currentIsu) {
                await updateStudent(currentIsu, studentData);
            } else {
                await createStudent(studentData);
            }
            
            // Успешно сохранено -> возвращаемся к списку
            window.location.href = 'index.html';
        } catch (error) {
            let errorMsg = error.message;
            if (error.response) {
                try {
                    const errorData = await error.response.json();
                    errorMsg = extractErrorMessage(errorData);
                } catch (e) {
                    // Если не получилось распарсить, оставляем как есть
                }
            }
            alert(`Ошибка: ${errorMsg}`);
        }
    });
}

const btnCancelForm = document.getElementById('btn-cancel-form');
if (btnCancelForm) {
    btnCancelForm.addEventListener('click', (e) => {
        if (btnCancelForm.tagName === 'BUTTON') {
            e.preventDefault();
            window.location.href = 'index.html';
        }
    });
}

// --- Обработчики для dossier.html ---
const btnCloseDossier = document.getElementById('btn-close-dossier');
if (btnCloseDossier) {
    btnCloseDossier.addEventListener('click', () => {
        window.location.href = 'index.html';
    });
}