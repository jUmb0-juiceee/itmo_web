import time
from collections import defaultdict
from threading import Lock

from django.conf import settings
from django.http import JsonResponse


class RateLimitMiddleware:
    """
    Middleware для ограничения частоты запросов.
    Ограничение: 100 запросов в минуту с одного IP.
    """

    # Настройки лимитов
    RATE_LIMIT = 100  # Максимум запросов
    TIME_WINDOW = 60  # Временное окно в секундах (1 минута)
    CLEANUP_INTERVAL = 300  # Как часто вычищать записи по «замолчавшим» IP

    def __init__(self, get_response):
        self.get_response = get_response
        # Словарь для хранения запросов: {ip: [timestamp1, timestamp2, ...]}
        self.request_log = defaultdict(list)
        self.lock = Lock()  # Для потокобезопасности
        self.last_cleanup = time.time()

    def __call__(self, request):
        # Получаем IP клиента
        client_ip = self.get_client_ip(request)

        # Проверяем лимит
        allowed, retry_after = self.check_rate_limit(client_ip)

        if not allowed:
            response = JsonResponse(
                {
                    "error": "Превышен лимит запросов",
                    "details": {
                        "message": f"Не более {self.RATE_LIMIT} запросов в минуту",
                        "retry_after": retry_after,
                    },
                },
                status=429,
            )
            response["Retry-After"] = str(retry_after)
            return response

        # Если лимит не превышен - обрабатываем запрос
        return self.get_response(request)

    def get_client_ip(self, request):
        """
        Получает IP-адрес клиента из запроса.

        X-Forwarded-For учитывается только при явно включённой настройке:
        иначе любой клиент может прислать этот заголовок и обойти лимит.
        """
        if getattr(settings, 'RATE_LIMIT_TRUST_X_FORWARDED_FOR', False):
            x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
            if x_forwarded_for:
                return x_forwarded_for.split(',')[0].strip()

        return request.META.get('REMOTE_ADDR') or 'unknown'

    def check_rate_limit(self, client_ip):
        """
        Проверяет, не превышен ли лимит запросов для данного IP.

        Возвращает (allowed, retry_after):
          allowed     — можно ли обработать запрос;
          retry_after — через сколько секунд освободится слот (0, если можно).
        """
        current_time = time.time()

        with self.lock:  # Потокобезопасность
            self.cleanup_stale_ips(current_time)

            # Удаляем старые запросы (старше TIME_WINDOW)
            timestamps = [
                ts for ts in self.request_log[client_ip]
                if current_time - ts < self.TIME_WINDOW
            ]

            # Проверяем количество запросов
            if len(timestamps) >= self.RATE_LIMIT:
                self.request_log[client_ip] = timestamps
                retry_after = int(self.TIME_WINDOW - (current_time - timestamps[0])) + 1
                return False, max(1, retry_after)

            # Добавляем текущий запрос
            timestamps.append(current_time)
            self.request_log[client_ip] = timestamps
            return True, 0

    def cleanup_stale_ips(self, current_time):
        """
        Периодически удаляет IP, от которых давно не было запросов,
        чтобы словарь не рос бесконечно. Вызывать только под локом.
        """
        if current_time - self.last_cleanup < self.CLEANUP_INTERVAL:
            return

        self.last_cleanup = current_time
        stale_ips = [
            ip for ip, timestamps in self.request_log.items()
            if not timestamps or current_time - timestamps[-1] >= self.TIME_WINDOW
        ]
        for ip in stale_ips:
            del self.request_log[ip]
