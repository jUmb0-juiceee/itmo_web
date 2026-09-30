from django.db import models

class Student(models.Model):
    # ИСУ ID делаем первичным ключом
    isu = models.IntegerField(primary_key=True, verbose_name="ИСУ ID")
    fio = models.CharField(max_length=200, verbose_name="ФИО")
    group = models.CharField(max_length=20, verbose_name="Группа")
    
    dormitory = models.IntegerField(null=True, blank=True, verbose_name="Общежитие")
    room = models.IntegerField(null=True, blank=True, verbose_name="Комната")
    date = models.DateField(null=True, blank=True, verbose_name="Срок заселения")
    
    is_foreigner = models.BooleanField(default=False, verbose_name="Иностранный студент")
    notes = models.TextField(max_length=200, blank=True, null=True, verbose_name="Заметки")

    def __str__(self):
        return f"{self.fio} ({self.group})"