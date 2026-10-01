# api/services.py
from .repository import StudentRepository
from .schemas import Student


class StudentService:
    @staticmethod
    def get_all_students(filters: dict = None) -> list:
        """Получение списка студентов с фильтрацией"""
        students = StudentRepository.get_all()
        
        if not filters:
            return students
        
        filtered = students
        
        # Фильтрация по группе (регистронезависимо)
        if 'group' in filters and filters['group']:
            group_filter = str(filters['group']).upper()
            filtered = [s for s in filtered if s.group.upper() == group_filter]
        
        # Фильтрация по общежитию
        if 'dorm' in filters and filters['dorm'] is not None:
            try:
                dorm_filter = int(filters['dorm'])
                filtered = [s for s in filtered if s.dorm == dorm_filter]
            except (ValueError, TypeError):
                pass  # Игнорируем некорректные фильтры
        
        # Фильтрация по комнате
        if 'room' in filters and filters['room'] is not None:
            try:
                room_filter = int(filters['room'])
                filtered = [s for s in filtered if s.room == room_filter]
            except (ValueError, TypeError):
                pass
        
        # Фильтрация по иностранному студенту
        if 'isForeigner' in filters and filters['isForeigner'] is not None:
            foreigner_filter = str(filters['isForeigner']).lower() == 'true'
            filtered = [s for s in filtered if s.isForeigner == foreigner_filter]
        
        return filtered

    @staticmethod
    def get_student_by_isu(isu: int):
        """Получение одного студента по ИСУ. Возвращает None, если не найден."""
        return StudentRepository.get_by_isu(isu)

    @staticmethod
    def create_student(validated_data: dict) -> Student:
        """Создание нового студента с проверкой уникальности ИСУ"""
        # Проверяем, не существует ли уже студент с таким ИСУ
        existing = StudentRepository.get_by_isu(validated_data['isu'])
        if existing:
            raise ValueError("Студент с таким ИСУ ID уже существует.")
        
        # Создаём объект студента
        student = Student(
            isu=validated_data['isu'],
            fio=validated_data['fio'],
            group=validated_data['group'],
            dorm=validated_data.get('dorm'),
            room=validated_data.get('room'),
            date=validated_data.get('date'),
            isForeigner=validated_data.get('isForeigner', False),
            notes=validated_data.get('notes', '')
        )
        
        # Сохраняем в репозиторий
        StudentRepository.add(student)
        return student

    @staticmethod
    def update_student(isu: int, validated_data: dict):
        """Обновление данных студента"""
        # Проверяем, существует ли студент
        existing = StudentRepository.get_by_isu(isu)
        if not existing:
            return None
        
        # Проверяем, не пытается ли пользователь занять чужой ИСУ
        new_isu = validated_data.get('isu')
        if new_isu and new_isu != isu:
            conflict = StudentRepository.get_by_isu(new_isu)
            if conflict:
                raise ValueError("Студент с таким ИСУ ID уже существует.")
        
        # Обновляем данные через репозиторий
        updated_student = StudentRepository.update(isu, validated_data)
        return updated_student

    @staticmethod
    def delete_student(isu: int) -> bool:
        """Удаление студента. Возвращает True, если удален, False если не найден."""
        return StudentRepository.delete(isu)