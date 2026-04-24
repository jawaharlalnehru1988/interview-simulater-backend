# Generated migration file for documents app

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='Document',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(help_text='Document name', max_length=255)),
                ('document_type', models.CharField(choices=[('pdf', 'PDF'), ('word', 'Word Document'), ('excel', 'Excel Spreadsheet'), ('image', 'Image'), ('powerpoint', 'PowerPoint'), ('text', 'Text File'), ('other', 'Other')], default='other', max_length=50)),
                ('category', models.CharField(help_text='Category for organizing documents (e.g., Interview Preparation, Research, etc.)', max_length=255)),
                ('old_or_new', models.CharField(choices=[('new', 'New'), ('old', 'Old')], default='new', max_length=10)),
                ('file', models.FileField(upload_to='documents/%Y/%m/%d/')),
                ('description', models.TextField(blank=True, null=True, help_text='Optional description')),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('user', models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.CASCADE, related_name='documents', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
        migrations.AddIndex(
            model_name='document',
            index=models.Index(fields=['user', '-created_at'], name='documents_d_user_id_created_idx'),
        ),
        migrations.AddIndex(
            model_name='document',
            index=models.Index(fields=['document_type'], name='documents_d_document_type_idx'),
        ),
        migrations.AddIndex(
            model_name='document',
            index=models.Index(fields=['category'], name='documents_d_category_idx'),
        ),
    ]
