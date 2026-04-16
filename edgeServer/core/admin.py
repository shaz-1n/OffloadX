from django.contrib import admin
from .models import ComputeTask, ComputeLog


class ComputeLogInline(admin.TabularInline):
    model = ComputeLog
    extra = 0
    readonly_fields = ['level', 'message', 'timestamp']


@admin.register(ComputeTask)
class ComputeTaskAdmin(admin.ModelAdmin):
    list_display = ['id', 'device_id', 'task_type', 'status', 'processing_time_ms', 'received_at']
    list_filter = ['status', 'task_type']
    search_fields = ['device_id', 'id']
    readonly_fields = ['id', 'received_at', 'processing_started_at', 'completed_at']
    inlines = [ComputeLogInline]
