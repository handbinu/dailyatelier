import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const created = new Counter('bid_201');
const tooLow = new Counter('bid_too_low');
const conflict = new Counter('bid_conflict');
const other4xx = new Counter('bid_other_4xx');
const server5xx = new Counter('bid_5xx');
const clientTimeout = new Counter('bid_client_timeout');
const unexpected = new Counter('bid_unexpected');
const bidDuration = new Trend('bid_duration', true);

const scenarioName = __ENV.BID_SCENARIO;
const baseUrl = required('BASE_URL').replace(/\/$/, '');
const fixturePassword = required('LOAD_TEST_FIXTURE_PASSWORD');
const bidderCount = numberEnv('BIDDER_COUNT', 32);
const artIds = required('ART_IDS').split(',').map((value) => Number(value));
const requestTimeout = __ENV.REQUEST_TIMEOUT || '10s';

if (!['hot', 'distributed', 'shared-account'].includes(scenarioName)) {
  throw new Error(`Unsupported BID_SCENARIO: ${scenarioName}`);
}
if (scenarioName === 'distributed' && artIds.length < 32) {
  throw new Error('The distributed scenario requires at least 32 art IDs.');
}

export const options = {
  scenarios: {
    smoke: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10s',
      startTime: '0s',
      tags: { load_phase: 'smoke' },
    },
    low: {
      executor: 'constant-vus',
      vus: 5,
      duration: '10s',
      startTime: '3s',
      gracefulStop: '2s',
      tags: { load_phase: 'low' },
    },
    ramp: {
      executor: 'ramping-vus',
      startVUs: 5,
      startTime: '16s',
      stages: [
        { duration: '8s', target: 10 },
        { duration: '8s', target: 20 },
        { duration: '8s', target: 32 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '2s',
      tags: { load_phase: 'ramp' },
    },
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    bid_5xx: ['count==0'],
    bid_client_timeout: ['count==0'],
    bid_unexpected: ['count==0'],
  },
};

export function setup() {
  const userIds = scenarioName === 'shared-account'
    ? ['load_bid_001']
    : Array.from({ length: bidderCount }, (_, index) => bidderId(index + 1));
  const tokens = {};
  for (const userId of userIds) {
    const response = http.post(
      `${baseUrl}/api/auth/login`,
      JSON.stringify({ userId, password: fixturePassword }),
      { headers: { 'Content-Type': 'application/json' }, timeout: requestTimeout },
    );
    if (response.status !== 200) {
      throw new Error(`Login failed for ${userId}: HTTP ${response.status}`);
    }
    const token = response.json('token');
    if (!token) {
      throw new Error(`Login returned no token for ${userId}.`);
    }
    tokens[userId] = token;
  }
  return { tokens };
}

export default function (data) {
  const slot = (exec.vu.idInInstance - 1) % bidderCount;
  const userId = scenarioName === 'shared-account'
    ? 'load_bid_001'
    : bidderId(slot + 1);
  const artId = scenarioName === 'hot'
    ? artIds[0]
    : artIds[slot % artIds.length];
  const sequence = exec.scenario.iterationInTest + phaseOffset();
  const bidPrice = 101000 + sequence * 1000;
  const response = http.post(
    `${baseUrl}/api/arts/${artId}/bids`,
    JSON.stringify({ bidPrice }),
    {
      headers: {
        Authorization: `Bearer ${data.tokens[userId]}`,
        'Content-Type': 'application/json',
      },
      timeout: requestTimeout,
      tags: { bid_scenario: scenarioName },
    },
  );
  record(response);
}

export function handleSummary(data) {
  delete data.setup_data;
  return {
    stdout: `${textSummary(data)}\n`,
    [required('SUMMARY_PATH')]: JSON.stringify(data, null, 2),
  };
}

function record(response) {
  bidDuration.add(response.timings.duration);
  if (response.status === 201) {
    created.add(1);
    return;
  }
  if (response.status === 0) {
    clientTimeout.add(1);
    return;
  }
  let code = '';
  try {
    code = response.json('code') || '';
  } catch (_) {
    // A non-JSON error is classified by its HTTP status below.
  }
  if (response.status === 409 && code === 'BID_TOO_LOW') {
    tooLow.add(1);
  } else if (response.status === 409 && code === 'BID_CONFLICT') {
    conflict.add(1);
  } else if (response.status >= 400 && response.status < 500) {
    other4xx.add(1);
  } else if (response.status >= 500) {
    server5xx.add(1);
  } else {
    unexpected.add(1);
  }
}

function phaseOffset() {
  if (exec.scenario.name === 'smoke') return 0;
  if (exec.scenario.name === 'low') return 100000;
  return 200000;
}

function bidderId(index) {
  return `load_bid_${String(index).padStart(3, '0')}`;
}

function required(name) {
  const value = __ENV[name];
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

function numberEnv(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer.`);
  return value;
}

function textSummary(data) {
  const names = [
    'iterations', 'http_reqs', 'bid_duration', 'bid_201', 'bid_too_low',
    'bid_conflict', 'bid_other_4xx', 'bid_5xx', 'bid_client_timeout', 'bid_unexpected',
  ];
  return names
    .filter((name) => data.metrics[name])
    .map((name) => `${name}: ${JSON.stringify(data.metrics[name].values)}`)
    .join('\n');
}
