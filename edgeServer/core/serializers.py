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
            'processing_time_ms', 'firebase_result_path', 'processed_result_url',
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


class FileComputeRequestSerializer(serializers.Serializer):
    """
    Validates incoming file upload requests from the mobile app.

    - image_mode  : controls image processing pipeline
    - pdf_mode    : controls PDF processing pipeline
    - text_mode   : controls text/CSV/JSON analysis pipeline
    - video_mode  : controls video analytics pipeline
    """
    device_id  = serializers.CharField(max_length=255)
    file       = serializers.FileField()
    task_type  = serializers.ChoiceField(choices=['IMAGE_GRAYSCALE'])

    image_mode = serializers.ChoiceField(
        choices=[
            'GRAYSCALE',        # Convert to greyscale
            'OBJECT_DETECTION', # Haar Cascade face/object detection
            'EDGE_DETECT',      # Canny edge detection
            'BLUR',             # Gaussian blur
            'SHARPEN',          # Unsharp mask sharpening
            'SEPIA',            # Warm sepia tone
            'INVERT',           # Colour inversion (negative)
        ],
        default='GRAYSCALE',
        required=False,
    )

    pdf_mode = serializers.ChoiceField(
        choices=[
            'ANALYZE',       # Word / page count + metadata
            'TEXT_EXTRACT',  # Pull all readable text
            'STORE',         # Save original, return download URL
        ],
        default='ANALYZE',
        required=False,
    )

    text_mode = serializers.ChoiceField(
        choices=[
            'WORD_COUNT',    # Words, lines, character count
            'KEYWORD_FREQ',  # Top-20 keyword frequency
            'SENTIMENT',     # Positive / negative sentiment ratio
            'STORE',         # Save original, return download URL
        ],
        default='WORD_COUNT',
        required=False,
    )

    video_mode = serializers.ChoiceField(
        choices=[
            'FACE_DETECTION',  # Haar Cascade ML face tracking analytics
            'FRAME_ANALYTICS', # Edge density / complexity per frame
            'THUMBNAIL',       # Extract first clear frame as thumbnail
            'PASSTHROUGH',     # Store video, return download URL
        ],
        default='FACE_DETECTION',
        required=False,
    )
