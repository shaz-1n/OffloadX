from django.db import models
import uuid

# =============================================================================
# OffloadX Computational Node Models
# =============================================================================
# These models are lightweight and TEMPORARY. The Hub (laptop) is a
# Computational Node -- it does NOT act as a primary storage node.
#
# Architecture:
#   - SQLite (on device)  : Performance logs, simple local data
#   - Firebase (cloud)    : Persistent, accessible data storage
#   - Laptop Hub (here)   : Real-time computation via Django
# =============================================================================


class ComputeTask(models.Model):
    """
    Tracks a computation request while it is being processed.
    This is ephemeral -- entries can be cleaned up after completion.
    """
    STATUS_CHOICES = [
        ('RECEIVED', 'Received'),
        ('PROCESSING', 'Processing'),
        ('COMPLETED', 'Completed'),
        ('FAILED', 'Failed'),
    ]

    TYPE_CHOICES = [
        ('COMPOSITE', 'Composite'),
        ('COMPLEX', 'Complex'),
    ]

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    device_id = models.CharField(max_length=255, help_text="Identifier of the requesting device")
    task_type = models.CharField(max_length=20, choices=TYPE_CHOICES)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='RECEIVED')

    # Timing metrics for performance comparison (local vs hub)
    received_at = models.DateTimeField(auto_now_add=True)
    processing_started_at = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    processing_time_ms = models.FloatField(null=True, blank=True, help_text="Processing duration in milliseconds")

    # Firebase reference where the result was pushed
    firebase_result_path = models.CharField(max_length=512, blank=True, default='',
                                            help_text="Firestore/Storage path where result was saved")

    error_message = models.TextField(blank=True, default='')

    class Meta:
        ordering = ['-received_at']

    def __str__(self):
        return f"ComputeTask {self.id} [{self.status}] - {self.processing_time_ms}ms"


class ComputeLog(models.Model):
    """
    Lightweight log entries for debugging and performance tracking.
    These stay on the hub temporarily and can be pushed to Firebase.
    """
    task = models.ForeignKey(ComputeTask, on_delete=models.CASCADE, related_name='logs')
    level = models.CharField(max_length=10, default='INFO')  # INFO, WARN, ERROR
    message = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['timestamp']

    def __str__(self):
        return f"[{self.level}] {self.message[:60]}"
