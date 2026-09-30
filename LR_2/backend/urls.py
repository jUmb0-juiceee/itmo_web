from django.contrib import admin
from django.urls import path, include

urlpatterns = [
    path('admin/', admin.site.urls),
    # Все запросы, начинающиеся с /api/, будут передаваться в наше приложение api
    path('api/', include('api.urls')),
]