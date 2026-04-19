from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("interviews", "0009_candidateprofile_hrvoiceinterviewsession_and_more"),
    ]

    operations = [
        migrations.AddField(
            model_name="personalcoachsession",
            name="lessons_by_subtopic",
            field=models.JSONField(default=dict),
        ),
        migrations.AddField(
            model_name="personalcoachsession",
            name="selected_lesson",
            field=models.CharField(blank=True, max_length=255),
        ),
    ]
