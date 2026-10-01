import re
from datetime import datetime
from typing import Dict, Any, List

class StudentValidator:
    @staticmethod
    def validate(data: Dict[str, Any]) -> Dict[str, str]:
        """
        Проверяет данные по строгим правилам Лабораторной №1.
        Возвращает словарь ошибок: {'поле': 'описание ошибки'}.
        Если словарь пустой — данные валидны.
        """
        errors = {}

        # 1. ИСУ: ровно 6 цифр
        isu = data.get('isu')
        if isu is not None:
            if not re.match(r'^\d{6}$', str(isu)):
                errors['isu'] = "ИСУ ID должен состоять ровно из 6 цифр."

        # 2. ФИО: три слова, русские буквы, каждое с заглавной
        fio = data.get('fio')
        if fio is not None:
            fio_str = str(fio).strip()
            # Проверка: 3 слова, каждое начинается с заглавной русской буквы, далее строчные русские буквы или дефис
            if not re.match(r'^[А-ЯЁ][а-яё\-]+ [А-ЯЁ][а-яё\-]+ [А-ЯЁ][а-яё\-]+$', fio_str):
                errors['fio'] = "ФИО должно состоять из трёх слов на русском языке, каждое с заглавной буквы."

        # 3. Группа: заглавная латинская буква и 4 цифры
        group = data.get('group')
        if group is not None:
            if not re.match(r'^[A-Z]\d{4}$', str(group).strip()):
                errors['group'] = "Группа должна состоять из одной заглавной латинской буквы и 4 цифр (например, M3301)."

        # 4. Общежитие и комната: положительные числа (>= 1)
        dorm = data.get('dorm')
        room = data.get('room')
        
        if dorm is not None and dorm != '':
            if not isinstance(dorm, int) or dorm < 1:
                errors['dorm'] = "Номер общежития должен быть положительным числом."
        
        if room is not None and room != '':
            if not isinstance(room, int) or room < 1:
                errors['room'] = "Номер комнаты должен быть положительным числом."

        # 5. Дата: год не раньше 2000
        date = data.get('date')
        if date is not None and date != '':
            try:
                parsed_date = datetime.strptime(str(date), "%Y-%m-%d")
                if parsed_date.year < 2000:
                    errors['date'] = "Год в дате заселения не может быть раньше 2000."
            except ValueError:
                errors['date'] = "Неверный формат даты. Используйте ГГГГ-ММ-ДД."

        # 6. Связанные поля: либо заполнены все три (dorm, room, date), либо ни одного
        has_dorm = bool(dorm)
        has_room = bool(room)
        has_date = bool(date)
        
        fields_filled_count = sum([has_dorm, has_room, has_date])
        
        if fields_filled_count not in [0, 3]:
            errors['non_field_errors'] = "Если указано общежитие, должны быть заполнены также комната и дата заселения (и наоборот)."

        return errors