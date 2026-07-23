// Admin-only (Owner/Administrator/Moderator, role re-verified server-side via Discord's own API --
// same trust model as discord-presence.js) list/detail/update for user-submitted bug reports.
const { redisCommand, flatArrayToObject } = require('../lib/redis');

const GUILD_ID = '1305524197874995401';
const ADMIN_ROLE_IDS = new Set([
  '1398953662872682567', // Owner
  '1398953666047643769', // Administrator
  '1528383809144492202', // Moderator
]);

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

/** @return the verified access token if the caller is Owner/Administrator/Moderator, otherwise null (and writes the error response itself). */
async function requireAdmin(req, res) {
  const authHeader = req.headers['authorization'] || '';
  const accessToken = authHeader.startsWith('Bearer ') ? authHeader.slice('Bearer '.length) : null;
  if (!accessToken) {
    res.status(401).json({ error: 'missing Authorization: Bearer <your Discord access token>' });
    return null;
  }
  const roleIds = await callerRoleIds(accessToken);
  if (!roleIds.some((id) => ADMIN_ROLE_IDS.has(id))) {
    res.status(403).json({ error: 'not an Owner/Administrator/Moderator of the GameRoute server' });
    return null;
  }
  return accessToken;
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function reportFromHash(id, flatHash) {
  const fields = flatArrayToObject(flatHash);
  return {
    id,
    userId: fields.userId || '',
    isDiscordUser: fields.isDiscordUser === '1',
    username: fields.username || '',
    globalName: fields.globalName || '',
    avatarHash: fields.avatarHash || '',
    version: fields.version || '',
    os: fields.os || '',
    description: fields.description || '',
    logs: fields.logs || '',
    submittedAt: Number(fields.submittedAt || 0),
    status: fields.status || 'Open',
    notes: fields.notes || '',
  };
}

async function handleGet(req, res) {
  if (!(await requireAdmin(req, res))) {
    return;
  }

  const userId = req.query.userId;
  if (userId) {
    // Detail view: every report from this one user, newest first.
    const { result: idsWithScores } = await redisCommand(['ZREVRANGE', `bugreports_by_user:${userId}`, '0', '-1', 'WITHSCORES']);
    const flat = Array.isArray(idsWithScores) ? idsWithScores : [];
    const reportIds = [];
    for (let i = 0; i < flat.length; i += 2) {
      reportIds.push(flat[i]);
    }
    const reports = await Promise.all(reportIds.map(async (id) => {
      const { result } = await redisCommand(['HGETALL', `bugreport:${id}`]);
      return reportFromHash(id, result);
    }));
    res.setHeader('Cache-Control', 'no-store');
    res.status(200).json({ reports });
    return;
  }

  // Summary view: every distinct person who's ever submitted a report, with their count and
  // most recent submission time -- the Admin panel's "click a user to see their history" list.
  const { result: users } = await redisCommand(['SMEMBERS', 'bugreport_users']);
  const userIds = Array.isArray(users) ? users : [];
  const summaries = await Promise.all(userIds.map(async (id) => {
    const [profileRes, latestRes, countRes] = await Promise.all([
      redisCommand(['HGETALL', `bugreport_user_profile:${id}`]),
      redisCommand(['ZREVRANGE', `bugreports_by_user:${id}`, '0', '0', 'WITHSCORES']),
      redisCommand(['ZCARD', `bugreports_by_user:${id}`]),
    ]);
    const fields = flatArrayToObject(profileRes.result);
    const latestFlat = Array.isArray(latestRes.result) ? latestRes.result : [];
    return {
      userId: id,
      username: fields.username || '',
      globalName: fields.globalName || '',
      avatarHash: fields.avatarHash || '',
      isDiscordUser: fields.isDiscordUser === '1',
      reportCount: Number(countRes.result || 0),
      latestSubmittedAt: latestFlat.length >= 2 ? Number(latestFlat[1]) : 0,
    };
  }));
  summaries.sort((a, b) => b.latestSubmittedAt - a.latestSubmittedAt);
  res.setHeader('Cache-Control', 'no-store');
  res.status(200).json({ users: summaries });
}

async function handlePost(req, res) {
  if (!(await requireAdmin(req, res))) {
    return;
  }

  let payload;
  try {
    payload = JSON.parse(await readBody(req));
  } catch {
    res.status(400).json({ error: 'invalid JSON body' });
    return;
  }
  const { reportId, status, notes } = payload || {};
  if (typeof reportId !== 'string' || !reportId) {
    res.status(400).json({ error: 'reportId is required' });
    return;
  }
  const updates = [];
  if (typeof status === 'string') {
    updates.push('status', status);
  }
  if (typeof notes === 'string') {
    updates.push('notes', notes);
  }
  if (updates.length === 0) {
    res.status(400).json({ error: 'nothing to update -- pass status and/or notes' });
    return;
  }
  await redisCommand(['HSET', `bugreport:${reportId}`, ...updates]);
  res.status(200).json({ ok: true });
}

module.exports = async (req, res) => {
  if (req.method === 'GET') {
    await handleGet(req, res);
    return;
  }
  if (req.method === 'POST') {
    await handlePost(req, res);
    return;
  }
  res.status(405).end();
};
