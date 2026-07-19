// Handles Discord's HTTP Interactions (slash commands) for the /userstats
// command. No bot process to keep alive -- Discord POSTs each interaction
// here directly, this verifies the request really came from Discord, and
// replies with a rough usage count built from two honest, low-privacy-cost
// sources: an opt-in install heartbeat (Upstash) and the GitHub release's
// public download_count (no telemetry involved at all).
const crypto = require('crypto');

const ED25519_SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

function verifySignature(rawBody, signature, timestamp, publicKeyHex) {
  try {
    const publicKeyDer = Buffer.concat([ED25519_SPKI_PREFIX, Buffer.from(publicKeyHex, 'hex')]);
    const publicKey = crypto.createPublicKey({ key: publicKeyDer, format: 'der', type: 'spki' });
    const message = Buffer.concat([Buffer.from(timestamp, 'utf8'), rawBody]);
    return crypto.verify(null, message, publicKey, Buffer.from(signature, 'hex'));
  } catch {
    return false;
  }
}

async function readRawBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

/** Active installs in the last 30 days, or null if Upstash isn't configured. */
async function getActiveInstallCount() {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) {
    return null;
  }
  const headers = { Authorization: `Bearer ${token}` };
  const cutoff = Math.floor(Date.now() / 1000) - 30 * 24 * 60 * 60;
  try {
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

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).end();
    return;
  }

  const signature = req.headers['x-signature-ed25519'];
  const timestamp = req.headers['x-signature-timestamp'];
  const publicKey = process.env.DISCORD_PUBLIC_KEY;
  const rawBody = await readRawBody(req);

  if (!signature || !timestamp || !publicKey || !verifySignature(rawBody, signature, timestamp, publicKey)) {
    res.status(401).send('invalid request signature');
    return;
  }

  const interaction = JSON.parse(rawBody.toString('utf8'));

  // Discord's periodic liveness check for the interactions endpoint.
  if (interaction.type === 1) {
    res.status(200).json({ type: 1 });
    return;
  }

  if (interaction.type === 2 && interaction.data && interaction.data.name === 'userstats') {
    const [activeInstalls, downloads] = await Promise.all([getActiveInstallCount(), getDownloadCount()]);
    const lines = [
      '**GameRoute usage stats**',
      activeInstalls === null
        ? '- Active installs (last 30 days): unavailable'
        : `- Active installs (last 30 days): **${activeInstalls}**`,
      downloads === null
        ? '- Installer downloads (all-time): unavailable'
        : `- Installer downloads (all-time): **${downloads}**`,
    ];
    res.status(200).json({
      type: 4,
      data: { content: lines.join('\n'), flags: 64 }, // ephemeral -- only the person running the command sees it
    });
    return;
  }

  res.status(400).json({ error: 'unknown interaction' });
};

// Raw request bytes are required for signature verification, so Vercel's
// automatic JSON body parsing has to be disabled for this function. This must
// be attached AFTER module.exports is assigned above -- setting it earlier
// gets discarded the moment module.exports is reassigned to the handler.
module.exports.config = { api: { bodyParser: false } };
