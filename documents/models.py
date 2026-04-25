from django.conf import settings
from django.db import models
from django.db.models.signals import post_delete, pre_save
from django.dispatch import receiver


class DocumentCategory(models.Model):
    name = models.CharField(max_length=255, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["name"]
        verbose_name = "Document Category"
        verbose_name_plural = "Document Categories"

    def __str__(self):
        return self.name


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
    category = models.ForeignKey(
        DocumentCategory,
        on_delete=models.SET_NULL,
        related_name="documents",
        null=True,
        blank=True,
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


@receiver(post_delete, sender=Document)
def auto_delete_document_file_on_delete(sender, instance, **kwargs):
    if not instance.file:
        return

    # Keep shared files if another row still points to the same storage path.
    in_use_elsewhere = Document.objects.filter(file=instance.file.name).exclude(pk=instance.pk).exists()
    if in_use_elsewhere:
        return

    if instance.file.storage.exists(instance.file.name):
        instance.file.storage.delete(instance.file.name)


@receiver(pre_save, sender=Document)
def auto_delete_document_file_on_change(sender, instance, **kwargs):
    if not instance.pk:
        return

    try:
        old_instance = Document.objects.get(pk=instance.pk)
    except Document.DoesNotExist:
        return

    old_file = old_instance.file
    new_file = instance.file

    if not old_file or old_file == new_file:
        return

    in_use_elsewhere = Document.objects.filter(file=old_file.name).exclude(pk=instance.pk).exists()
    if in_use_elsewhere:
        return

    if old_file.storage.exists(old_file.name):
        old_file.storage.delete(old_file.name)
