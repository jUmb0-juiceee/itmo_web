// main.js
const screenTable = document.getElementById('screen-table');
const screenForm = document.getElementById('screen-form');
const screenDossier = document.getElementById('screen-dossier');

const btnAdd = document.getElementById('btn-add');
const btnCancelForm = document.getElementById('btn-cancel-form');
const btnCloseDossier = document.getElementById('btn-close-dossier');
const studentForm = document.getElementById('student-form');

const filterGroupInput = document.getElementById('filter-group');
const btnApplyFilter = document.getElementById('btn-apply-filter');
const btnClearFilter = document.getElementById('btn-clear-filter');

let currentEditingIsu = null;

// === ФУНКЦИИ ПЕРЕКЛЮЧЕНИЯ ЭКРАНОВ ===

function showForm() {
    screenTable.style.display = 'none';
    screenDossier.style.display = 'none';
    screenForm.style.display = 'block';  
}

function showTable() {
    screenForm.style.display = 'none';   
    screenTable.style.display = 'block';
    screenDossier.style.display = 'none';
    loadStudents(); // Перезагружаем данные с сервера при возврате
}

function showDossierScreen() {
    screenTable.style.display = 'none';
    screenForm.style.display = 'none';
    screenDossier.style.display = 'block';
}

// === ОТРИСОВКА ТАБЛИЦЫ (вынесена в отдельную функцию) ===

function renderStudents(students) {
    const tbody = document.getElementById('students-table-body');
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

        const row = `
            <tr>
                <td>${student.fio}</td>
                <td>${student.group}</td>
                <td>${student.isu}</td>
                <td>${dormStr}</td>
                <td>${roomStr}</td>
                <td>${dateStr}</td>
                <td>${foreignerStr}</td>
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

// === ЗАГРУЗКА СТУДЕНТОВ (GET с query-параметрами) ===

async function loadStudents(filters = {}) {
    const tbody = document.getElementById('students-table-body');
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center;">Загрузка...</td></tr>';
    
    try {
        const students = await fetchStudents(filters);
        renderStudents(students);
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: red;">Ошибка: ${error.message}</td></tr>`;
    }
}

// === ЗАГРУЗКА ЧЕРЕЗ QUERY МЕТОД (POST с телом) ===

// Функция для сложной фильтрации через QUERY метод
async function loadStudentsViaQuery(filters) {
    const tbody = document.getElementById('students-table-body');
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center;">Загрузка...</td></tr>';
    
    try {
        // ИСПРАВЛЕНО: стучимся на /api/requests методом QUERY, а не на несуществующий /api/requests-query
        const response = await fetch(`${API_BASE_URL}/requests`, {
            method: 'QUERY', 
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(filters)
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Ошибка при фильтрации');
        }
        
        const students = await response.json();
        renderStudents(students);
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: red;">Ошибка: ${error.message}</td></tr>`;
    }
}

// === ОБРАБОТКА ФОРМЫ ===

studentForm.addEventListener('submit', async function(event) {
    event.preventDefault();

    const studentData = {
        isu: Number(document.getElementById('isu').value),
        fio: document.getElementById('fio').value.trim(),
        group: document.getElementById('group').value.trim(),
        dorm: document.getElementById('dorm').value ? Number(document.getElementById('dorm').value) : null, // Было dormitory
        room: document.getElementById('room').value ? Number(document.getElementById('room').value) : null,
        date: document.getElementById('date').value || null,
        isForeigner: document.getElementById('foreigner').checked, // Было is_foreigner
        notes: document.getElementById('notes').value.trim()
    };

    try {
        if (currentEditingIsu !== null) {
            await updateStudent(currentEditingIsu, studentData);
        } else {
            await createStudent(studentData);
        }
        
        studentForm.reset();
        currentEditingIsu = null;
        document.getElementById('btn-save').textContent = 'Сохранить';
        showTable();
    } catch (error) {
        alert(`Ошибка: ${error.message}`);
    }
});

// === ОБРАБОТКА КЛИКОВ ПО ТАБЛИЦЕ ===

document.getElementById('students-table-body').addEventListener('click', async function(event) {
    const button = event.target.closest('button');
    if (!button) return;

    const action = button.dataset.action;
    const isu = Number(button.dataset.isu);

    try {
        if (action === 'view') {
            const student = await fetchStudent(isu);
            if (!student) {
                alert('Студент не найден');
                return;
            }
            renderDossier(student);
            showDossierScreen();
        } else if (action === 'edit') {
            const student = await fetchStudent(isu);
            if (!student) {
                alert('Студент не найден');
                return;
            }
            currentEditingIsu = student.isu;
            document.getElementById('fio').value = student.fio;
            document.getElementById('group').value = student.group;
            document.getElementById('isu').value = student.isu;
            document.getElementById('dorm').value = student.dorm || '';
            document.getElementById('room').value = student.room || '';
            document.getElementById('date').value = student.date || '';
            document.getElementById('foreigner').checked = student.isForeigner;
            document.getElementById('notes').value = student.notes || '';
            
            document.getElementById('btn-save').textContent = 'Обновить данные';
            showForm();
        } else if (action === 'delete') {
            if (confirm("Вы уверены, что хотите удалить этого студента?")) {
                await deleteStudent(isu);
                loadStudents(); // Перезагружаем таблицу с сервера
            }
        }
    } catch (error) {
        alert(`Ошибка: ${error.message}`);
    }
});

// === ОТРИСОВКА ДОСЬЕ ===

function renderDossier(student) {
    const dateStr = student.date ? new Date(student.date).toLocaleDateString('ru-RU') : 'Не указано';
    const foreignerStr = student.is_foreigner ? 'Да' : 'Нет';
    const dormStr = student.dormitory ? `Общежитие №${student.dormitory}, комната ${student.room}` : 'Не проживает в общежитии';
    const notesStr = student.notes ? student.notes : 'Заметок нет';

    const dossierHTML = `
        <h3>${student.fio}</h3>
        <div class="dossier-row">
            <span class="dossier-label">Группа:</span>
            <span class="dossier-value">${student.group}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">ИСУ ID:</span>
            <span class="dossier-value">${student.isu}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Проживание:</span>
            <span class="dossier-value">${dormStr}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Срок заселения:</span>
            <span class="dossier-value">${dateStr}</span>
        </div>
        <div class="dossier-row">
            <span class="dossier-label">Иностранный студент:</span>
            <span class="dossier-value">${foreignerStr}</span>
        </div>
        <div class="dossier-row" style="flex-direction: column; border: none; margin-top: 15px;">
            <span class="dossier-label" style="width: 100%; margin-bottom: 5px;">Заметки:</span>
            <span class="dossier-value" style="background: #f9f9f9; padding: 10px; border-radius: 4px; display: block;">${notesStr}</span>
        </div>
    `;
    document.getElementById('dossier-content').innerHTML = dossierHTML;
}

// === ОБРАБОТЧИКИ КНОПОК ===

btnAdd.addEventListener('click', () => {
    currentEditingIsu = null;
    document.getElementById('btn-save').textContent = 'Сохранить';
    studentForm.reset();
    showForm();
});

btnCancelForm.addEventListener('click', () => {
    currentEditingIsu = null;
    document.getElementById('btn-save').textContent = 'Сохранить';
    studentForm.reset();
    showTable();
});

btnCloseDossier.addEventListener('click', () => {
    showTable();
});

// === ЛОГИКА РАСШИРЕННОЙ ФИЛЬТРАЦИИ ===

// === ЛОГИКА РАСШИРЕННОЙ ФИЛЬТРАЦИИ ===

btnApplyFilter.addEventListener('click', () => {
    const group = filterGroupInput.value.trim();
    const dorm = document.getElementById('filter-dormitory').value;
    const room = document.getElementById('filter-room').value;
    const foreigner = document.getElementById('filter-foreigner').value;
    
    const filters = {};
    
    if (group) filters.group = group;
    if (dorm) filters.dorm = Number(dorm); // Было dormitory
    if (room) filters.room = Number(room);
    if (foreigner !== '') filters.isForeigner = foreigner === 'true'; // Было is_foreigner
    
    const filterCount = Object.keys(filters).length;
    
    if (filterCount <= 2) {
        loadStudents(filters);
    } else {
        loadStudentsViaQuery(filters);
    }
});

btnClearFilter.addEventListener('click', () => {
    filterGroupInput.value = '';
    document.getElementById('filter-dormitory').value = '';
    document.getElementById('filter-room').value = ''; // Очищаем поле комнаты
    document.getElementById('filter-foreigner').value = '';
    loadStudents({});
});

// === ИНИЦИАЛИЗАЦИЯ ПРИ ЗАГРУЗКЕ СТРАНИЦЫ ===

loadStudents();