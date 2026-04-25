from django.shortcuts import get_object_or_404
from django_filters import rest_framework as filters
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .models import Document, DocumentCategory
from .serializers import DocumentSerializer, DocumentUploadSerializer


class DocumentFilter(filters.FilterSet):
    """Filter for Document queryset."""

    category_name = filters.CharFilter(field_name="category__name", lookup_expr="icontains")

    class Meta:
        model = Document
        fields = {
            "document_type": ["exact"],
            "category": ["exact"],
            "old_or_new": ["exact"],
            "created_at": ["gte", "lte"],
        }


class DocumentViewSet(viewsets.ModelViewSet):
    """
    ViewSet for managing documents.
    
    Allows users to:
    - List all their documents (with filtering and searching)
    - Create/upload new documents
    - Retrieve document details
    - Update document metadata
    - Delete documents
    """

    permission_classes = [IsAuthenticated]
    parser_classes = (MultiPartParser, FormParser)
    filter_backends = [filters.DjangoFilterBackend]
    filterset_class = DocumentFilter

    def get_queryset(self):
        """Return documents for the current user only."""
        return Document.objects.select_related("category", "user").filter(user=self.request.user)

    def get_serializer_class(self):
        """Use different serializers for different actions."""
        if self.action == "create" or self.action == "upload":
            return DocumentUploadSerializer
        return DocumentSerializer

    def perform_create(self, serializer):
        """Automatically assign the current user to the document."""
        serializer.save(user=self.request.user)

    @action(detail=False, methods=["post"], parser_classes=(MultiPartParser, FormParser))
    def upload(self, request):
        """
        Upload a new document.
        
        Expected fields:
        - name: Document name
        - document_type: Type of document (pdf, word, excel, image, powerpoint, text, other)
        - category: Category for organizing
        - old_or_new: New or Old
        - file: The file to upload
        - description (optional): Additional description
        """
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        self.perform_create(serializer)
        return Response(DocumentSerializer(serializer.instance, context={"request": request}).data, 
                       status=status.HTTP_201_CREATED)

    @action(detail=False, methods=["get"])
    def categories(self, request):
        """Get list of all unique categories used by the user."""
        categories = DocumentCategory.objects.all().values("id", "name")
        return Response({"categories": list(categories)})

    @action(detail=False, methods=["get"])
    def document_types(self, request):
        """Get list of available document types."""
        return Response({"document_types": Document.DocumentType.choices})

    @action(detail=False, methods=["get"])
    def old_or_new_choices(self, request):
        """Get list of old/new choices."""
        return Response({"old_or_new_choices": Document.OldOrNew.choices})

    @action(detail=True, methods=["get"])
    def download(self, request, pk=None):
        """
        Get download URL for a specific document.
        Note: This endpoint returns the URL; actual file serving should be handled by your web server.
        """
        document = self.get_object()
        serializer = self.get_serializer(document)
        return Response(serializer.data)

    @action(detail=False, methods=["get"])
    def statistics(self, request):
        """Get document statistics for the user."""
        queryset = self.get_queryset()
        stats = {
            "total_documents": queryset.count(),
            "by_type": {},
            "by_category": {},
            "by_age": {
                "new": queryset.filter(old_or_new="new").count(),
                "old": queryset.filter(old_or_new="old").count(),
            },
        }

        # Count by document type
        for doc_type, display_name in Document.DocumentType.choices:
            stats["by_type"][display_name] = queryset.filter(document_type=doc_type).count()

        # Count by category
        categories = queryset.values_list("category__name", flat=True).distinct()
        for category in categories:
            if not category:
                continue
            stats["by_category"][category] = queryset.filter(category__name=category).count()

        return Response(stats)
