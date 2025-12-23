/**
 * Load Test - Normal Traffic Simulation
 * 
 * Purpose: Verify API performance under expected load
 * Duration: 5 minutes
 * VUs: Ramp up to 50
 * 
 * Run: k6 run load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const singleConversionDuration = new Trend('single_conversion_duration');
const rangeConversionDuration = new Trend('range_conversion_duration');
const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // Ramp up to 20 VUs
    { duration: '3m', target: 50 },   // Stay at 50 VUs
    { duration: '1m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'],  // 95% under 100ms
    http_req_failed: ['rate<0.01'],     // Less than 1% errors
    'single_conversion_duration': ['p(95)<50'],
    'range_conversion_duration': ['p(95)<200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 70% single conversions, 30% range conversions
  if (Math.random() < 0.7) {
    testSingleConversion();
  } else {
    testRangeConversion();
  }
  
  sleep(0.1);
}

function testSingleConversion() {
  const randomNum = Math.floor(Math.random() * 3999) + 1;
  const startTime = Date.now();
  
  const response = http.get(`${BASE_URL}/romannumeral?query=${randomNum}`);
  
  singleConversionDuration.add(Date.now() - startTime);
  
  const success = check(response, {
    'single: status is 200': (r) => r.status === 200,
    'single: valid JSON': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.input && body.output;
      } catch (e) {
        return false;
      }
    },
  });
  
  errorRate.add(!success);
}

function testRangeConversion() {
  const min = Math.floor(Math.random() * 100) + 1;
  const max = min + Math.floor(Math.random() * 50) + 1;
  const startTime = Date.now();
  
  const response = http.get(`${BASE_URL}/romannumeral?min=${min}&max=${max}`);
  
  rangeConversionDuration.add(Date.now() - startTime);
  
  const success = check(response, {
    'range: status is 200': (r) => r.status === 200,
    'range: valid conversions array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.conversions) && body.conversions.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
  
  errorRate.add(!success);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

