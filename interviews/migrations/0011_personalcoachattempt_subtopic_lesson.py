from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("interviews", "0010_personalcoachsession_lesson_catalog"),
    ]

    operations = [
        migrations.AddField(
            model_name="personalcoachattempt",
            name="lesson",
            field=models.CharField(blank=True, max_length=255),
        ),
        migrations.AddField(
            model_name="personalcoachattempt",
            name="subtopic",
            field=models.CharField(blank=True, max_length=255),
        ),
    ]
