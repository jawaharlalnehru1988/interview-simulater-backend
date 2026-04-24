from rest_framework import serializers

from .models import Document


class DocumentSerializer(serializers.ModelSerializer):
    document_type_display = serializers.CharField(
        source="get_document_type_display", read_only=True
    )
    old_or_new_display = serializers.CharField(
        source="get_old_or_new_display", read_only=True
    )
    file_url = serializers.SerializerMethodField()

    class Meta:
        model = Document
        fields = [
            "id",
            "name",
            "document_type",
            "document_type_display",
            "category",
            "old_or_new",
            "old_or_new_display",
            "file",
            "file_url",
            "description",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "created_at", "updated_at", "file_url"]

    def get_file_url(self, obj):
        """Return the absolute URL of the file."""
        request = self.context.get("request")
        if obj.file and request:
            return request.build_absolute_uri(obj.file.url)
        return obj.file.url if obj.file else None


class DocumentUploadSerializer(serializers.ModelSerializer):
    """Serializer for file upload with validation."""

    class Meta:
        model = Document
        fields = ["name", "document_type", "category", "old_or_new", "file", "description"]

    def validate_file(self, value):
        """Validate file size (max 50MB)."""
        max_size = 50 * 1024 * 1024  # 50MB
        if value.size > max_size:
            raise serializers.ValidationError(
                f"File size exceeds 50MB limit. Current size: {value.size / (1024*1024):.2f}MB"
            )
        return value
