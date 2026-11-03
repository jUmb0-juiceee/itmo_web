# api/urls.py
from django.urls import path
from . import views

urlpatterns = [
    # Эндпоинт для работы со списком студентов
    path('requests', views.StudentListCreateView.as_view(), name='student-list'),
    
    # Эндпоинт для работы с конкретным студентом по его ИСУ ID
    path('requests/<int:isu>', views.StudentDetailView.as_view(), name='student-detail'),
]