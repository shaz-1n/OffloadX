from django.urls import path
from .views import ComputeView, TaskStatusView, HubHealthView

urlpatterns = [
    path('compute/', ComputeView.as_view(), name='compute'),
    path('status/<uuid:task_id>/', TaskStatusView.as_view(), name='task-status'),
    path('health/', HubHealthView.as_view(), name='hub-health'),
]
