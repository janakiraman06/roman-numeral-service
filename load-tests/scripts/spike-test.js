/**
 * Spike Test - Sudden Traffic Surge
 * 
 * Purpose: Test system behavior during sudden traffic spikes
 * Duration: 2 minutes
 * Pattern: Sudden spike to 100 VUs
 * 
 * Run: k6 run spike-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 5 },    // Baseline
    { duration: '5s', target: 100 },   // Sudden spike!
    { duration: '30s', target: 100 },  // Stay at peak
    { duration: '5s', target: 5 },     // Quick recovery
    { duration: '30s', target: 5 },    // Stabilize
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const randomNum = Math.floor(Math.random() * 3999) + 1;
  
  const response = http.get(`${BASE_URL}/romannumeral?query=${randomNum}`);
  
  check(response, {
    'status is 200': (r) => r.status === 200,
    'valid response': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.input && body.output;
      } catch (e) {
        return false;
      }
    },
  });
  
  sleep(0.1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

