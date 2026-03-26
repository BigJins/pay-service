# RestTemplate → WebClient 전환 학습 문서

## 왜 바꾸는가

RestTemplate은 **동기 블로킹** 방식이다.
Toss API를 호출하면 응답이 올 때까지 해당 스레드가 멈춘다.

```
요청 스레드 ──────[Toss API 대기 200ms]──────▶ 응답 처리
            ↑
     이 구간 동안 스레드가 아무것도 못 하고 대기
```

동시 요청이 100개면 100개 스레드가 전부 Toss 응답을 기다리며 멈춰있다.
WebClient는 **논블로킹** 방식이라 I/O 대기 중 스레드를 다른 작업에 쓸 수 있다.

---

## Config 변경

### Before — RestTemplate Bean 생성

```java
@Profile("!local")
@Configuration
public class TossClientConfig {

    @Bean
    public RestTemplate tossRestTemplate() {
        return new RestTemplate();  // 타임아웃 설정 없음
    }
}
```

### After — WebClient Bean 생성

```java
@Profile("!local")
@Configuration
public class TossClientConfig {

    @Bean
    public WebClient tossWebClient(TossProperties tossProperties) {

        // Netty HttpClient로 저수준 타임아웃 설정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)  // 연결 타임아웃 3초
                .responseTimeout(Duration.ofSeconds(10));               // 응답 타임아웃 10초

        return WebClient.builder()
                .baseUrl(tossProperties.getBaseUrl())  // 기본 URL 주입 (uri()에서 path만 쓸 수 있게)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

**포인트:**
- `HttpClient.create()` — Netty 기반 HTTP 클라이언트 설정
- `CONNECT_TIMEOUT_MILLIS` — TCP 연결 맺는 시간 제한 (서버가 응답 없을 때)
- `responseTimeout` — 응답 body를 받기까지 최대 대기 시간
- `baseUrl` — 한 번만 설정하면 `uri()`에서 path만 써도 됨

---

## Adapter 변경

### Before — RestTemplate 방식

```java
@Component
@RequiredArgsConstructor
public class TossPaymentsRestAdapter implements TossPayments {

    private final TossProperties tossProperties;
    private final RestTemplate tossRestTemplate;   // ← RestTemplate 주입
    private final ObjectMapper objectMapper;

    @Override
    public TossConfirmResult confirm(PaymentReadyRequest request, String paymentKey) {
        String url = tossProperties.getBaseUrl() + "/v1/payments/confirm";

        // 헤더를 매번 직접 조립
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.getSecretKey()));

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", request.tossOrderId(),
                "amount", request.amount()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            // 동기 블로킹 호출 — 응답 올 때까지 스레드 점유
            ResponseEntity<String> response = tossRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class
            );

            String raw = response.getBody();
            String tossStatus = extractStatus(raw);
            boolean isApproved = "DONE".equalsIgnoreCase(tossStatus);

            return isApproved
                    ? TossConfirmResult.approved(tossStatus, raw)
                    : TossConfirmResult.failed(tossStatus, raw);

        } catch (HttpStatusCodeException e) {
            // 4xx/5xx 응답은 예외로 던져짐 → catch에서 처리
            String raw = e.getResponseBodyAsString();
            String tossStatus = extractStatus(raw);
            return TossConfirmResult.failed(
                    tossStatus != null ? tossStatus : "FAILED", raw
            );
        } catch (Exception e) {
            String raw = "{\"error\":\"" + e.getClass().getSimpleName() + ":" + e.getMessage() + "\"}";
            return TossConfirmResult.failed("FAILED", raw);
        }
    }
}
```

**문제점:**
1. `tossRestTemplate.exchange()` — 응답까지 스레드 블로킹
2. 4xx/5xx가 **예외**로 처리됨 → catch 분기가 별도 필요
3. 타임아웃 설정이 없어 Toss 서버 장애 시 무한 대기 가능
4. 재시도 로직 없음

---

### After — WebClient 방식

```java
@Component
@RequiredArgsConstructor
public class TossPaymentsRestAdapter implements TossPayments {

    private final TossProperties tossProperties;
    private final WebClient tossWebClient;         // ← WebClient 주입
    private final ObjectMapper objectMapper;

    @Override
    public TossConfirmResult confirm(PaymentReadyRequest request, String paymentKey) {
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", request.tossOrderId(),
                "amount", request.amount()
        );

        try {
            String raw = tossWebClient.post()
                    .uri("/v1/payments/confirm")            // baseUrl이 이미 설정됨
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.getSecretKey()))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)

                    // exchangeToMono: 성공/실패 응답 모두 body를 그대로 받음
                    // retrieve() 대신 쓰는 이유: retrieve()는 4xx/5xx를 예외로 던짐
                    .exchangeToMono(response ->
                            response.bodyToMono(String.class).defaultIfEmpty("")
                    )

                    // retryWhen: 네트워크 오류(연결 실패, 타임아웃)일 때만 재시도
                    // WebClientRequestException = 네트워크 레벨 오류 (4xx/5xx 제외)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(e -> e instanceof WebClientRequestException))

                    // block(): 리액티브 파이프라인을 동기 결과로 변환
                    // Spring MVC 컨텍스트이므로 여기서는 block() 사용
                    // Spring WebFlux로 전환하면 Mono<TossConfirmResult>를 반환할 수 있음
                    .block(Duration.ofSeconds(15));

            String tossStatus = extractStatus(raw);
            boolean isApproved = "DONE".equalsIgnoreCase(tossStatus);

            return isApproved
                    ? TossConfirmResult.approved(tossStatus, raw)
                    : TossConfirmResult.failed(tossStatus != null ? tossStatus : "FAILED", raw);

        } catch (Exception e) {
            String raw = "{\"error\":\"" + e.getClass().getSimpleName() + ":" + e.getMessage() + "\"}";
            return TossConfirmResult.failed("FAILED", raw);
        }
    }
}
```

---

## 핵심 개념 정리

### retrieve() vs exchangeToMono()

```java
// retrieve() — 4xx/5xx를 WebClientResponseException으로 던짐
.retrieve()
.bodyToMono(String.class)
// → 실패 응답을 onStatus()나 catch로 별도 처리해야 함

// exchangeToMono() — 상태코드 무관하게 응답을 직접 처리
.exchangeToMono(response -> response.bodyToMono(String.class))
// → 성공이든 실패든 body를 그대로 받음
// → Toss API는 실패 응답도 JSON body를 줌 → 이 방식이 적합
```

### retryWhen() — 재시도 조건

```java
.retryWhen(
    Retry.backoff(2, Duration.ofMillis(500))  // 최대 2회, 첫 대기 500ms (지수 증가)
         .filter(e -> e instanceof WebClientRequestException)
         // WebClientRequestException = 네트워크 오류 (DNS 실패, 연결 거부, 타임아웃)
         // 4xx/5xx HTTP 응답은 재시도 안 함 (의도적 거절이므로)
)
```

| 오류 종류 | 재시도 여부 | 이유 |
|----------|------------|------|
| 연결 실패 (ECONNREFUSED) | ✅ 재시도 | 일시적 네트워크 문제 |
| 응답 타임아웃 | ✅ 재시도 | 일시적 지연 |
| 400 Bad Request | ❌ 재시도 안 함 | 요청 자체가 잘못됨 |
| 401 Unauthorized | ❌ 재시도 안 함 | 인증 문제, 재시도해도 동일 |

### block() — 왜 쓰는가

```java
// WebClient는 기본적으로 Mono<T>를 반환 (비동기)
Mono<String> mono = tossWebClient.post()...exchangeToMono(...);

// Spring MVC 컨트롤러는 동기 반환을 기대
// Mono를 동기 값으로 변환하려면 block() 필요
String raw = mono.block(Duration.ofSeconds(15));

// Spring WebFlux로 전환하면 block() 없이 Mono를 반환할 수 있음
// 그러면 진정한 논블로킹이 됨
```

### 타임아웃 설정 위치

```
Config에서 설정한 타임아웃:
  CONNECT_TIMEOUT = 3s  → TCP 연결 자체를 맺는 시간
  responseTimeout = 10s → 응답 body 수신까지 시간

block()에서 설정한 타임아웃:
  block(Duration.ofSeconds(15)) → 리액티브 파이프라인 전체 최대 대기

결과적으로: 네트워크 오류 + 재시도 2회까지 포함해서 최대 15초
```

---

## build.gradle.kts 변경

```kotlin
// 추가
implementation("org.springframework.boot:spring-boot-starter-webflux")

// 기존 유지 (MVC 제거하지 않음)
implementation("org.springframework.boot:spring-boot-starter-webmvc")
```

Spring Boot는 webmvc와 webflux가 동시에 있으면 **MVC를 우선**한다.
WebClient만 쓰기 위해 webflux를 추가하는 것이므로 서버는 여전히 Tomcat이다.
Netty 서버로 완전히 전환하려면 webmvc를 제거해야 한다.