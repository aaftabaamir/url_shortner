import http from 'k6/http';
import { check, sleep } from 'k6';

// Run: k6 run loadtest/redirect-load-test.js
// Update SHORT_CODE with a real code created via POST /api/v1/links first.

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // ramp up
    { duration: '1m', target: 200 },   // sustained load
    { duration: '30s', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<250'], // p99 under 250ms
    http_req_failed: ['rate<0.01'],   // <1% errors
  },
};

const BASE_URL = 'http://localhost:8080';
const SHORT_CODE = 'REPLACE_WITH_REAL_CODE';

export default function () {
  const res = http.get(`${BASE_URL}/${SHORT_CODE}`, { redirects: 0 });
  check(res, {
    'status is 302': (r) => r.status === 302,
  });
  sleep(0.1);
}
