// Receives the GameRoute app's opt-in heartbeat: { installId, version }.
// Nothing else is accepted or stored -- no IP logging beyond what Vercel's
// platform logs for every request anyway, no per-user history.
module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).end();
    return;
  }

  const body = req.body || {};
  const installId = typeof body.installId === 'string' ? body.installId : null;
  if (!installId || installId.length < 8 || installId.length > 100) {
    res.status(400).json({ error: 'invalid installId' });
    return;
  }

  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (url && token) {
    const now = Math.floor(Date.now() / 1000);
    try {
      await fetch(`${url}/zadd/active_installs/${now}/${encodeURIComponent(installId)}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch {
      // best-effort -- a dropped heartbeat isn't worth failing the app's request over
    }
  }

  res.status(204).end();
};
