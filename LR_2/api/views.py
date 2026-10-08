# api/views.py
import traceback
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status

from .serializers import StudentSerializer
from .services import StudentService
from .schemas import Student
from .validators import StudentValidator, FilterValidator


def format_error(message, details=None):
    """Вспомогательная функция для единого формата ошибок"""
    error_body = {"error": message}
    if details:
        error_body["details"] = details
    return error_body


class StudentListCreateView(APIView):
    # Разрешаем методы: GET, POST, QUERY, OPTIONS, HEAD
    http_method_names = ['get', 'post', 'query', 'options', 'head']

    def get(self, request):
        """Получение списка студентов (GET /api/requests?group=...)"""
        filters = {}
        if request.query_params.get('group'):
            filters['group'] = request.query_params.get('group')
        if request.query_params.get('dorm'):
            filters['dorm'] = request.query_params.get('dorm')
        if request.query_params.get('room'):
            filters['room'] = request.query_params.get('room')
        if request.query_params.get('isForeigner'):
            filters['isForeigner'] = request.query_params.get('isForeigner')

        # ВАЛИДАЦИЯ ФИЛЬТРОВ
        cleaned_filters, errors = FilterValidator.validate(filters)
        if errors:
            return Response(
                {"error": "Ошибка в параметрах фильтрации", "details": errors}, 
                status=status.HTTP_400_BAD_REQUEST
            )

        students = StudentService.get_all_students(cleaned_filters)
        return Response(
            StudentSerializer.many_to_representation(students),
            status=status.HTTP_200_OK
        )

    def post(self, request):
        """Создание нового студента (POST /api/requests)"""
        try:
            # 1. Базовая проверка и приведение типов
            validated_data, errors = StudentSerializer.validate(request.data)
            
            if errors:
                # ИСПРАВЛЕНО: используем .get() вместо прямого обращения к ключу
                is_bad_request = any(
                    errors.get(k) and 'обязательно' in str(errors.get(k)).lower()
                    for k in ['isu', 'fio', 'group']
                )
                code = status.HTTP_400_BAD_REQUEST if is_bad_request else status.HTTP_422_UNPROCESSABLE_ENTITY
                return Response({"error": "Ошибка валидации", "details": errors}, status=code)

            # 2. Бизнес-логика (проверка уникальности)
            student = StudentService.create_student(validated_data)
            return Response(
                StudentSerializer.to_representation(student),
                status=status.HTTP_201_CREATED
            )
        except ValueError as e:
            # Дубликат ИСУ
            return Response(
                {"error": str(e), "details": {"isu": str(e)}},
                status=status.HTTP_409_CONFLICT
            )
        except Exception as e:
            # Любая другая ошибка — возвращаем 500 с деталями
            return Response(
                {"error": "Внутренняя ошибка сервера", "details": {"server": str(e)}},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
            
        except Exception as e:
            # ЯДЕРНЫЙ ПЕРЕХВАТ: печатаем полный стек ошибки
            full_traceback = traceback.format_exc()
            print("\n" + "!"*50)
            print("КРИТИЧЕСКАЯ ОШИБКА В POST ЗАПРОСЕ:")
            print(full_traceback)
            print("!"*50 + "\n")
            
            return Response(
                {
                    "error": "Внутренняя ошибка сервера", 
                    "details": {
                        "server": str(e),
                        "traceback": full_traceback # Отправляем стек ошибки прямо в браузер!
                    }
                },
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    def query(self, request):
        """Сложная фильтрация (QUERY /api/requests)"""
        filters = request.data
        
        if not isinstance(filters, dict):
            return Response(
                {"error": "Тело запроса должно быть JSON-объектом"},
                status=status.HTTP_400_BAD_REQUEST
            )
        
        # ВАЛИДАЦИЯ ФИЛЬТРОВ
        cleaned_filters, errors = FilterValidator.validate(filters)
        if errors:
            return Response(
                {"error": "Ошибка в параметрах фильтрации", "details": errors}, 
                status=status.HTTP_400_BAD_REQUEST
            )

        students = StudentService.get_all_students(cleaned_filters)
        return Response(
            StudentSerializer.many_to_representation(students),
            status=status.HTTP_200_OK
        )


class StudentDetailView(APIView):
    http_method_names = ['get', 'patch', 'delete', 'options', 'head']

    def get(self, request, isu):
        """Получение студента по ИСУ (GET /api/requests/:id)"""
        student = StudentService.get_student_by_isu(isu)
        if not student:
            return Response(format_error("Студент не найден"), status=status.HTTP_404_NOT_FOUND)
        
        return Response(
            StudentSerializer.to_representation(student),
            status=status.HTTP_200_OK
        )

    def patch(self, request, isu):
        """Частичное обновление студента (PATCH /api/requests/:id)"""
        try:
            student = StudentService.get_student_by_isu(isu)
            if not student:
                return Response(
                    {"error": "Студент не найден", "details": {"isu": "Студент не найден"}},
                    status=status.HTTP_404_NOT_FOUND
                )

            # Смешиваем текущие данные с новыми перед валидацией
            current_data = StudentSerializer.to_representation(student)
            for key in request.data:
                if key in StudentSerializer.ALLOWED_FIELDS:
                    current_data[key] = request.data[key]

            validated_data, errors = StudentSerializer.validate(current_data)
            
            if errors:
                return Response(
                    {"error": "Ошибка валидации", "details": errors},
                    status=status.HTTP_422_UNPROCESSABLE_ENTITY
                )

            # Проверка на смену ИСУ на занятый
            new_isu = validated_data.get('isu')
            if new_isu and new_isu != isu:
                if StudentService.get_student_by_isu(new_isu):
                    return Response(
                        {"error": "Студент с таким ИСУ ID уже существует", "details": {"isu": "Студент с таким ИСУ ID уже существует"}},
                        status=status.HTTP_409_CONFLICT
                    )

            updated_student = StudentService.update_student(isu, validated_data)
            return Response(
                StudentSerializer.to_representation(updated_student),
                status=status.HTTP_200_OK
            )
        except Exception as e:
            return Response(
                {"error": "Внутренняя ошибка сервера", "details": {"server": str(e)}},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    def delete(self, request, isu):
        """Удаление студента (DELETE /api/requests/:id)"""
        deleted = StudentService.delete_student(isu)
        if not deleted:
            return Response(format_error("Студент не найден"), status=status.HTTP_404_NOT_FOUND)
        
        # 204 No Content - успешное удаление без тела ответа
        return Response(status=status.HTTP_204_NO_CONTENT)