# Selsup test task

API client

## Task Requirements

Implement a class using Java (you can use 17th version) to work with Chestniy Znak API. The class has to be thread-safe and has to be able to limit amout of requests to the API within specified time period. The limitation has to be applied through the constructor:
**public CrptApi(TimeUnit timeUnit, int requestLimit)**
* timeUnit - the time period (second, minute, hour etc)
* requestLimit - max amount of requests to be performed within the specified time period. 

If the amount of request is above the limit, the call must be blocked in order not to exceed the maximum number of API requests and continue execution, without throwing an exception when the limit of calls is not exceeded. In any situation, it is forbidden for the method to call the API exceeding the limit.

You have to implement only one method - Document creation to start vending goods produced on the territory of Russian Federation. The document and the signature are passed to the method as an object and a string accordingly.

**HTTP POST -> https://ismp.crpt.ru/api/v3/lk/documents/create**

Json body:
{"description":{ "participantInn": "string" }, "doc_id": "string", "doc_status": "string", "doc_type": "LP_INTRODUCE_GOODS", "importRequest": true, "owner_inn": "string", "participant_inn": "string", "producer_inn": "string", "production_date": "2020-01-23", "production_type": "string", "products": [ { "certificate_document": "string", "certificate_document_date": "2020-01-23", "certificate_document_number": "string", "owner_inn": "string", "producer_inn": "string", "production_date": "2020-01-23", "tnved_code": "string", "uit_code": "string", "uitu_code": "string" } ], "reg_date": "2020-01-23", "reg_number": "string"}

You can use HTTP client libraries, Json serialization libraries. 

The implementation has be as convenient as possible for further extention of the functionality.

The program has be written in a single file CrptApi.java. All additional classes that are used must be internal.