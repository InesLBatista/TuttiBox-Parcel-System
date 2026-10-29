import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const stations = [
  'ST-AVEIRO-CENTRO',
  'ST-AVEIRO-FORUM',
  'ST-AVEIRO-ESTACAO',
];
const pages = [
  '/',
  '/courier.html',
  '/recipient.html',
  '/tracking.html',
  '/operator.html',
];

const completedParcelFlows = new Counter('completed_parcel_flows');
const rejectedRegistrations = new Counter('rejected_registrations');

export const options = {
  scenarios: {
    station_search: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 2,
      maxVUs: 10,
      stages: [
        { target: 2, duration: '10s' },
        { target: 5, duration: '20s' },
        { target: 5, duration: '20s' },
        { target: 0, duration: '5s' },
      ],
      exec: 'searchStations',
      tags: { workload: 'station_search' },
    },
    parcel_lifecycle: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { target: 2, duration: '10s' },
        { target: 6, duration: '20s' },
        { target: 6, duration: '20s' },
        { target: 0, duration: '5s' },
      ],
      gracefulRampDown: '10s',
      exec: 'exerciseParcelApi',
      tags: { workload: 'parcel_lifecycle' },
    },
    web_pages: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 1,
      maxVUs: 3,
      stages: [
        { target: 1, duration: '10s' },
        { target: 2, duration: '20s' },
        { target: 2, duration: '20s' },
        { target: 0, duration: '5s' },
      ],
      exec: 'renderWebPages',
      tags: { workload: 'web_pages' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{workload:station_search}': ['p(95)<1500'],
    'http_req_duration{workload:parcel_lifecycle}': ['p(95)<1500'],
    'http_req_duration{workload:web_pages}': ['p(95)<1500'],
    checks: ['rate>0.98'],
    dropped_iterations: ['count==0'],
  },
};

export function searchStations() {
  const response = http.get(`${baseUrl}/api/stations/search?address=Aveiro`, {
    tags: { endpoint: 'station_search' },
  });

  check(response, {
    'station search returns HTTP 200': (res) => res.status === 200,
    'station search returns all three seeded stations': (res) => {
      if (res.status !== 200) return false;
      try {
        const results = res.json();
        return Array.isArray(results) && results.length === 3;
      } catch (_) {
        return false;
      }
    },
  });
  sleep(0.2);
}

export function renderWebPages() {
  const path = pages[__ITER % pages.length];
  const response = http.get(`${baseUrl}${path}`, {
    tags: { endpoint: 'web_page', workload: 'web_pages', page: path },
  });

  check(response, {
    [`${path} returns HTTP 200`]: (res) => res.status === 200,
    [`${path} returns HTML`]: (res) =>
      res.headers['Content-Type']?.includes('text/html') || false,
  });
  sleep(0.2);
}

export function exerciseParcelApi() {
  // Each VU repeatedly uses one station. With six VUs and two S compartments
  // per station, no more than two test reservations target a station at once.
  const stationId = stations[(__VU - 1) % stations.length];
  const request = {
    stationId,
    compartmentSize: 'S',
    recipientContact: `k6-vu-${__VU}@example.test`,
    partnerId: 'K6-PERFORMANCE-TEST',
  };
  const headers = { headers: { 'Content-Type': 'application/json' } };
  const registered = http.post(`${baseUrl}/api/parcels`, JSON.stringify(request), {
    ...headers,
    tags: { endpoint: 'parcel_register', workload: 'parcel_lifecycle' },
  });

  const registrationOk = check(registered, {
    'parcel registration returns HTTP 201': (res) => res.status === 201,
    'parcel registration returns tracking code': (res) => {
      if (res.status !== 201) return false;
      try {
        return Boolean(res.json('trackingCode'));
      } catch (_) {
        return false;
      }
    },
  });

  if (!registrationOk) {
    rejectedRegistrations.add(1);
    sleep(0.2);
    return;
  }

  const trackingCode = registered.json('trackingCode');
  const trackingUrl = `${baseUrl}/api/parcels/${encodeURIComponent(trackingCode)}`;
  const status = http.get(`${trackingUrl}/status`, {
    tags: { endpoint: 'parcel_status', workload: 'parcel_lifecycle' },
  });
  const history = http.get(`${trackingUrl}/history`, {
    tags: { endpoint: 'parcel_history', workload: 'parcel_lifecycle' },
  });
  const details = http.get(trackingUrl, {
    tags: { endpoint: 'parcel_details', workload: 'parcel_lifecycle' },
  });
  const operatorParcels = http.get(
    `${baseUrl}/api/operator/parcels?stationId=${encodeURIComponent(stationId)}&state=CREATED`,
    { tags: { endpoint: 'operator_parcels', workload: 'parcel_lifecycle' } },
  );
  const compartments = http.get(
    `${baseUrl}/api/operator/stations/${encodeURIComponent(stationId)}/compartments`,
    { tags: { endpoint: 'operator_compartments', workload: 'parcel_lifecycle' } },
  );
  check(status, {
    'new parcel status is CREATED': (res) => res.status === 200 && res.json() === 'CREATED',
  });
  check(history, {
    'new parcel history contains its initial event': (res) =>
      res.status === 200 && Array.isArray(res.json()) && res.json().length >= 1,
  });
  check(details, {
    'parcel details are available by tracking code': (res) =>
      res.status === 200 && res.json('trackingCode') === trackingCode,
  });
  check(operatorParcels, {
    'operator parcel filter returns HTTP 200': (res) => res.status === 200,
  });
  check(compartments, {
    'operator compartment inventory returns HTTP 200': (res) => res.status === 200,
  });

  const cancelled = http.post(
    `${trackingUrl}/cancel?actor=K6-PERFORMANCE-TEST`,
    null,
    { tags: { endpoint: 'parcel_cancel', workload: 'parcel_lifecycle' } },
  );
  const cancellationOk = check(cancelled, {
    'parcel cancellation returns HTTP 200': (res) => res.status === 200,
    'cancelled parcel releases its reservation': (res) =>
      res.status === 200 && res.json('currentState') === 'CANCELLED',
  });

  if (cancellationOk) completedParcelFlows.add(1);
  sleep(0.2);
}
