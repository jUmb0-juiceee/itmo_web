const screenTable = document.getElementById('screen-table');
const screenForm = document.getElementById('screen-form');

const btnAdd = document.getElementById('btn-add');
const btnCancelForm = document.getElementById('btn-cancel-form');

const studentForm = document.getElementById('student-form');

const screenDossier = document.getElementById('screen-dossier');
const dossierContent = document.getElementById('dossier-content');
const btnCloseDossier = document.getElementById('btn-close-dossier');

// создаём глобальный массив студентов
let students = loadStudentsFromCookie();

// переменная-флаг редактирования студента
let currentEditingId = null;

// Функции переключения экранов
function showForm() {
    screenTable.style.display = 'none';
    screenDossier.style.display = 'none';
    screenForm.style.display = 'block';  
}

function showTable() {
    screenForm.style.display = 'none';   
    screenTable.style.display = 'block';
    screenDossier.style.display = 'none';
}

function showDossierScreen() {
    screenTable.style.display = 'none';
    screenForm.style.display = 'none';
    screenDossier.style.display = 'block';
}


// обработка формы и валидация

function handleFormSubmit(event) {
    event.preventDefault();

    const fio = document.getElementById('fio').value.trim();
    const group = document.getElementById('group').value.trim();
    const isuRaw = document.getElementById('isu').value;
    const dormRaw = document.getElementById('dorm').value;
    const roomRaw = document.getElementById('room').value;
    const date = document.getElementById('date').value;
    const isForeigner = document.getElementById('foreigner').checked; 
    const notes = document.getElementById('notes').value.trim();

    // все правила валидации

    // 1. проверка фио (3 слова, с заглавной, только русский алфавит)
    const fioRegex = /^[А-ЯЁ][а-яё]+\s[А-ЯЁ][а-яё]+\s[А-ЯЁ][а-яё]+$/;
    if (!fioRegex.test(fio)) {
        alert("Ошибка ФИО: Введите ровно три слова (Фамилия Имя Отчество). Каждое слово с заглавной буквы, только русские буквы.");
        return;
    }

    // 2. проверка группы (1 англ. заглавная буква + 4 цифры)
    const groupRegex = /^[A-Z]\d{4}$/;
    if (!groupRegex.test(group)) {
        alert("Ошибка Группы: Группа должна состоять из одной заглавной английской буквы и четырех цифр (например, P3212).");
        return;
    }

    // 3. проверка ИСУ (Ровно 6 цифр)
    const isuRegex = /^\d{6}$/;
    if (!isuRegex.test(isuRaw)) {
        alert("Ошибка ИСУ ID: ИСУ должен состоять ровно из 6 цифр.");
        return;
    }

    // 4. проверка уникальности ИСУ (Защита от дубликатов)
    const isIsuDuplicate = students.some(s => s.isu === Number(isuRaw) && s.id !== currentEditingId);
    if (isIsuDuplicate) {
        alert("Ошибка: Студент с таким ИСУ ID уже существует в системе!");
        return;
    }

    // 5. проверка общаги и комнаты (Только положительные числа)
    if (dormRaw && (isNaN(dormRaw) || Number(dormRaw) <= 0)) {
        alert("Ошибка: Номер общежития должен быть положительным числом.");
        return;
    }
    if (roomRaw && (isNaN(roomRaw) || Number(roomRaw) <= 0)) {
        alert("Ошибка: Номер комнаты должен быть положительным числом.");
        return;
    }

    // 6. проверка даты (Год не раньше 2000-го)
    if (date) {
        const selectedYear = new Date(date).getFullYear();
        if (selectedYear < 2000) {
            alert("Ошибка: Срок заселения не может быть раньше 2000 года.");
            return;
        }
    }

    // 7. если начали заполнять общагу - должны заполнить про неё всё
    const isDormBlockStarted = dormRaw || roomRaw || date;
    if (isDormBlockStarted) {
        if (!dormRaw || !roomRaw || !date) {
            alert("Если вы указываете данные об общежитии, пожалуйста, заполните все три поля (общага, комната, дата).");
            return;
        }
    }

    const studentData = {
        fio: fio,
        group: group,
        isu: Number(isuRaw),
        dorm: dormRaw ? Number(dormRaw) : null,
        room: roomRaw ? Number(roomRaw) : null,
        date: date,
        isForeigner: isForeigner,
        notes: notes
    };

    if (currentEditingId !== null) {
        const index = students.findIndex(s => s.id === currentEditingId);
        
        if (index !== -1) {
            students[index] = { ...students[index], ...studentData };
        }
        
        currentEditingId = null;
        document.getElementById('btn-save').textContent = 'Сохранить';
        
    } else {
        studentData.id = Date.now();
        students.push(studentData);
    }

    saveStudentsToCookie(students);

    studentForm.reset();
    renderTable();
    showTable();
}

// отрисовка таблицы

function renderTable() {
    const tbody = document.getElementById('students-table-body');
    
    tbody.innerHTML = '';

    if (students.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #777;">Список студентов пуст. Добавьте первого студента!</td></tr>';
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
                    <button class="action-btn btn-view" title="Посмотреть досье" data-action="view" data-id="${student.id}">👁️</button>
                    <button class="action-btn btn-edit" title="Редактировать" data-action="edit" data-id="${student.id}">✏️</button>
                    <button class="action-btn btn-delete" title="Удалить" data-action="delete" data-id="${student.id}">🗑️</button>
                </td>
            </tr>
        `;
        
        tbody.innerHTML += row;
    });
}

// Функция удаления студента
function handleDelete(id) {
    if (confirm("Вы уверены, что хотите удалить этого студента?")) {
        deleteStudentById(id);
        
        students = loadStudentsFromCookie();
        renderTable();
    }
}

// функция открытия досье

function openDossier(id) {
    const student = students.find(s => s.id === id);
    
    if (!student) return;

    const dateStr = student.date ? new Date(student.date).toLocaleDateString('ru-RU') : 'Не указано';
    const foreignerStr = student.isForeigner ? 'Да' : 'Нет';
    
    const dormStr = student.dorm ? `Общежитие №${student.dorm}, комната ${student.room}` : 'Не проживает в общежитии';
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

    showDossierScreen();
}

// Функция открытия формы для редактирования

function openEditForm(id) {
    const student = students.find(s => s.id === id);
    if (!student) return;

    currentEditingId = id;

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
}

// "слушаем" нажатия кнопок

btnAdd.addEventListener('click', () => {
    currentEditingId = null;
    document.getElementById('btn-save').textContent = 'Сохранить';
    studentForm.reset();
    showForm();
});

btnCancelForm.addEventListener('click', () => {
    currentEditingId = null;
    document.getElementById('btn-save').textContent = 'Сохранить';
    studentForm.reset();
    showTable();
});

btnCloseDossier.addEventListener('click', () => {
    showTable();
});

studentForm.addEventListener('submit', handleFormSubmit);

const tableBody = document.getElementById('students-table-body');
if (tableBody) {
    tableBody.addEventListener('click', function(event) {
        const button = event.target.closest('button');
        
        if (!button) return;

        const action = button.dataset.action;
        const id = Number(button.dataset.id);

        if (action === 'view') {
            openDossier(id);
        } else if (action === 'edit') {
            openEditForm(id);
        } else if (action === 'delete') {
            handleDelete(id);
        }
    });
}

renderTable();