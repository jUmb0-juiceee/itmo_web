from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.core.exceptions import ValidationError

from .models import Student
from .serializers import StudentSerializer
from .services import StudentService

# Эндпоинт для списка студентов и создания нового (GET и POST /api/requests)
class StudentListCreateView(APIView):
    def get(self, request):
        # Получаем все query-параметры из URL (например, ?group=M3301)
        filters = request.query_params.dict()
        students = StudentService.get_all_students(filters)
        
        # many=True, потому что мы передаем список (QuerySet)
        serializer = StudentSerializer(students, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request):
        serializer = StudentSerializer(data=request.data)
        if serializer.is_valid():
            try:
                student = StudentService.create_student(serializer.validated_data)
                return Response(StudentSerializer(student).data, status=status.HTTP_201_CREATED)
            except ValidationError as e:
                # Если ошибка касается ИСУ -> 409 Conflict
                if 'ису' in str(e).lower() or 'isu' in str(e).lower():
                    return Response({"error": "Студент с таким ИСУ ID уже существует."}, status=status.HTTP_409_CONFLICT)
                # Любая другая бизнес-ошибка из сервиса -> 422 Unprocessable Entity
                return Response({"error": str(e)}, status=status.HTTP_422_UNPROCESSABLE_ENTITY)

        # Если DRF ругается на поле isu, значит сработала проверка на уникальность
        if 'isu' in serializer.errors:
            return Response({"error": "Студент с таким ИСУ ID уже существует."}, status=status.HTTP_409_CONFLICT)
        
        # Если данные не прошли валидацию формата (нет обязательных полей и т.д.) -> 400 Bad Request
        return Response({"error": serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


# Эндпоинт для работы с конкретным студентом (GET, PATCH, DELETE /api/requests/:id)
class StudentDetailView(APIView):
    def get_object(self, isu):
        return StudentService.get_student_by_isu(isu)

    def get(self, request, isu):
        student = self.get_object(isu)
        if not student:
            return Response({"error": "Студент не найден"}, status=status.HTTP_404_NOT_FOUND)
        
        serializer = StudentSerializer(student)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def patch(self, request, isu):
        # partial=True разрешает передавать не все поля (только те, что нужно обновить)
        serializer = StudentSerializer(data=request.data, partial=True)
        if serializer.is_valid():
            try:
                student = StudentService.update_student(isu, serializer.validated_data)
                if not student:
                    return Response({"error": "Студент не найден"}, status=status.HTTP_404_NOT_FOUND)
                return Response(StudentSerializer(student).data, status=status.HTTP_200_OK)
            except ValidationError as e:
                return Response({"error": str(e)}, status=status.HTTP_409_CONFLICT)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    def delete(self, request, isu):
        deleted = StudentService.delete_student(isu)
        if deleted:
            # 204 No Content - успешное удаление без тела ответа
            return Response(status=status.HTTP_204_NO_CONTENT)
        return Response({"error": "Студент не найден"}, status=status.HTTP_404_NOT_FOUND)


class StudentQueryView(APIView):
    """
    Эндпоинт для сложной фильтрации через POST запрос.
    Принимает фильтры в теле запроса вместо query-параметров.
    """
    def post(self, request):
        # Получаем фильтры из тела запроса (не из URL)
        filters = request.data
        
        students = StudentService.get_all_students(filters)
        serializer = StudentSerializer(students, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)