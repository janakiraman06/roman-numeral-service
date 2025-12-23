/**
 * Stress Test - Find Breaking Point
 * 
 * Purpose: Determine system limits and behavior under extreme load
 * Duration: 5 minutes
 * VUs: Ramp up to 200
 * 
 * Run: k6 run stress-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Ramp up
    { duration: '1m', target: 100 },   // Increase load
    { duration: '1m', target: 150 },   // Push harder
    { duration: '1m', target: 200 },   // Near breaking point
    { duration: '1m', target: 200 },   // Stay at peak
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // More relaxed under stress
    http_req_failed: ['rate<0.10'],     // Allow up to 10% errors
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const randomNum = Math.floor(Math.random() * 3999) + 1;
  
  const response = http.get(`${BASE_URL}/romannumeral?query=${randomNum}`);
  
  check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });
  
  sleep(0.05);
}

export function handleSummary(data) {
  console.log('\n========== STRESS TEST SUMMARY ==========\n');
  console.log(`Total Requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Failed Requests: ${data.metrics.http_req_failed.values.passes}`);
  console.log(`Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
  console.log(`p95 Response Time: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
  console.log(`Max Response Time: ${data.metrics.http_req_duration.values.max.toFixed(2)}ms`);
  
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

