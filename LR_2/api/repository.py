import json
import os
from typing import List, Optional
from .schemas import Student

# Путь к файлу данных (в корне папки api или проекта, пусть будет в api)
DATA_FILE = os.path.join(os.path.dirname(__file__), 'students.json')

class StudentRepository:
    @staticmethod
    def _load_data() -> List[dict]:
        """Читает данные из JSON файла"""
        if not os.path.exists(DATA_FILE):
            return []
        try:
            with open(DATA_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return []

    @staticmethod
    def _save_data(data: List[dict]):
        """Сохраняет данные в JSON файл"""
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)

    @staticmethod
    def get_all() -> List[Student]:
        """Возвращает список всех студентов"""
        raw_data = StudentRepository._load_data()
        return [Student.from_dict(item) for item in raw_data]

    @staticmethod
    def get_by_isu(isu: int) -> Optional[Student]:
        """Ищет студента по ИСУ"""
        students = StudentRepository.get_all()
        for student in students:
            if student.isu == isu:
                return student
        return None

    @staticmethod
    def add(student: Student):
        """Добавляет нового студента"""
        raw_data = StudentRepository._load_data()
        raw_data.append(student.to_dict())
        StudentRepository._save_data(raw_data)

    @staticmethod
    def update(isu: int, updated_data: dict) -> Optional[Student]:
        """Обновляет данные студента. Возвращает обновленного студента или None."""
        raw_data = StudentRepository._load_data()
        for i, item in enumerate(raw_data):
            if item['isu'] == isu:
                # Обновляем только переданные поля
                item.update(updated_data)
                StudentRepository._save_data(raw_data)
                return Student.from_dict(item)
        return None

    @staticmethod
    def delete(isu: int) -> bool:
        """Удаляет студента по ИСУ. Возвращает True, если успешно."""
        raw_data = StudentRepository._load_data()
        initial_length = len(raw_data)
        raw_data = [item for item in raw_data if item['isu'] != isu]
        
        if len(raw_data) < initial_length:
            StudentRepository._save_data(raw_data)
            return True
        return False