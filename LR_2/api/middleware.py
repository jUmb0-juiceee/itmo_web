import time
from collections import defaultdict
from django.http import JsonResponse
from threading import Lock

class RateLimitMiddleware:
    """
    Middleware для ограничения частоты запросов.
    Ограничение: 100 запросов в минуту с одного IP.
    """
    
    # Настройки лимитов
    RATE_LIMIT = 100  # Максимум запросов
    TIME_WINDOW = 60  # Временное окно в секундах (1 минута)
    
    def __init__(self, get_response):
        self.get_response = get_response
        # Словарь для хранения запросов: {ip: [timestamp1, timestamp2, ...]}
        self.request_log = defaultdict(list)
        self.lock = Lock()  # Для потокобезопасности
    
    def __call__(self, request):
        # Получаем IP клиента
        client_ip = self.get_client_ip(request)
        
        # Проверяем лимит
        if not self.check_rate_limit(client_ip):
            return JsonResponse(
                {
                    "error": "Превышен лимит запросов",
                    "details": {
                        "message": f"Не более {self.RATE_LIMIT} запросов в минуту"
                    }
                },
                status=429
            )
        
        # Если лимит не превышен - обрабатываем запрос
        response = self.get_response(request)
        return response
    
    def get_client_ip(self, request):
        """Получает IP-адрес клиента из запроса"""
        x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
        if x_forwarded_for:
            ip = x_forwarded_for.split(',')[0]
        else:
            ip = request.META.get('REMOTE_ADDR')
        return ip
    
    def check_rate_limit(self, client_ip):
        """Проверяет, не превышен ли лимит запросов для данного IP"""
        current_time = time.time()
        
        with self.lock:  # Потокобезопасность
            # Удаляем старые запросы (старше TIME_WINDOW)
            self.request_log[client_ip] = [
                ts for ts in self.request_log[client_ip]
                if current_time - ts < self.TIME_WINDOW
            ]
            
            # Проверяем количество запросов
            if len(self.request_log[client_ip]) >= self.RATE_LIMIT:
                return False
            
            # Добавляем текущий запрос
            self.request_log[client_ip].append(current_time)
            return True