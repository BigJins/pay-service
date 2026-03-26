# Spring Cloud Config Server — 런타임 설정 관리

## 왜 필요한가?

**문제**: Toss API 재시도 횟수나 타임아웃을 바꾸려면 JAR를 다시 빌드하고 배포해야 한다.

```
변경 전: 재시도 2회, 백오프 500ms, 타임아웃 15초 — 코드 하드코딩
배포 없이 바꾸고 싶다 → Config Server
```

**해결**: Config Server가 설정 파일을 관리하고, 서비스는 기동 시 + 런타임에 설정을 가져온다.

---

## 구성 흐름

```
C:/newpractice/configs/      ← 설정 파일 (filesystem backend)
    application.yml          ← 전체 공통
    pay-service.yml          ← pay-service 전용 (toss.retry.*)
    order-service.yml        ← order-service 전용

         ↓ 서비스 기동 시 HTTP로 읽기
config-server (port 8888)
         ↓
pay-service / order-service
```

---

## 런타임 설정 변경 시나리오

### Before (하드코딩)
```java
// TossPaymentsRestAdapter.java — 코드 수정 → 재배포 필요
.retryWhen(Retry.backoff(2, Duration.ofMillis(500))...)
.block(Duration.ofSeconds(15));
```

### After (Config Server 연동)
```yaml
# C:/newpractice/configs/pay-service.yml
toss:
  retry:
    max-attempts: 2    ← 파일만 수정하면 됨
    backoff-ms: 500
  timeout-seconds: 15
```

```java
// TossPaymentsRestAdapter.java — 설정값 주입
.retryWhen(Retry.backoff(
    tossRetryProperties.getRetry().getMaxAttempts(),
    Duration.ofMillis(tossRetryProperties.getRetry().getBackoffMs()))...)
.block(Duration.ofSeconds(tossRetryProperties.getTimeoutSeconds()));
```

### 런타임 반영 (재시작 없음)
```bash
# 1. configs/pay-service.yml 수정 (예: max-attempts: 3으로 변경)

# 2. pay-service에 refresh 요청
curl -X POST http://localhost:8080/actuator/refresh

# 3. 즉시 반영 — 다음 Toss API 호출부터 새 설정 사용
```

---

## @ConfigurationProperties 자동 리바인딩

`TossRetryProperties`는 `@ConfigurationProperties("toss")` 클래스.
Spring Cloud는 `/actuator/refresh` 호출 시 Environment를 재로드하고,
`@ConfigurationProperties` 빈을 자동으로 새 값으로 리바인딩한다.
→ `@RefreshScope` 없이도 재시작 없이 값이 갱신된다.

---

## 실제 테스트

```bash
# config-server 기동
cd C:/newpractice/config-server
./gradlew bootRun

# 설정 확인
curl http://localhost:8888/pay-service/default
# → toss.retry.max-attempts=2, toss.retry.backoff-ms=500, toss.timeout-seconds=15

# pay-service 기동
cd C:/newpractice/pay-service
./gradlew bootRun --args='--spring.profiles.active=local'

# configs/pay-service.yml에서 max-attempts: 5로 변경 후
curl -X POST http://localhost:8080/actuator/refresh
# → ["toss.retry.max-attempts"] 반환 (변경된 키 목록)

# 다음 호출부터 재시도 5회로 동작
```

---

## optional: 의미

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

`optional:` 접두사 덕분에 Config Server가 꺼져 있어도 서비스가 기동된다.
→ local 개발 시 Config Server 없이도 application-local.yml 설정으로 정상 동작.

---

## 면접 포인트

| 질문 | 답변 |
|------|------|
| Config Server를 왜 쓰나? | 배포 없이 런타임에 설정 변경 가능 — feature flag, timeout 조정, retry 튜닝 |
| @RefreshScope vs @ConfigurationProperties refresh | @ConfigurationProperties는 자동 리바인딩. @RefreshScope는 @Value 필드나 빈 자체를 새로 생성할 때 |
| optional:configserver가 없으면? | Config Server 미기동 시 서비스 기동 실패 — local 개발 불편 |
| native vs git backend | native=파일시스템 직접 읽기(로컬 개발용), git=버전 관리+audit trail(운영 권장) |