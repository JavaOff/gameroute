// Shared by the /userstats Discord command (discord-interactions.js) and the desktop app's
// in-app Admin panel (stats.js) -- same two honest, low-privacy-cost numbers either way: an
// opt-in install heartbeat (Upstash) and the GitHub release's public download_count. No
// per-user data anywhere in here, just aggregate counts.

// Twice the app's heartbeat interval (see Constants.TELEMETRY_HEARTBEAT_INTERVAL_MINUTES),
// as a buffer against scheduling drift/network delay -- an install missing two
// heartbeats in a row is a reasonable line for "no longer running".
const ONLINE_WINDOW_SECONDS = 10 * 60;

/** Installs that heartbeated within the last ONLINE_WINDOW_SECONDS, or null if Upstash isn't configured. */
async function getOnlineNowCount() {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) {
    return null;
  }
  const headers = { Authorization: `Bearer ${token}` };
  const cutoff = Math.floor(Date.now() / 1000) - ONLINE_WINDOW_SECONDS;
  try {
    // Drop stale entries first so a closed app doesn't linger in the count forever.
    await fetch(`${url}/zremrangebyscore/active_installs/-inf/${cutoff}`, { method: 'POST', headers });
    const res = await fetch(`${url}/zcard/active_installs`, { method: 'POST', headers });
    const data = await res.json();
    return typeof data.result === 'number' ? data.result : null;
  } catch {
    return null;
  }
}

/** All-time download count of the latest release's installer asset, or null on any failure. */
async function getDownloadCount() {
  try {
    const res = await fetch('https://api.github.com/repos/JavaOff/gameroute/releases/latest', {
      headers: { 'User-Agent': 'gameroute-discord-bot', Accept: 'application/vnd.github+json' },
    });
    const data = await res.json();
    const asset = (data.assets || []).find((a) => a.name && a.name.endsWith('.exe'));
    return asset ? asset.download_count : null;
  } catch {
    return null;
  }
}

module.exports = { getOnlineNowCount, getDownloadCount };
