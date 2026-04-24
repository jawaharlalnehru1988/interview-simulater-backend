from django.conf import settings
from django.db import models


class Document(models.Model):
    class DocumentType(models.TextChoices):
        PDF = "pdf", "PDF"
        WORD = "word", "Word Document"
        EXCEL = "excel", "Excel Spreadsheet"
        IMAGE = "image", "Image"
        POWERPOINT = "powerpoint", "PowerPoint"
        TEXT = "text", "Text File"
        OTHER = "other", "Other"

    class OldOrNew(models.TextChoices):
        NEW = "new", "New"
        OLD = "old", "Old"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="documents",
        null=True,
        blank=True,
    )
    name = models.CharField(max_length=255, help_text="Document name")
    document_type = models.CharField(
        max_length=50,
        choices=DocumentType.choices,
        default=DocumentType.OTHER,
    )
    category = models.CharField(
        max_length=255,
        help_text="Category for organizing documents (e.g., Interview Preparation, Research, etc.)",
    )
    old_or_new = models.CharField(
        max_length=10,
        choices=OldOrNew.choices,
        default=OldOrNew.NEW,
    )
    file = models.FileField(upload_to="documents/%Y/%m/%d/")
    description = models.TextField(blank=True, null=True, help_text="Optional description")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["user", "-created_at"]),
            models.Index(fields=["document_type"]),
            models.Index(fields=["category"]),
        ]

    def __str__(self):
        return f"{self.name} ({self.get_document_type_display()})"
