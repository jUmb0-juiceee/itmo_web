# api/exceptions.py
from rest_framework.views import exception_handler
from rest_framework.response import Response
from rest_framework import status

def custom_exception_handler(exc, context):
    """
    Перехватывает непредвиденные ошибки Django/DRF и приводит их к единому виду.
    """
    # Сначала вызываем стандартный обработчик
    response = exception_handler(exc, context)

    if response is not None:
        # Если DRF сам вернул ошибку (например, 400 за плохой JSON), форматируем её
        return Response(
            {"error": str(response.data)},
            status=response.status_code
        )
    
    # Если response is None, значит произошла критическая ошибка (500)
    return Response(
        {"error": "Внутренняя ошибка сервера. Попробуйте позже."},
        status=status.HTTP_500_INTERNAL_SERVER_ERROR
    )