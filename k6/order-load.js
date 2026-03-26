/**
 * order-service — 주문 생성 부하 테스트
 *
 * 목적: order-service 처리량 / 응답시간 측정
 *
 * 엔드포인트:
 *   POST /api/orders       — 주문 생성
 *   GET  /api/orders/{id}  — 주문 조회
 *
 * 요청 구조 (코드 기준):
 *   OrderCreateRequest {
 *     buyerId: Long,
 *     orderLines: [{
 *       productId: Long,
 *       productNameSnapshot: String (2~200자),
 *       unitPrice: { amount: Long },   ← Money record
 *       quantity: int (≥1)
 *     }],
 *     shippingInfo: {
 *       receiverName: String (2~50자),
 *       receiverPhone: String (010 + 8자리, 예: "01012345678"),
 *       address: {
 *         zipCode: String (숫자 5자리, 예: "06236"),
 *         roadAddress: String (≤200자),
 *         detailAddress: String (≤200자)
 *       },
 *       deliveryMemo: String | null
 *     }
 *   }
 *
 * 사전 조건:
 *   order-service 기동: ./gradlew bootRun --args='--spring.profiles.active=local'
 *   (order-service 디렉토리에서)
 *
 * 실행:
 *   k6 run k6/order-load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const ORDER_BASE_URL = 'http://127.0.0.1:8081';

export const options = {
  summaryTrendStats: ['med', 'p(90)', 'p(95)', 'p(99)', 'avg'],
  scenarios: {
    order_create: {
      executor: 'constant-vus',
      vus: 30,
      duration: '30s',
      tags: { scenario: 'create' },
    },
  },
  thresholds: {
    'http_req_failed':                       ['rate<0.01'],
    'http_req_duration{scenario:create}':    ['p(95)<1000'],
  },
};

function buildOrderPayload(vu, iter) {
  return JSON.stringify({
    buyerId: vu,
    orderLines: [
      {
        productId: 101,
        productNameSnapshot: '테스트 상품 A',
        unitPrice: { amount: 15000 },
        quantity: 2,
      },
      {
        productId: 202,
        productNameSnapshot: '테스트 상품 B',
        unitPrice: { amount: 5000 },
        quantity: 1,
      },
    ],
    shippingInfo: {
      receiverName: '홍길동',
      receiverPhone: '01012345678',
      address: {
        zipCode: '06236',
        roadAddress: '서울특별시 강남구 테헤란로 152',
        detailAddress: `${iter}동 101호`,
      },
      deliveryMemo: null,
    },
  });
}

export default function () {
  const headers = { 'Content-Type': 'application/json' };

  // 1. 주문 생성
  const createRes = http.post(
    `${ORDER_BASE_URL}/api/orders`,
    buildOrderPayload(__VU, __ITER),
    { headers, timeout: '10s' },
  );

  const created = check(createRes, {
    '주문 생성 201 또는 200': (r) => r.status === 200 || r.status === 201,
    'tossOrderId 존재':      (r) => {
      try { return !!JSON.parse(r.body).tossOrderId; } catch { return false; }
    },
  });

  // 2. 생성된 주문 조회 (응답에서 ID를 파싱할 수 없으면 스킵)
  if (created && createRes.status === 200) {
    // OrderCreatorResponse: { tossOrderId, amount } — orderId는 응답에 없음
    // GET /api/orders/{orderId} 는 orderId(PK)가 필요하므로 여기서는 생략
    // orderId를 응답에 포함하려면 OrderCreatorResponse에 id 필드 추가 필요
  }

  sleep(0.1);
}
