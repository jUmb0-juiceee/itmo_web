from django.core.exceptions import ValidationError
from .models import Student

class StudentService:
    @staticmethod
    def get_all_students(filters=None):
        """Получение списка студентов с возможностью фильтрации"""
        queryset = Student.objects.all()
        
        if filters:
            if 'group' in filters and filters['group']:
                queryset = queryset.filter(group__iexact=filters['group'])
            if 'dormitory' in filters and filters['dormitory']:
                queryset = queryset.filter(dormitory=filters['dormitory'])
            if 'room' in filters and filters['room']:
                queryset = queryset.filter(room=filters['room'])
            if 'is_foreigner' in filters and filters['is_foreigner'] is not None:
                # Преобразуем строку 'true'/'false' из запроса в boolean
                is_foreigner = str(filters['is_foreigner']).lower() == 'true'
                queryset = queryset.filter(is_foreigner=is_foreigner)
                
        return queryset

    @staticmethod
    def get_student_by_isu(isu):
        """Получение одного студента по ИСУ. Возвращает None, если не найден."""
        try:
            return Student.objects.get(isu=isu)
        except Student.DoesNotExist:
            return None

    @staticmethod
    def create_student(validated_data):
        """Создание нового студента с проверкой уникальности ИСУ"""
        if Student.objects.filter(isu=validated_data['isu']).exists():
            raise ValidationError({"isu": "Студент с таким ИСУ ID уже существует."})
        
        return Student.objects.create(**validated_data)

    @staticmethod
    def update_student(isu, validated_data):
        """Обновление данных студента"""
        student = StudentService.get_student_by_isu(isu)
        if not student:
            return None
            
        # Проверяем, не пытается ли пользователь занять чужой ИСУ
        new_isu = validated_data.get('isu')
        if new_isu and new_isu != isu and Student.objects.filter(isu=new_isu).exists():
            raise ValidationError({"isu": "Студент с таким ИСУ ID уже существует."})
            
        for attr, value in validated_data.items():
            setattr(student, attr, value)
        student.save()
        return student

    @staticmethod
    def delete_student(isu):
        """Удаление студента. Возвращает True, если удален, False если не найден."""
        student = StudentService.get_student_by_isu(isu)
        if student:
            student.delete()
            return True
        return False

    @staticmethod
    def create_student(validated_data):
        """Создание нового студента с проверкой уникальности ИСУ"""
        if Student.objects.filter(isu=validated_data['isu']).exists():
            raise ValidationError({"isu": "Студент с таким ИСУ ID уже существует."})
        
        # Бизнес-проверка: номер общежития должен быть от 1 до 20 (пример)
        dormitory = validated_data.get('dormitory')
        if dormitory and (dormitory < 1 or dormitory > 20):
            raise ValidationError({"dormitory": "Номер общежития должен быть от 1 до 20."})
        
        return Student.objects.create(**validated_data)

    @staticmethod
    def update_student(isu, validated_data):
        """Обновление данных студента"""
        student = StudentService.get_student_by_isu(isu)
        if not student:
            return None
            
        # Проверяем, не пытается ли пользователь занять чужой ИСУ
        new_isu = validated_data.get('isu')
        if new_isu and new_isu != isu and Student.objects.filter(isu=new_isu).exists():
            raise ValidationError({"isu": "Студент с таким ИСУ ID уже существует."})
        
        # Бизнес-проверка: номер общежития
        dormitory = validated_data.get('dormitory')
        if dormitory and (dormitory < 1 or dormitory > 20):
            raise ValidationError({"dormitory": "Номер общежития должен быть от 1 до 20."})
            
        for attr, value in validated_data.items():
            setattr(student, attr, value)
        student.save()
        return student