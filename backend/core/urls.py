from django.urls import path
from .views import (
    ComputeView, TaskStatusView, HubHealthView, FileComputeView, SystemInfoView,
    CloudComputeView, CloudFileComputeView, PerformanceReportView,
)

urlpatterns = [
    path('compute/', ComputeView.as_view(), name='compute'),
    path('upload/', FileComputeView.as_view(), name='file-upload'),
    path('status/<uuid:task_id>/', TaskStatusView.as_view(), name='task-status'),
    path('health/', HubHealthView.as_view(), name='hub-health'),
    path('system-info/', SystemInfoView.as_view(), name='system-info'),
    # Cloud tier endpoints (Apr 10)
    path('cloud/compute/', CloudComputeView.as_view(), name='cloud-compute'),
    path('cloud/upload/', CloudFileComputeView.as_view(), name='cloud-file-upload'),
    # Performance report (cross-tier comparison)
    path('report/', PerformanceReportView.as_view(), name='performance-report'),
]
