package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    // Ссылка к api Честного знака для создания документа на ввод
    //товара в оборот, произведенного на территории РФ в виде константы.
    private final static String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final RequestsLimiter requestsLimiter;
    private final HttpClient httpClient;
    private final ObjectMapper om;

    /** Конструктор класса {@link CrptApi} для отправки документа
     * на ввод товара в оборот, произведенного на территории РФ,
     * в Честный знак.
     * Доступные методы: {@link #sendDocument(Document, String)}, {@link #sendRequest(String, String)}
     *
     * @param timeUnit промежуток времени {@link TimeUnit}
     * @param requestLimit количество запросов в заданном промежутке времени {@code timeUnit}
     */
    public CrptApi(final TimeUnit timeUnit, final int requestLimit) {
        this.requestsLimiter = new RequestsLimiter(timeUnit, requestLimit);
        this.httpClient = HttpClient.newHttpClient();
        this.om = new ObjectMapper();

    }

    /** Метод для отправки документа на ввод товара в оборот, произведенного на территории РФ,
     * в Честный знак.
     *
     * @param document документ в формате {@link Document}
     * @param signature электронная подпись отправителя в виде строки
     * @throws InterruptedException прерывается операция
     */
    public void sendDocument(final Document document, final String signature) throws InterruptedException {

        try {
            requestsLimiter.acquire();

            String documentJson = om.writeValueAsString(document);
            HttpResponse<String> response = sendRequest(documentJson, signature);

            if (response.statusCode() != 200) {
                throw new IOException("Failed to create document at CRPT: " + response.body());
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while serialized document to json. " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            requestsLimiter.release();
        }

    }

    /**
     * Метод для отправки HTTP POST запроса.
     *
     * @param documentJson документ в формате Json
     * @param signature электронная подпись отправителя в виде строки
     * @return возвращает ответ {@link HttpResponse} на сделанный запрос
     * @throws IOException если ошибка ввода-вывода возникает при отправке или получения
     * @throws InterruptedException если прерывается операция
     */
    private HttpResponse<String> sendRequest(final String documentJson, final String signature)
                                            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
            // не знал куда вставить подпись, насколько знаю, она нужна для получения токена
            // и вот токен уже нужно указывать в запросах в виде Authorization header
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(documentJson))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    }

    /**
     * Класс для ограничения количества запросов в заданный промежуток времени.
     * Реализован на базе {@link Semaphore}.
     * Доступные методы:
     * {@link #acquire()}, {@link #release()}
     */
    private static class RequestsLimiter {

        private final Semaphore semaphore;

        /**
         * Создание {@code RequestsLimiter} с указанным промежутком времени и
         * количеством разрешений
         *
         * @param timeUnit промежуток времени
         * @param requestLimit количество запросов в заданном промежутке времени {@code timeUnit}
         */
        public RequestsLimiter(final TimeUnit timeUnit, final int requestLimit) {
            this.semaphore = new Semaphore(requestLimit);
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

            executorService.scheduleAtFixedRate(
                    () -> {
                        semaphore.drainPermits();           // получаем информацию о текущем состоянии семафора
                        semaphore.release(requestLimit);    // освобождаем ресурсы
                    },
                    0,
                    timeUnit.toMillis(1),
                    timeUnit
            );

        }

        /**
         * Получение разрешения от {@link RequestsLimiter}, блокируя его до тех пор, пока оно не будет доступно,
         * или поток не будет прерван.
         *
         * @throws RuntimeException поток прерван при освобождении ресурсов
         */
        public void acquire() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // устанавливаем флаг прерывания текущего потока
                throw new RuntimeException("The thread is interrupted while acquiring semaphore. " + e.getMessage());
            }
        }

        /**
         * Освобождение разрешения, возвращая в {@link RequestsLimiter}
         */
        public void release() {
            semaphore.release();
        }

    }

    /**
     * Документ на ввод товара в оборот, произведенного на территории РФ,
     * со следующими параметрами
     *
     * @param description описание документа {@link Document}
     * @param doc_id идентификатор документа
     * @param doc_status статус документа
     * @param doc_type тип документа {@link DOCUMENT_TYPE}
     * @param importRequest признак импорта
     * @param owner_inn ИНН собственника
     * @param participant_inn ИНН участника оборота
     * @param producer_inn ИНН производителя
     * @param production_date Дата производства товара
     * @param production_type Тип производственного заказа
     * @param products Перечень товаров {@link Product}
     * @param reg_date Дата и время регистрации
     * @param reg_number Регистрационный номер документа
     */
    public record Document (
            Description description,
            String doc_id,
            String doc_status,
            DOCUMENT_TYPE doc_type,
            String importRequest,
            String owner_inn,
            String participant_inn,
            String producer_inn,
            String production_date,
            String production_type,
            Product[] products,
            String reg_date,
            String reg_number
    ) {}

    /**
     * Модель Описания, со следующими параметрами
     *
     * @param participantInn ИНН участника оборота товаров
     */
    public record Description(
            String participantInn
    ) {}

    /**
     * Модель Продукта, со следующими параметрами
     *
     * @param certificate_document Код вида документа
     * @param certificate_document_date Дата документа
     * @param certificate_document_number Номер документа
     * @param owner_inn ИНН собственника товар
     * @param producer_inn ИНН производителя товара
     * @param production_date Дата производства товара
     * @param tnved_code Код ТНВЭД
     * @param uit_code Уникальный идентификатор товара
     * @param uitu_code Уникальный идентификатор транспортной упаковки
     */
    public record Product(
            String certificate_document,
            String certificate_document_date,
            String certificate_document_number,
            String owner_inn,
            String producer_inn,
            String production_date,
            String tnved_code,
            String uit_code,
            String uitu_code
    ) {}

    /**
     * Типы документов в виде Enum
     */
    enum DOCUMENT_TYPE {
        LP_INTRODUCE_GOODS
    }

}