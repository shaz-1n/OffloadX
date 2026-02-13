from rest_framework import serializers
from .models import ComputeTask, ComputeLog


class ComputeLogSerializer(serializers.ModelSerializer):
    class Meta:
        model = ComputeLog
        fields = ['level', 'message', 'timestamp']


class ComputeTaskSerializer(serializers.ModelSerializer):
    logs = ComputeLogSerializer(many=True, read_only=True)

    class Meta:
        model = ComputeTask
        fields = [
            'id', 'device_id', 'task_type', 'status',
            'received_at', 'processing_started_at', 'completed_at',
            'processing_time_ms', 'firebase_result_path',
            'error_message', 'logs',
        ]
        read_only_fields = [
            'id', 'status', 'received_at', 'processing_started_at',
            'completed_at', 'processing_time_ms', 'firebase_result_path',
            'error_message',
        ]


class ComputeRequestSerializer(serializers.Serializer):
    """
    Validates incoming compute requests from the mobile app.
    The 'data' field carries the payload to be processed.
    """
    device_id = serializers.CharField(max_length=255)
    task_type = serializers.ChoiceField(choices=['COMPOSITE', 'COMPLEX'])
    data = serializers.JSONField(help_text="The payload to process (e.g., numbers, matrices, etc.)")
