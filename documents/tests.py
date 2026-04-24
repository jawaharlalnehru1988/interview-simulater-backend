from django.test import TestCase
from django.contrib.auth.models import User

from .models import Document


class DocumentModelTest(TestCase):
    """Test cases for the Document model."""

    def setUp(self):
        """Set up test user and documents."""
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpass123'
        )

    def test_document_creation(self):
        """Test creating a document."""
        document = Document.objects.create(
            user=self.user,
            name='Test Document',
            document_type=Document.DocumentType.PDF,
            category='Test Category',
            old_or_new=Document.OldOrNew.NEW,
            file='test_file.pdf'
        )
        self.assertEqual(document.name, 'Test Document')
        self.assertEqual(document.document_type, 'pdf')
        self.assertEqual(document.category, 'Test Category')
        self.assertEqual(document.old_or_new, 'new')

    def test_document_string_representation(self):
        """Test the string representation of a document."""
        document = Document.objects.create(
            user=self.user,
            name='Test PDF',
            document_type=Document.DocumentType.PDF,
            category='Category',
            file='test.pdf'
        )
        self.assertEqual(str(document), 'Test PDF (PDF)')

    def test_user_documents_relation(self):
        """Test the relationship between user and documents."""
        doc1 = Document.objects.create(
            user=self.user,
            name='Document 1',
            document_type=Document.DocumentType.PDF,
            category='Category',
            file='doc1.pdf'
        )
        doc2 = Document.objects.create(
            user=self.user,
            name='Document 2',
            document_type=Document.DocumentType.WORD,
            category='Category',
            file='doc2.docx'
        )
        self.assertEqual(self.user.documents.count(), 2)
        self.assertIn(doc1, self.user.documents.all())
        self.assertIn(doc2, self.user.documents.all())
