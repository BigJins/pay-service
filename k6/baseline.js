/**
 * pay-service — 처리량 / 응답시간 Baseline 측정
 *
 * 목적: Spring Batch Outbox relay 도입 전후 처리량 비교를 위한 기준 수치 확보
 *       (hikaricp_connections_active, http_req_duration P95/P99)
 *
 * 엔드포인트: POST /api/payments/toss/confirm
 * 요청 DTO : ConfirmCommand { tossOrderId: string, paymentKey: string, amount: number }
 *
 * 사전 조건:
 *   pay-service 기동: ./gradlew bootRun --args='--spring.profiles.active=local'
 *   Grafana 오픈 후 hikaricp_connections_active 패널 준비
 *
 * 실행:
 *   k6 run k6/baseline.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://127.0.0.1:8080';

export const options = {
  summaryTrendStats: ['med', 'p(90)', 'p(95)', 'p(99)', 'avg', 'min', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: 10,
      duration: '10s',
      gracefulStop: '3s',
      tags: { phase: 'warmup' },
    },
    load: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      startTime: '13s',
      gracefulStop: '3s',
      tags: { phase: 'load' },
    },
    peak: {
      executor: 'constant-vus',
      vus: 100,
      duration: '10s',
      startTime: '46s',
      gracefulStop: '3s',
      tags: { phase: 'peak' },
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.05'],
  },
};

export default function () {
  // 멱등성 체크 우회: 매 요청마다 고유한 ID 사용
  // 같은 paymentKey 재사용 시 existsByPaymentKeyAndEventType()에서 early return → 실제 DB 부하 없음
  const uid = `${__VU}_${__ITER}`;
  const payload = JSON.stringify({
    tossOrderId: `ORD_LOAD_${uid}`,
    paymentKey:  `pk_load_${uid}`,
    amount:      10000,
  });

  const res = http.post(
    `${BASE_URL}/api/payments/toss/confirm`,
    payload,
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' },
  );

  check(res, { 'status 200': (r) => r.status === 200 });

  sleep(0.05);
}
