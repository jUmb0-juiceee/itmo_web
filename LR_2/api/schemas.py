from dataclasses import dataclass, asdict
from typing import Optional

@dataclass
class Student:
    isu: int
    fio: str
    group: str
    dorm: Optional[int] = None
    room: Optional[int] = None
    date: Optional[str] = None
    isForeigner: bool = False
    notes: str = ""

    def to_dict(self) -> dict:
        """Преобразует объект в словарь для сохранения в JSON"""
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict) -> 'Student':
        """Создает объект из словаря (при чтении из JSON)"""
        return cls(**data)