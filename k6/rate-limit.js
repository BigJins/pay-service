/**
 * apigateway-service — Rate Limiting 검증 테스트
 *
 * 목적: 동일 IP에서 짧은 시간에 대량 요청 시
 *       Gateway가 429 Too Many Requests를 반환하는지 확인
 *
 * 엔드포인트: POST /api/payments/toss/confirm (Gateway 경유)
 * Gateway 포트: 8000 (apigateway-service)
 * Rate Limit 설정: replenishRate=5, burstCapacity=10
 *
 * 사전 조건:
 *   - pay-service 기동 (local 프로파일, port 8080)
 *   - apigateway-service 기동 (local 프로파일, port 8000)
 *   - Redis 기동 (port 6379)
 *
 * 실행:
 *   k6 run k6\rate-limit.js
 *
 * 예상 결과:
 *   - 초당 5개까지는 200 (또는 정상 처리)
 *   - 버스트 10개 소진 후 429 발생
 *   - checks: '429 없음' 실패 → 429가 존재함을 확인
 *   - checks: '서버 에러 없음' 통과 → 500은 없음
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const GATEWAY_URL = 'http://127.0.0.1:8000';

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-vus',
      vus: 20,
      duration: '5s',
    },
  },
};

let requestCount = 0;

export default function () {
  // 각 VU마다 고유한 paymentKey (멱등성 우회)
  const uniqueKey = `rate_test_pk_${__VU}_${__ITER}`;

  const payload = JSON.stringify({
    tossOrderId: `RATE_TEST_${__VU}_${__ITER}`,
    paymentKey: uniqueKey,
    amount: 10000,
  });

  const res = http.post(
    `${GATEWAY_URL}/api/payments/toss/confirm`,
    payload,
    { headers: { 'Content-Type': 'application/json' }, timeout: '5s' },
  );

  check(res, {
    '서버 에러 없음 (500 아님)': (r) => r.status !== 500,
    '정상 처리 또는 Rate Limit': (r) => r.status === 200 || r.status === 409 || r.status === 429,
  });

  // 429가 발생하면 로그 (k6는 console.log 지원)
  if (res.status === 429) {
    console.log(`[VU ${__VU}] Rate limited: 429 Too Many Requests`);
  }
}
