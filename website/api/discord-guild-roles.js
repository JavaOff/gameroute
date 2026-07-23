// Resolves Discord role names/colors for a guild, so the desktop app can show a connected
// user's real server role next to their profile. Role *names* require a bot token (the
// user's own OAuth token can only list which role IDs they hold, via guilds.members.read --
// GET /guilds/{id}/roles needs the bot to be a member of that guild), so this has to be a
// small server-side proxy: the bot token never ships inside the distributed desktop app.
module.exports = async (req, res) => {
  if (req.method !== 'GET') {
    res.status(405).end();
    return;
  }

  const guildId = req.query.guildId;
  if (!guildId || !/^\d+$/.test(guildId)) {
    res.status(400).json({ error: 'guildId query param (numeric) is required' });
    return;
  }

  const token = process.env.DISCORD_BOT_TOKEN;
  if (!token) {
    res.status(500).json({ error: 'DISCORD_BOT_TOKEN not configured' });
    return;
  }

  try {
    const discordRes = await fetch(`https://discord.com/api/v10/guilds/${guildId}/roles`, {
      headers: {
        Authorization: `Bot ${token}`,
        'User-Agent': 'GameRouteBot (https://gameroute.app, 1.0)',
      },
    });
    if (!discordRes.ok) {
      res.status(discordRes.status).json({ error: `Discord API returned ${discordRes.status}` });
      return;
    }
    const roles = await discordRes.json();
    // Only what the client actually needs -- id (to match against the member's role list),
    // name, color and position (to pick the highest / "main" role, same as Discord's own client).
    const trimmed = roles.map((r) => ({ id: r.id, name: r.name, color: r.color, position: r.position }));
    res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=600');
    res.status(200).json({ roles: trimmed });
  } catch (e) {
    res.status(502).json({ error: 'Could not reach Discord', detail: e.message });
  }
};
