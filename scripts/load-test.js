// Prueba de carga (k6) para la plataforma de nominaciones.
//
// Objetivo: validar el requisito de escalabilidad (10.000 -> 1.000.000 solicitudes/dia)
// y verificar que el borde acepta de forma asincronica sin degradarse bajo picos.
//
// Calculo de referencia:
//   10.000 /dia  ~=  0.12 req/s   (carga base)
//   1.000.000/dia ≈ 11.6 req/s    (objetivo de volumen)
// El escenario hace rampa hasta un pico (mas alla del promedio) para validar picos.
//
// Uso:
//   # Desactivar el rate limit en la API para medir capacidad real:
//   $env:RATE_LIMIT_ENABLED="false"        # PowerShell
//   k6 run scripts/load-test.js
//
//   # Contra otro entorno:
//   k6 run -e BASE_URL=http://localhost:8081 -e API_TOKEN=dev-token scripts/load-test.js
//
// Interpretacion: el endpoint de ingreso debe responder 202 rapido (no espera a ABM).
// Si aparece 429, es el rate limiting (bajalo con RATE_LIMIT_RPM o desactivalo para medir).

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const API_TOKEN = __ENV.API_TOKEN || 'dev-token';

export const options = {
  scenarios: {
    nomination_flow: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 300,
      stages: [
        { target: 10, duration: '10s' }, // ~10k/dia
        { target: 50, duration: '20s' }, // pico
        { target: 100, duration: '10s' }, // stress
        { target: 0, duration: '5s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],      // < 1% de errores
    http_req_duration: ['p(95)<800'],    // p95 por debajo de 800 ms
    checks: ['rate>0.99'],
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function () {
  const body = JSON.stringify({
    customer_id: '123456',
    account_id: `ACC_${__VU}_${__ITER}`,
    card_id: 'TOKEN-REFERENCE',
    alias: 'CUENTA_PRINCIPAL',
    request_id: uuid(),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${API_TOKEN}`,
      'Idempotency-Key': uuid(),
      'X-Client-Id': `k6-vu-${__VU}`,
    },
  };

  const res = http.post(`${BASE_URL}/v1/nominations`, body, params);

  check(res, {
    'aceptada 202': (r) => r.status === 202,
    'devuelve nomination_id': (r) => {
      try {
        return !!r.json('nomination_id');
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.1);
}
