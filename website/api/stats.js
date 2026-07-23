// Public, unauthenticated aggregate usage numbers -- the same ones the Discord bot's
// /userstats command shows. Backs the desktop app's in-app Admin panel (visible only to
// Owner/Administrator/Moderator, enforced client-side since there's nothing sensitive here:
// no per-user data, just install counts anyone could also get by asking the Discord bot).
const { getOnlineNowCount, getDownloadCount } = require('../lib/discordStats');

module.exports = async (req, res) => {
  if (req.method !== 'GET') {
    res.status(405).end();
    return;
  }
  const [onlineNow, downloads] = await Promise.all([getOnlineNowCount(), getDownloadCount()]);
  res.setHeader('Cache-Control', 's-maxage=30, stale-while-revalidate=60');
  res.status(200).json({ onlineNow, downloads });
};
