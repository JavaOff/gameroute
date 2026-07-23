// Backs the in-app Admin panel's "who's connected" list. Two very different trust levels:
//
// POST (write): any GameRoute install can report its OWN already-opted-in Discord identity
// (id/username/avatar) -- same self-reported, unauthenticated shape as the anonymous install
// heartbeat in telemetry.js. Low stakes: a user can only ever report themselves.
//
// GET (read): must NOT be publicly reachable -- this returns other people's Discord identities.
// The caller proves they're actually an Owner/Administrator/Moderator by passing their OWN
// Discord OAuth access token (Authorization: Bearer <token>), which this handler uses to ask
// Discord itself (not the caller) what roles that token's owner holds in the GameRoute server.
// A forged/absent token gets nothing back -- Discord tokens can't be faked client-side, so this
// holds even though the whole repo (including this exact role-ID list) is public on GitHub.
const GUILD_ID = '1305524197874995401';
const ADMIN_ROLE_IDS = new Set([
  '1398953662872682567', // Owner
  '1398953666047643769', // Administrator
  '1528383809144492202', // Moderator
]);

// Same "twice the heartbeat interval" drift buffer used for the anonymous online-now count.
const PRESENCE_WINDOW_SECONDS = 10 * 60;

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

async function handlePost(req, res) {
  let payload;
  try {
    payload = JSON.parse(await readBody(req));
  } catch {
    res.status(400).json({ error: 'invalid JSON body' });
    return;
  }
  const { discordId, username, globalName, avatarHash } = payload || {};
  if (typeof discordId !== 'string' || !/^\d{5,30}$/.test(discordId)) {
    res.status(400).json({ error: 'discordId must be a numeric Discord snowflake' });
    return;
  }

  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) {
    res.status(204).end(); // best-effort, same as telemetry.js -- don't error the client over our own config
    return;
  }
  const headers = { Authorization: `Bearer ${token}` };
  const now = Math.floor(Date.now() / 1000);
  try {
    await fetch(`${url}/zadd/discord_presence/${now}/${encodeURIComponent(discordId)}`, { method: 'POST', headers });
    // A path-style call (/hset/key/field/value/...) collapses into a broken double-slash the
    // moment any argument (e.g. a user with no globalName set) is an empty string, which Upstash's
    // edge silently 301-redirects instead of erroring -- fetch() doesn't follow that usefully, so
    // the whole write vanished with no visible failure. Sending the full command as a JSON array
    // body instead handles empty strings (and any other odd characters) correctly.
    await fetch(url, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify(['HSET', `discord_user:${discordId}`,
          'username', String(username || ''),
          'globalName', String(globalName || ''),
          'avatarHash', String(avatarHash || '')]),
    });
  } catch {
    // best-effort, same as telemetry.js
  }
  res.status(204).end();
}

/** The caller's own role IDs in the GameRoute server, verified by asking Discord itself -- never trusts the caller. */
async function callerRoleIds(accessToken) {
  const res = await fetch(`https://discord.com/api/users/@me/guilds/${GUILD_ID}/member`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) {
    return [];
  }
  const data = await res.json();
  return Array.isArray(data.roles) ? data.roles : [];
}

async function handleGet(req, res) {
  const authHeader = req.headers['authorization'] || '';
  const accessToken = authHeader.startsWith('Bearer ') ? authHeader.slice('Bearer '.length) : null;
  if (!accessToken) {
    res.status(401).json({ error: 'missing Authorization: Bearer <your Discord access token>' });
    return;
  }

  const roleIds = await callerRoleIds(accessToken);
  const isAdmin = roleIds.some((id) => ADMIN_ROLE_IDS.has(id));
  if (!isAdmin) {
    res.status(403).json({ error: 'not an Owner/Administrator/Moderator of the GameRoute server' });
    return;
  }

  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) {
    res.status(200).json({ users: [] });
    return;
  }
  const headers = { Authorization: `Bearer ${token}` };
  const cutoff = Math.floor(Date.now() / 1000) - PRESENCE_WINDOW_SECONDS;
  try {
    await fetch(`${url}/zremrangebyscore/discord_presence/-inf/${cutoff}`, { method: 'POST', headers });
    const zrangeRes = await fetch(`${url}/zrange/discord_presence/0/-1/withscores`, { method: 'POST', headers });
    const zrangeData = await zrangeRes.json();
    const flat = Array.isArray(zrangeData.result) ? zrangeData.result : [];
    // Upstash returns [member, score, member, score, ...] for WITHSCORES.
    const entries = [];
    for (let i = 0; i < flat.length; i += 2) {
      entries.push({ id: flat[i], lastSeen: Number(flat[i + 1]) });
    }

    const users = await Promise.all(entries.map(async (entry) => {
      const hgetRes = await fetch(`${url}/hgetall/discord_user:${encodeURIComponent(entry.id)}`, { method: 'POST', headers });
      const hgetData = await hgetRes.json();
      const flatFields = Array.isArray(hgetData.result) ? hgetData.result : [];
      const fields = {};
      for (let i = 0; i < flatFields.length; i += 2) {
        fields[flatFields[i]] = flatFields[i + 1];
      }
      return {
        id: entry.id,
        lastSeen: entry.lastSeen,
        username: fields.username || '',
        globalName: fields.globalName || '',
        avatarHash: fields.avatarHash || '',
      };
    }));

    res.setHeader('Cache-Control', 'no-store');
    res.status(200).json({ users });
  } catch (e) {
    res.status(502).json({ error: 'Could not read presence data', detail: e.message });
  }
}

module.exports = async (req, res) => {
  if (req.method === 'POST') {
    await handlePost(req, res);
    return;
  }
  if (req.method === 'GET') {
    await handleGet(req, res);
    return;
  }
  res.status(405).end();
};
