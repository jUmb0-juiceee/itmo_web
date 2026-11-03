# api/services.py
from .repository import StudentRepository
from .schemas import Student


class StudentService:
    @staticmethod
    def get_all_students(filters: dict = None, search: str = None) -> list:
        """Получение списка студентов с фильтрацией и поиском"""
        students = StudentRepository.get_all()
        
        # 1. Применяем фильтры
        if filters:
            filtered = students
            
            if 'group' in filters and filters['group']:
                group_filter = str(filters['group']).upper()
                filtered = [s for s in filtered if s.group.upper() == group_filter]
            
            if 'dorm' in filters and filters['dorm'] is not None:
                dorm_filter = int(filters['dorm'])
                filtered = [s for s in filtered if s.dorm == dorm_filter]
            
            if 'room' in filters and filters['room'] is not None:
                room_filter = int(filters['room'])
                filtered = [s for s in filtered if s.room == room_filter]
            
            if 'isForeigner' in filters and filters['isForeigner'] is not None:
                foreigner_filter = bool(filters['isForeigner'])
                filtered = [s for s in filtered if s.isForeigner == foreigner_filter]
            
            students = filtered
        
        # 2. Применяем поиск по ФИО и заметкам (регистронезависимо)
        if search:
            search_lower = search.lower().strip()
            students = [
                s for s in students
                if search_lower in s.fio.lower() or 
                   (s.notes and search_lower in s.notes.lower())
            ]
        
        return students

    @staticmethod
    def get_student_by_isu(isu: int):
        """Получение одного студента по ИСУ. Возвращает None, если не найден."""
        return StudentRepository.get_by_isu(isu)

    @staticmethod
    def create_student(validated_data: dict) -> Student:
        """Создание нового студента с проверкой уникальности ИСУ"""
        existing = StudentRepository.get_by_isu(validated_data.get('isu'))
        if existing:
            raise ValueError("Студент с таким ИСУ ID уже существует.")
        
        student = Student(
            isu=validated_data.get('isu'),
            fio=validated_data.get('fio', ''),
            group=validated_data.get('group', ''),
            dorm=validated_data.get('dorm'),
            room=validated_data.get('room'),
            date=validated_data.get('date'),
            isForeigner=validated_data.get('isForeigner', False),
            notes=validated_data.get('notes', '')
        )
        
        StudentRepository.add(student)
        return student

    @staticmethod
    def update_student(isu: int, validated_data: dict):
        """Обновление данных студента"""
        existing = StudentRepository.get_by_isu(isu)
        if not existing:
            return None
        
        new_isu = validated_data.get('isu')
        if new_isu and new_isu != isu:
            conflict = StudentRepository.get_by_isu(new_isu)
            if conflict:
                raise ValueError("Студент с таким ИСУ ID уже существует.")
        
        updated_student = StudentRepository.update(isu, validated_data)
        return updated_student

    @staticmethod
    def delete_student(isu: int) -> bool:
        """Удаление студента. Возвращает True, если удален, False если не найден."""
        return StudentRepository.delete(isu)

    @staticmethod
    def get_students_paginated(page: int = 1, limit: int = 20, filters: dict = None, search: str = None) -> dict:
        """
        Получение студентов с пагинацией, фильтрами и поиском.
        """
        # Получаем всех студентов (с фильтрацией и поиском)
        all_students = StudentService.get_all_students(filters, search)
        
        # Вычисляем общее количество
        total = len(all_students)
        
        # Вычисляем границы
        offset = (page - 1) * limit
        end = offset + limit
        
        # Получаем подмножество
        paginated_students = all_students[offset:end]
        
        # Вычисляем общее количество страниц
        total_pages = (total + limit - 1) // limit if total > 0 else 0
        
        return {
            "students": paginated_students,
            "pagination": {
                "page": page,
                "limit": limit,
                "total": total,
                "total_pages": total_pages,
                "has_next": page < total_pages,
                "has_prev": page > 1
            }
        }