from rest_framework.views import exception_handler
from rest_framework.response import Response
from rest_framework import status

def custom_exception_handler(exc, context):
    """
    Кастомный обработчик исключений для единого формата ошибок.
    """
    # Сначала вызываем стандартный обработчик DRF
    response = exception_handler(exc, context)

    if response is not None and response.status_code == 400:
        # Проверяем, есть ли ошибка уникальности (это уже 409, но на всякий случай)
        if 'isu' in response.data:
            response.status_code = status.HTTP_409_CONFLICT
            response.data = {"error": "Студент с таким ИСУ ID уже существует."}
        else:
            # Форматируем ошибки валидации в единый формат
            response.data = {"error": str(response.data)}
    
    # Если это наша кастомная ValidationError (422)
    elif response is not None and response.status_code == 400:
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        response.data = {"error": str(exc.detail)}

    # Если это непредвиденная ошибка (500 Internal Server Error)
    elif response is None:
        import logging
        logger = logging.getLogger(__name__)
        logger.error(f"Непредвиденная ошибка: {exc}", exc_info=True)
        
        response = Response(
            {"error": "Внутренняя ошибка сервера. Попробуйте позже."},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )

    return response