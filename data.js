// функция для чтения конкретной куки по имени
function getCookie(name) {
    let matches = document.cookie.match(new RegExp(
        "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + "=([^;]*)"
    ));
    return matches ? decodeURIComponent(matches[1]) : undefined;
}

// Функция сохранения массива студентов в куки
function saveStudentsToCookie(studentsArray) {
    const jsonString = JSON.stringify(studentsArray);
    document.cookie = `students_db=${encodeURIComponent(jsonString)}; path=/; max-age=2592000`;
}

// Функция чтения массива студентов из кук
function loadStudentsFromCookie() {
    const jsonString = getCookie('students_db');
    
    if (!jsonString) {
        return [];
    }
    
    return JSON.parse(decodeURIComponent(jsonString));
}

// Функция удаления студента по ID
function deleteStudentById(id) {
    let students = loadStudentsFromCookie();
    students = students.filter(student => student.id !== id);
    saveStudentsToCookie(students);
}