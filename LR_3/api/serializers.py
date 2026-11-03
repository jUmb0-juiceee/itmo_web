# api/serializers.py
from typing import Dict, Any, List
from .schemas import Student
from .validators import StudentValidator

class StudentSerializer:
    REQUIRED_FIELDS = ['isu', 'fio', 'group']
    ALLOWED_FIELDS = ['isu', 'fio', 'group', 'dorm', 'room', 'date', 'isForeigner', 'notes']
    
    @staticmethod
    def validate(data: Dict[str, Any]) -> tuple:
        """
        Возвращает (validated_data, errors).
        Использует безопасный метод .get() для предотвращения KeyError.
        """
        errors = {}
        validated_data = {}
        
        # 1. Безопасное получение сырых данных
        raw_isu = data.get('isu')
        raw_fio = data.get('fio')
        raw_group = data.get('group')
        
        # 2. Проверка на обязательные поля
        if raw_isu is None or (isinstance(raw_isu, str) and str(raw_isu).strip() == ''):
            errors['isu'] = "Поле 'ИСУ ID' обязательно для заполнения."
        if raw_fio is None or (isinstance(raw_fio, str) and str(raw_fio).strip() == ''):
            errors['fio'] = "Поле 'ФИО' обязательно для заполнения."
        if raw_group is None or (isinstance(raw_group, str) and str(raw_group).strip() == ''):
            errors['group'] = "Поле 'Группа' обязательно для заполнения."
        
        if errors:
            return {}, errors
        
        # 3. Базовое преобразование типов (теперь мы уверены, что они не пустые)
        try:
            validated_data['isu'] = int(raw_isu)
        except (ValueError, TypeError):
            errors['isu'] = "ИСУ ID должен быть числом."
            
        validated_data['fio'] = str(raw_fio).strip()
        validated_data['group'] = str(raw_group).strip()
        
        # Необязательные числовые поля
        raw_dorm = data.get('dorm')
        if raw_dorm is not None and str(raw_dorm).strip() != '':
            try:
                validated_data['dorm'] = int(raw_dorm)
            except (ValueError, TypeError):
                errors['dorm'] = "Поле 'Общежитие' должно быть числом."
        else:
            validated_data['dorm'] = None
            
        raw_room = data.get('room')
        if raw_room is not None and str(raw_room).strip() != '':
            try:
                validated_data['room'] = int(raw_room)
            except (ValueError, TypeError):
                errors['room'] = "Поле 'Комната' должно быть числом."
        else:
            validated_data['room'] = None
            
        raw_date = data.get('date')
        if raw_date is not None and str(raw_date).strip() != '':
            validated_data['date'] = str(raw_date).strip()
        else:
            validated_data['date'] = None
            
        validated_data['isForeigner'] = bool(data.get('isForeigner', False))
        validated_data['notes'] = str(data.get('notes', '')).strip()
        
        if errors:
            return {}, errors
        
        # 4. Строгая бизнес-валидация по правилам LR_1
        validation_errors = StudentValidator.validate(validated_data)
        if validation_errors:
            return {}, validation_errors
            
        return validated_data, errors

    @staticmethod
    def to_representation(student: Student) -> Dict[str, Any]:
        return {
            'isu': student.isu,
            'fio': student.fio,
            'group': student.group,
            'dorm': student.dorm,
            'room': student.room,
            'date': student.date,
            'isForeigner': student.isForeigner,
            'notes': student.notes
        }
    
    @staticmethod
    def many_to_representation(students: List[Student]) -> List[Dict[str, Any]]:
        return [StudentSerializer.to_representation(s) for s in students]