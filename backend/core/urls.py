from django.urls import path
from .views import ComputeView, TaskStatusView, HubHealthView, FileComputeView

urlpatterns = [
    path('compute/', ComputeView.as_view(), name='compute'),
    path('upload/', FileComputeView.as_view(), name='file-upload'),
    path('status/<uuid:task_id>/', TaskStatusView.as_view(), name='task-status'),
    path('health/', HubHealthView.as_view(), name='hub-health'),
]
