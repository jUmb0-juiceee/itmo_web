// Поиск элементов на странице
const screenTable = document.getElementById('screen-table');
const screenForm = document.getElementById('screen-form');

const btnAdd = document.getElementById('btn-add');
const btnBack1 = document.getElementById('btn-back-1');

const studentForm = document.getElementById('student-form');

const screenDossier = document.getElementById('screen-dossier');
const dossierContent = document.getElementById('dossier-content');
const btnBack2 = document.getElementById('btn-back-2');

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

    if (fio.length < 3) {
        alert("ФИО должно содержать минимум 3 символа!");
        return;
    }

    if (isuRaw === "" || isNaN(Number(isuRaw))) {
        alert("ИСУ ID должен быть заполнен и состоять из цифр!");
        return;
    }

    if (!/^[a-zA-Zа-яА-Я]/.test(group)) {
        alert("Группа должна начинаться с буквы!");
        return;
    }

    const isDormBlockStarted = dormRaw || roomRaw || date;
    if (isDormBlockStarted) {
        if (!dormRaw || !roomRaw || !date) {
            alert("Если вы указываете данные об общежитии, пожалуйста, заполните номер общежития, номер комнаты и срок заселения полностью. Либо оставьте все три поля пустыми.");
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
                    <button class="action-btn btn-view" title="Посмотреть досье" onclick="openDossier(${student.id})">👁️</button>
                    <button class="action-btn btn-edit" title="Редактировать" onclick="openEditForm(${student.id})">✏️</button>
                    <button class="action-btn btn-delete" title="Удалить" onclick="handleDelete(${student.id})">🗑️</button>
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

    dossierContent.innerHTML = dossierHTML;

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

btnBack1.addEventListener('click', () => {
    currentEditingId = null;
    document.getElementById('btn-save').textContent = 'Сохранить';
    studentForm.reset();
    showTable();
});

btnBack2.addEventListener('click', () => {
    showTable();
});

studentForm.addEventListener('submit', handleFormSubmit);

renderTable();