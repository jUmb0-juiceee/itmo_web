import json
import os
import threading
from typing import List, Optional, Dict
from .schemas import Student


class StudentRepository:
    """
    Репозиторий студентов.
    - кэш в памяти (файл читается один раз);
    - индекс по ИСУ для поиска за O(1);
    - RLock для потокобезопасной записи.
    """

    FILE_PATH = os.path.join(os.path.dirname(__file__), '..', 'students.json')

    _cache: Optional[List[Student]] = None      # сохраняет порядок записей
    _index: Dict[int, Student] = {}             # isu -> Student, поиск за O(1)
    _lock = threading.RLock()                   # повторный захват тем же потоком разрешён

    # === ВНУТРЕННИЕ МЕТОДЫ (вызываются только под локом) ===

    @staticmethod
    def _read_from_file() -> List[Student]:
        """Читает данные из JSON-файла и преобразует их в объекты Student."""
        if not os.path.exists(StudentRepository.FILE_PATH):
            return []

        try:
            with open(StudentRepository.FILE_PATH, 'r', encoding='utf-8') as f:
                data = json.load(f)
            return [Student(**item) for item in data]
        except (json.JSONDecodeError, IOError) as e:
            print(f"Ошибка чтения файла студентов: {e}")
            return []

    @staticmethod
    def _write_to_file(students: List[Student]):
        """Записывает список студентов в JSON-файл."""
        data = [s.__dict__ for s in students]
        with open(StudentRepository.FILE_PATH, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    @classmethod
    def _ensure_loaded(cls):
        """Ленивая загрузка кэша и построение индекса. Вызывать только под локом."""
        if cls._cache is None:
            cls._cache = cls._read_from_file()
            cls._index = {s.isu: s for s in cls._cache}

    # === ПУБЛИЧНЫЙ ИНТЕРФЕЙС (сигнатуры не изменились) ===

    @classmethod
    def get_all(cls) -> List[Student]:
        """Возвращает копию списка студентов (порядок сохраняется)."""
        with cls._lock:
            cls._ensure_loaded()
            return list(cls._cache)

    @classmethod
    def get_by_isu(cls, isu: int) -> Optional[Student]:
        """Поиск студента по ИСУ за O(1) через индекс."""
        with cls._lock:
            cls._ensure_loaded()
            return cls._index.get(isu)

    @classmethod
    def add(cls, student: Student):
        """Добавление нового студента."""
        with cls._lock:
            cls._ensure_loaded()
            cls._cache.append(student)
            cls._index[student.isu] = student
            cls._write_to_file(cls._cache)

    @classmethod
    def update(cls, isu: int, updated_data: dict) -> Optional[Student]:
        """Обновление данных студента. Поддерживает смену ИСУ."""
        with cls._lock:
            cls._ensure_loaded()
            student = cls._index.get(isu)
            if student is None:
                return None

            for key, value in updated_data.items():
                if hasattr(student, key):
                    setattr(student, key, value)

            # Если ИСУ изменился — переносим запись в индексе
            if student.isu != isu:
                cls._index.pop(isu, None)
                cls._index[student.isu] = student

            cls._write_to_file(cls._cache)
            return student

    @classmethod
    def delete(cls, isu: int) -> bool:
        """Удаление студента по ИСУ."""
        with cls._lock:
            cls._ensure_loaded()
            student = cls._index.pop(isu, None)
            if student is None:
                return False

            cls._cache = [s for s in cls._cache if s.isu != isu]
            cls._write_to_file(cls._cache)
            return True