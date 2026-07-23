// Shared Upstash Redis REST helper for the bug-report endpoints. Always sends the command as a
// JSON array body (not path segments) -- path-style calls silently break the moment any argument
// is an empty string (see discord-presence.js's history for why), and bug reports have several
// free-text fields (description, logs, notes) that legitimately can be empty or contain almost
// any character.
async function redisCommand(args) {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) {
    return { result: null, configured: false };
  }
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(args),
  });
  const data = await res.json();
  return { result: data.result, configured: true };
}

/** Upstash returns Redis hashes/WITHSCORES results as a flat [k1, v1, k2, v2, ...] array. */
function flatArrayToObject(flatArray) {
  const obj = {};
  const arr = Array.isArray(flatArray) ? flatArray : [];
  for (let i = 0; i < arr.length; i += 2) {
    obj[arr[i]] = arr[i + 1];
  }
  return obj;
}

module.exports = { redisCommand, flatArrayToObject };
