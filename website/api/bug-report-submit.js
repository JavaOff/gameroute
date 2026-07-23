// Only ever called when a user writes a description and presses "Send Report" themselves
// (see Settings > Report a Problem in the desktop app) -- nothing here runs in the background or
// on a schedule. Tied to their Discord id if connected, otherwise their anonymous install id, so
// the Admin panel can group a person's reports together into a history.
const { redisCommand } = require('../lib/redis');

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).end();
    return;
  }

  let payload;
  try {
    payload = JSON.parse(await readBody(req));
  } catch {
    res.status(400).json({ error: 'invalid JSON body' });
    return;
  }

  const { userId, isDiscordUser, username, globalName, avatarHash, version, os, description, logs } = payload || {};
  if (typeof userId !== 'string' || userId.length < 3 || userId.length > 100) {
    res.status(400).json({ error: 'userId is required' });
    return;
  }
  if (typeof description !== 'string' || description.trim().length === 0 || description.length > 5000) {
    res.status(400).json({ error: 'description must be 1-5000 characters' });
    return;
  }

  const { result: idResult, configured } = await redisCommand(['INCR', 'bugreport_id_counter']);
  if (!configured) {
    res.status(503).json({ error: 'Report storage is not configured' });
    return;
  }
  const reportId = String(idResult);
  const now = Math.floor(Date.now() / 1000);

  await redisCommand(['HSET', `bugreport:${reportId}`,
      'userId', userId,
      'isDiscordUser', isDiscordUser ? '1' : '0',
      'username', String(username || ''),
      'globalName', String(globalName || ''),
      'avatarHash', String(avatarHash || ''),
      'version', String(version || ''),
      'os', String(os || ''),
      'description', description,
      'logs', String(logs || '').slice(0, 20000),
      'submittedAt', String(now),
      'status', 'Open',
      'notes', '']);
  await redisCommand(['ZADD', `bugreports_by_user:${userId}`, now, reportId]);
  await redisCommand(['ZADD', 'bugreports_all', now, reportId]);
  await redisCommand(['SADD', 'bugreport_users', userId]);
  // Refreshed on every submission so the Admin panel's summary list shows this person's current
  // name/avatar even if only their oldest report is being looked at.
  await redisCommand(['HSET', `bugreport_user_profile:${userId}`,
      'username', String(username || ''),
      'globalName', String(globalName || ''),
      'avatarHash', String(avatarHash || ''),
      'isDiscordUser', isDiscordUser ? '1' : '0']);

  res.status(201).json({ reportId });
};
