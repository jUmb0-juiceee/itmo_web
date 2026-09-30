from django.urls import path
from . import views

urlpatterns = [
    # /api/requests
    path('requests', views.StudentListCreateView.as_view(), name='student-list'),
    # /api/requests/123456
    path('requests/<int:isu>', views.StudentDetailView.as_view(), name='student-detail'),

    # маршрут для QUERY метода
    path('requests-query', views.StudentQueryView.as_view(), name='student-query'),
]