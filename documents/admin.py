from django.contrib import admin

from .models import Document


@admin.register(Document)
class DocumentAdmin(admin.ModelAdmin):
    list_display = (
        "id",
        "name",
        "user",
        "document_type",
        "category",
        "old_or_new",
        "created_at",
        "updated_at",
    )
    list_filter = ("document_type", "category", "old_or_new", "created_at", "updated_at")
    search_fields = ("name", "description", "user__username")
    readonly_fields = ("created_at", "updated_at")
    ordering = ("-created_at",)
    list_per_page = 50
    date_hierarchy = "created_at"
    actions = ("mark_as_new", "mark_as_old")

    fieldsets = (
        (
            "Basic Information",
            {"fields": ("user", "name", "description")},
        ),
        (
            "Document Details",
            {"fields": ("document_type", "category", "old_or_new")},
        ),
        (
            "File",
            {"fields": ("file",)},
        ),
        (
            "Timestamps",
            {"fields": ("created_at", "updated_at")},
        ),
    )

    @admin.action(description="Mark selected documents as New")
    def mark_as_new(self, request, queryset):
        queryset.update(old_or_new=Document.OldOrNew.NEW)

    @admin.action(description="Mark selected documents as Old")
    def mark_as_old(self, request, queryset):
        queryset.update(old_or_new=Document.OldOrNew.OLD)
