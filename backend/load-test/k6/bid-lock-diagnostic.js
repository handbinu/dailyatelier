import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const created = new Counter('bid_201');
const tooLow = new Counter('bid_too_low');
const conflict = new Counter('bid_conflict');
const other4xx = new Counter('bid_other_4xx');
const server5xx = new Counter('bid_5xx');
const clientTimeout = new Counter('bid_client_timeout');
const unexpected = new Counter('bid_unexpected');
const bidDuration = new Trend('bid_duration', true);

const baseUrl = required('BASE_URL').replace(/\/$/, '');
const userId = required('BIDDER_ID');
const fixturePassword = required('LOAD_TEST_FIXTURE_PASSWORD');
const artId = required('ART_ID');
const bidPrice = Number(required('BID_PRICE'));
const requestTimeout = __ENV.REQUEST_TIMEOUT || '65s';

export const options = {
  scenarios: {
    diagnostic: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '75s',
    },
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ userId, password: fixturePassword }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' },
  );
  if (response.status !== 200 || !response.json('token')) {
    throw new Error(`Login failed for ${userId}: HTTP ${response.status}`);
  }
  return { token: response.json('token') };
}

export default function (data) {
  const response = http.post(
    `${baseUrl}/api/arts/${artId}/bids`,
    JSON.stringify({ bidPrice }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        'Content-Type': 'application/json',
      },
      timeout: requestTimeout,
    },
  );
  bidDuration.add(response.timings.duration);
  if (response.status === 201) return created.add(1);
  if (response.status === 0) return clientTimeout.add(1);
  let code = '';
  try { code = response.json('code') || ''; } catch (_) { /* classified by status */ }
  if (response.status === 409 && code === 'BID_TOO_LOW') tooLow.add(1);
  else if (response.status === 409 && code === 'BID_CONFLICT') conflict.add(1);
  else if (response.status >= 400 && response.status < 500) other4xx.add(1);
  else if (response.status >= 500) server5xx.add(1);
  else unexpected.add(1);
}

export function handleSummary(data) {
  delete data.setup_data;
  const names = [
    'iterations', 'bid_duration', 'bid_201', 'bid_too_low', 'bid_conflict',
    'bid_other_4xx', 'bid_5xx', 'bid_client_timeout', 'bid_unexpected',
  ];
  const output = names
    .filter((name) => data.metrics[name])
    .map((name) => `${name}: ${JSON.stringify(data.metrics[name].values)}`)
    .join('\n');
  return {
    stdout: `${output}\n`,
    [required('SUMMARY_PATH')]: JSON.stringify(data, null, 2),
  };
}

function required(name) {
  const value = __ENV[name];
  if (!value) throw new Error(`${name} is required.`);
  return value;
}
