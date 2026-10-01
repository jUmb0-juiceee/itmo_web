from typing import Optional, Dict, Any, List
from .schemas import Student
from .validators import StudentValidator

class StudentSerializer:
    REQUIRED_FIELDS = ['isu', 'fio', 'group']
    ALLOWED_FIELDS = ['isu', 'fio', 'group', 'dorm', 'room', 'date', 'isForeigner', 'notes']
    
    @staticmethod
    def validate(data: Dict[str, Any]) -> Dict[str, Any]:
        errors = {}
        validated_data = {}
        
        # 1. Проверка на обязательные поля (базовая)
        for field in StudentSerializer.REQUIRED_FIELDS:
            if field not in data or data[field] is None or (isinstance(data[field], str) and str(data[field]).strip() == ''):
                errors[field] = f"Поле '{field}' обязательно для заполнения."
        
        if errors:
            return {}, errors
        
        # 2. Базовое преобразование типов
        try:
            validated_data['isu'] = int(data['isu'])
        except (ValueError, TypeError):
            errors['isu'] = "ИСУ ID должен быть числом."
            
        validated_data['fio'] = str(data['fio']).strip()
        validated_data['group'] = str(data['group']).strip()
        
        # Необязательные числовые поля
        for field in ['dorm', 'room']:
            if field in data and data[field] is not None and str(data[field]).strip() != '':
                try:
                    validated_data[field] = int(data[field])
                except (ValueError, TypeError):
                    errors[field] = f"Поле '{field}' должно быть числом."
            else:
                validated_data[field] = None
                
        if 'date' in data and data['date'] is not None and str(data['date']).strip() != '':
            validated_data['date'] = str(data['date']).strip()
        else:
            validated_data['date'] = None
            
        validated_data['isForeigner'] = bool(data.get('isForeigner', False))
        validated_data['notes'] = str(data.get('notes', '')).strip()
        
        # 3. Строгая бизнес-валидация по правилам LR_1
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