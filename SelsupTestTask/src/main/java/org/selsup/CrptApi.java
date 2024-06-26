package org.selsup;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.log4j.BasicConfigurator;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class CrptApi {
    private final CreateDocumentApiClient createDocumentApiClient;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private volatile Date lastReset = new Date();

    public String sendDocument(DocumentRoot documentRoot) throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long nextReset = lastReset.getTime() + timeUnit.toMillis(1);
        if (currentTime >= nextReset) {
            requestCounter.set(0);
            lastReset = new Date(currentTime);
        }

        while (requestCounter.get() >= requestLimit) {
            wait(nextReset - currentTime);
        }

        String httpResponse = createDocumentApiClient.sendRequest(documentRoot);
        requestCounter.incrementAndGet();
        return httpResponse;
    }



    @AllArgsConstructor
    @Getter
    @Setter
    static class DocumentRoot {
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("production_type")
        private String productionType;

        private List<Product> products;

        @JsonProperty("reg_date")
        private String regDate;

        @JsonProperty("reg_number")
        private String regNumber;
    }



    @AllArgsConstructor
    @Getter
    @Setter
    static class Description{
        private String participantInn;
    }



    @AllArgsConstructor
    @Getter
    @Setter
    static class Product{
        @JsonProperty("certificate_document")
        private String certificateDocument;

        @JsonProperty("certificate_document_date")
        private String certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("tnved_code")
        private String tnvedCode;

        @JsonProperty("uit_code")
        private String uitCode;

        @JsonProperty("uitu_code")
        private String uituCode;
    }



    @RequiredArgsConstructor
    @Slf4j
    static abstract class ApiClient<T, R> {
        protected final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

        public abstract R sendRequest(T entity);

        protected String writeJsonBody(T entity) {
            try {
                return new ObjectMapper().writeValueAsString(entity);
            } catch (JsonProcessingException e) {
                log.error("Couldn't transform entity to Json: " + e.getMessage());
                throw new IllegalArgumentException(e);
            }
        }
    }



    static abstract class CreateDocumentApiClient extends ApiClient<DocumentRoot, String> {
        protected static final String CONTENT_TYPE_HEADER = "Content-Type";
        protected static final String CONTENT_TYPE_HEADER_VALUE = "application/json";
        protected static final String AUTH = "AUTH";
        protected static final String AUTH_VALUE = "s3cret_k3y";
    }



    @Slf4j
    static class CreateDocumentApiClientSync extends CreateDocumentApiClient {
        @Override
        public String sendRequest(DocumentRoot documentRoot) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault();
                 CloseableHttpResponse httpResponse = (CloseableHttpResponse) httpClient
                         .execute(preparePostRequestPayload(documentRoot), new CreateDocumentHttpClientResponseHandler()))
            {
                return EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            } catch (IOException e) {
                log.error("API request failed with: " + e.getMessage());
            } catch (ParseException e) {
                log.error("Couldn't read API response:  " + e.getMessage());
            }
            return "API request wasn't performed";
        }

        private HttpPost preparePostRequestPayload(DocumentRoot documentRoot) {
            HttpPost httpPost = new HttpPost(URL);
            StringEntity documentStringEntity = new StringEntity(writeJsonBody(documentRoot));
            httpPost.setEntity(documentStringEntity);
            httpPost.setHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_VALUE);
            httpPost.setHeader(AUTH, AUTH_VALUE);
            return httpPost;
        }
    }



    static class CreateDocumentHttpClientResponseHandler implements HttpClientResponseHandler<ClassicHttpResponse> {
        @Override
        public ClassicHttpResponse handleResponse(ClassicHttpResponse response) {
            return response;
        }
    }



    @Slf4j
    //One more variant of making an API call, but in an asynchronous manner
    static class CreateDocumentApiClientAsync extends CreateDocumentApiClient {

        @Override
        public String sendRequest(DocumentRoot documentRoot) {
            HttpClient.create()
                    .headers(prepareHeaders())
                    .post()
                    .uri(URL)
                    .send(prepareBody(documentRoot))
                    .response()
                    .onErrorComplete(exception -> {
                        log.error(exception.getMessage());
                        throw new IllegalArgumentException();
                    })
                    .subscribe();

            return "API request has been made";

            /*
                Of course, it is unlikely that similar method would return a simple String in real world application,
                because either the context and operations with the stream will be different.

                However, in my use case, this is the only way out, since I wanted to provide an asynchronous alternative
                to the "sendDocument(DocumentRoot documentRoot)" method without breaking the application flow, where the method is called.

                Unfortunately, to incorporate this implementation into the current flow, we will have to block the stream to wait for result.
            */
        }

        private ByteBufMono prepareBody(DocumentRoot documentRoot) {
            return ByteBufMono.fromString(
                    Mono.just(writeJsonBody(documentRoot))
            );
        }

        private Consumer<? super HttpHeaders> prepareHeaders() {
            return h -> h.add(
                    new DefaultHttpHeaders()
                            .add(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_VALUE)
                            .add(AUTH, AUTH_VALUE)
            );
        }
    }



    public static void main(String[] args) {
        BasicConfigurator.configure();

        final int requestLimit = 10;
        CreateDocumentApiClient createDocumentApiClientSync = new CreateDocumentApiClientSync();
        CreateDocumentApiClient createDocumentApiClientAsync = new CreateDocumentApiClientAsync();
        CrptApi crptApi = new CrptApi(createDocumentApiClientAsync, TimeUnit.MINUTES, requestLimit);

        DocumentRoot documentRoot = new DocumentRoot(
                new Description("participantInn"),
                "docId",
                "docStatus",
                "docType",
                true,
                "ownerInn",
                "participantInn",
                "productInn",
                "productionDate",
                "productionType",
                List.of(
                        new Product(
                                "certificateDocument",
                                "certificateDocumentDate",
                                "certificateDocumentNumber",
                                "ownerInn",
                                "producerInn",
                                "productionDate",
                                "tvnedCode",
                                "uidCode",
                                "uituCode"
                        )
                ),
                "regDate",
                "regNumber"
        );

        try {
            System.out.println(crptApi.sendDocument(documentRoot));
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            throw new IllegalStateException();
        }
    }
}