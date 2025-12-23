/**
 * Smoke Test - Basic API Verification
 * 
 * Purpose: Verify the API is working correctly with minimal load
 * Duration: 30 seconds
 * VUs: 1
 * 
 * Run: k6 run smoke-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],     // Less than 1% errors
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Test single conversion
  const singleResponse = http.get(`${BASE_URL}/romannumeral?query=42`);
  check(singleResponse, {
    'single: status is 200': (r) => r.status === 200,
    'single: has input field': (r) => JSON.parse(r.body).input === '42',
    'single: has output field': (r) => JSON.parse(r.body).output === 'XLII',
  });

  sleep(0.5);

  // Test range conversion
  const rangeResponse = http.get(`${BASE_URL}/romannumeral?min=1&max=5`);
  check(rangeResponse, {
    'range: status is 200': (r) => r.status === 200,
    'range: has conversions array': (r) => JSON.parse(r.body).conversions.length === 5,
  });

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

