const expectedSize = 72_734_300;
const primaryUrl = 'https://apk-download-production-54b3.up.railway.app/latest.apk';
const backupUrl = 'https://github.com/hhk16/8kpro-downloads/releases/download/v5.1.6-round-robin-1/8Kpro-round-robin-direct-login.apk';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function rangeCheck(label, url) {
  const response = await fetch(url, {
    headers: { Range: 'bytes=0-0', 'User-Agent': '8KPro-Download-Monitor/1.0' },
    redirect: 'follow',
  });
  const contentRange = response.headers.get('content-range');
  const body = await response.arrayBuffer();
  assert(response.status === 206, `${label}: expected HTTP 206, received ${response.status}`);
  assert(contentRange === `bytes 0-0/${expectedSize}`, `${label}: unexpected content range ${contentRange}`);
  assert(body.byteLength === 1, `${label}: expected one byte, received ${body.byteLength}`);
  console.log(`${label}: direct range check passed`);
}

async function resolveRedirect(url) {
  const response = await fetch(url, {
    method: 'HEAD',
    redirect: 'manual',
    headers: { 'User-Agent': '8KPro-Download-Monitor/1.0' },
  });
  assert([301, 302, 303, 307, 308].includes(response.status), `backup: expected redirect, received ${response.status}`);
  const location = response.headers.get('location');
  assert(location, 'backup: redirect did not include a destination');
  return new URL(location, url).toString();
}

async function globalpingRangeCheck(label, url) {
  const target = new URL(url);
  const requestOptions = {
    method: 'GET',
    path: target.pathname,
    headers: { Range: 'bytes=0-0', 'User-Agent': '8KPro-Download-Monitor/1.0' },
  };
  if (target.search.length > 1) requestOptions.query = target.search.slice(1);
  const createResponse = await fetch('https://api.globalping.io/v1/measurements', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'User-Agent': '8KPro-Download-Monitor/1.0' },
    body: JSON.stringify({
      type: 'http',
      target: target.hostname,
      locations: [
        { country: 'AE', limit: 20 },
        { country: 'SA', limit: 20 },
        { country: 'LB', limit: 5 },
      ],
      measurementOptions: {
        protocol: 'HTTPS',
        request: requestOptions,
      },
    }),
  });
  const created = await createResponse.json();
  assert(
    createResponse.ok && created.id,
    `${label}: Globalping rejected the measurement (${createResponse.status}: ${JSON.stringify(created)})`,
  );

  let measurement;
  for (let attempt = 0; attempt < 15; attempt += 1) {
    await new Promise((resolve) => setTimeout(resolve, 2_000));
    const response = await fetch(`https://api.globalping.io/v1/measurements/${created.id}`, {
      headers: { 'User-Agent': '8KPro-Download-Monitor/1.0' },
    });
    measurement = await response.json();
    if (measurement.status !== 'in-progress') break;
  }

  assert(measurement?.status === 'finished', `${label}: Globalping measurement did not finish`);
  const returnedCountries = new Set(measurement.results?.map((entry) => entry.probe.country));
  assert(returnedCountries.has('AE'), `${label}: no UAE probe result was returned`);
  assert(returnedCountries.has('SA'), `${label}: no Saudi Arabia probe result was returned`);
  assert(returnedCountries.has('LB'), `${label}: no Lebanon probe result was returned`);
  for (const entry of measurement.results) {
    const location = `${entry.probe.city}, ${entry.probe.country} / ${entry.probe.network}`;
    assert(entry.result.status === 'finished', `${label}: ${location} failed: ${entry.result.rawOutput || 'unknown error'}`);
    assert(entry.result.statusCode === 206, `${label}: ${location} returned HTTP ${entry.result.statusCode}`);
    assert(entry.result.headers?.['content-range'] === `bytes 0-0/${expectedSize}`, `${label}: ${location} returned an invalid content range`);
  }
  console.log(`${label}: UAE/KSA/Lebanon checks passed across ${measurement.results.length} probes — https://globalping.io?measurement=${created.id}`);
}

await rangeCheck('primary', primaryUrl);
await rangeCheck('backup', backupUrl);
await globalpingRangeCheck('primary', primaryUrl);
await globalpingRangeCheck('backup', await resolveRedirect(backupUrl));
