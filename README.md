# Тестовое задание CrptApi

Краткое описание:
Необходимо реализовать на языке Java (можно использовать 17 версию) класс для работы с API Честного знака. Класс должен быть
thread-safe и поддерживать ограничение на количество запросов к API. 

# Описание работы
Реализован внутренний класс для контроля количества запросов к Api в указанный промежуток времени, использует `Semaphore` и `ExecutorService`.
```java
public RequestsLimiter(final TimeUnit timeUnit, final int requestLimit)
```
# Использование
Создать экземпляр класса `CrptApi`, передав в качестве параметров временной интервал `TimeUnit` и количество запросов в данный интервал.
Пример:
```java
CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 2)
```

Для отправки документа необходимо использовать метод `sendDocument`, в который необходимо передать в качестве параметров 
документ в виде объекта `Document` и электронную подпись отправителя в виде `String`.

Пример:
```java
Document document = new Document("A", "B" ...);
String sig = "ABCDEFGHIJKLMNOP#1234567890"

crptApi.sendDocument(document, sig);

```
