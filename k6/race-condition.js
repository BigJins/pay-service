/**
 * pay-service — 중복 결제 Race Condition 재현 테스트
 *
 * 목적: Redis 분산 락 도입 전, 동일 paymentKey로 동시 요청 시
 *       DB unique constraint에서 충돌이 발생하는지 확인
 *
 * 엔드포인트: POST /api/payments/toss/confirm
 * 요청 DTO : ConfirmCommand { tossOrderId: string, paymentKey: string, amount: number }
 *
 * 사전 조건:
 *   - pay-service 기동: ./gradlew bootRun --args='--spring.profiles.active=local'
 *   - TossPaymentsStub 활성화 상태 (local 프로파일)
 *   - 이전 테스트 데이터 초기화:
 *     DELETE FROM payment_ledger WHERE toss_order_id = 'RACE_TEST_ORDER_1';
 *
 * 실행:
 *   k6 run k6/race-condition.js
 *
 * 결과 확인 쿼리:
 *   SELECT COUNT(*), event_type
 *   FROM payment_ledger
 *   WHERE toss_order_id = 'RACE_TEST_ORDER_1'
 *   GROUP BY event_type;
 *
 * 예상 결과 (Before - 락 없음):
 *   - k6: 200 = 1건, 500 = N건 (DB constraint 충돌)
 *   - DB: COUNT = 1 (unique constraint가 중복 저장은 막음)
 *   → 문제: 나머지 N건이 500 에러 (사용자에게 결제 실패 오류 노출)
 *
 * 예상 결과 (After - Redis 락 적용):
 *   - k6: 200 = 1건, 4xx = N건 (락 획득 실패로 즉시 거절)
 *   - DB: COUNT = 1
 *   → 개선: 에러 없이 깔끔하게 중복 차단
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://127.0.0.1:8080';

// 모든 VU가 동일한 값 사용 — 의도적 race condition 유발
const RACE_ORDER_ID  = 'RACE_TEST_ORDER_1';
const RACE_PAYMENT_KEY = 'race_test_pk_001';

export const options = {
  scenarios: {
    race: {
      executor: 'shared-iterations',
      vus: 20,
      iterations: 20,
      maxDuration: '15s',
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    tossOrderId: RACE_ORDER_ID,
    paymentKey:  RACE_PAYMENT_KEY,
    amount:      10000,
  });

  const res = http.post(
    `${BASE_URL}/api/payments/toss/confirm`,
    payload,
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' },
  );

  check(res, {
    '200 또는 409': (r) => r.status === 200 || r.status === 409,
    '500 아님'    : (r) => r.status !== 500,
  });
}
