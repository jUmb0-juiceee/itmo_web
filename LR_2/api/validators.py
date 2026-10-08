import re
from datetime import datetime
from typing import Dict, Any

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
            isu_str = str(isu).strip()
            if not re.match(r'^\d{6}$', isu_str):
                errors['isu'] = "ИСУ ID должен состоять ровно из 6 цифр."

        # 2. ФИО: три слова, русские буквы, каждое с заглавной
        fio = data.get('fio')
        if fio is not None:
            fio_str = str(fio).strip()
            if not re.match(r'^[А-ЯЁ][а-яё\-]+ [А-ЯЁ][а-яё\-]+ [А-ЯЁ][а-яё\-]+$', fio_str):
                errors['fio'] = "ФИО должно состоять из трёх слов на русском языке, каждое с заглавной буквы."

        # 3. Группа: заглавная латинская буква и 4 цифры
        group = data.get('group')
        if group is not None:
            group_str = str(group).strip()
            if not re.match(r'^[A-Z]\d{4}$', group_str):
                errors['group'] = "Группа должна состоять из одной заглавной латинской буквы и 4 цифр (например, M3301)."

        # 4. Общежитие и комната: положительные числа (>= 1)
        dorm = data.get('dorm')
        room = data.get('room')
        
        if dorm is not None and dorm != '' and dorm is not None:
            if not isinstance(dorm, int) or dorm < 1:
                errors['dorm'] = "Номер общежития должен быть положительным числом."
        
        if room is not None and room != '' and room is not None:
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
        has_dorm = dorm is not None and dorm != '' and dorm != 0
        has_room = room is not None and room != '' and room != 0
        has_date = date is not None and date != ''
        
        fields_filled_count = sum([has_dorm, has_room, has_date])
        
        if fields_filled_count not in [0, 3]:
            errors['non_field_errors'] = "Если указано общежитие, должны быть заполнены также комната и дата заселения (и наоборот)."

        return errors

# api/validators.py (добавить в конец файла)

class FilterValidator:
    ALLOWED_FILTERS = ['group', 'dorm', 'room', 'isForeigner']

    @staticmethod
    def validate(filters: dict) -> tuple:
        """
        Проверяет параметры фильтрации на корректность.
        Возвращает (cleaned_filters, errors).
        """
        errors = {}
        cleaned = {}

        for key, value in filters.items():
            # Игнорируем неизвестные поля (или можно вернуть ошибку)
            if key not in FilterValidator.ALLOWED_FILTERS:
                continue
            
            # 1. Группа: 1 заглавная латинская буква + 4 цифры
            if key == 'group':
                if not isinstance(value, str) or not re.match(r'^[A-Z]\d{4}$', value.strip()):
                    errors['group'] = "Группа должна состоять из одной заглавной латинской буквы и 4 цифр."
                else:
                    cleaned['group'] = value.strip()
                    
            # 2. Общежитие: целое число >= 1
            elif key == 'dorm':
                try:
                    dorm_val = int(value)
                    if dorm_val < 1:
                        errors['dorm'] = "Номер общежития должен быть положительным числом."
                    else:
                        cleaned['dorm'] = dorm_val
                except (ValueError, TypeError):
                    errors['dorm'] = "Номер общежития должен быть числом."
                    
            # 3. Комната: целое число >= 1
            elif key == 'room':
                try:
                    room_val = int(value)
                    if room_val < 1:
                        errors['room'] = "Номер комнаты должен быть положительным числом."
                    else:
                        cleaned['room'] = room_val
                except (ValueError, TypeError):
                    errors['room'] = "Номер комнаты должен быть числом."
                    
            # 4. Иностранный студент: булево значение (или строка 'true'/'false')
            elif key == 'isForeigner':
                if isinstance(value, bool):
                    cleaned['isForeigner'] = value
                elif isinstance(value, str) and value.lower() in ['true', 'false']:
                    cleaned['isForeigner'] = value.lower() == 'true'
                else:
                    errors['isForeigner'] = "Значение должно быть true или false."

        return cleaned, errors
