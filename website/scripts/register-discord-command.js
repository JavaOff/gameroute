// Registers the /userstats global slash command for the GameRoute Discord app.
// Reads DISCORD_BOT_TOKEN from website/.env so the token never has to be typed
// into a command or shell history. Run with: node scripts/register-discord-command.js
const fs = require('fs');
const path = require('path');

const envPath = path.join(__dirname, '..', '.env');
if (!fs.existsSync(envPath)) {
  console.error(`Missing ${envPath} -- copy your bot token into it as DISCORD_BOT_TOKEN=... first.`);
  process.exit(1);
}

const envText = fs.readFileSync(envPath, 'utf8');
const match = envText.match(/^\s*DISCORD_BOT_TOKEN\s*=\s*(.+?)\s*$/m);
const token = match ? match[1].trim() : '';

if (!token) {
  console.error(`DISCORD_BOT_TOKEN is empty in ${envPath} -- paste your bot token there first.`);
  process.exit(1);
}

const applicationId = '1528300009945169970';

async function main() {
  const res = await fetch(`https://discord.com/api/v10/applications/${applicationId}/commands`, {
    method: 'POST',
    headers: {
      Authorization: `Bot ${token}`,
      'Content-Type': 'application/json',
      // A descriptive bot User-Agent, as Discord's own docs recommend --
      // requests with no/odd User-Agent strings get blocked by Discord's
      // Cloudflare front-end with a misleading "internal network error" (40333).
      'User-Agent': 'GameRouteBot (https://gameroute.app, 1.0)',
    },
    body: JSON.stringify({ name: 'userstats', description: 'Show GameRoute usage stats', type: 1 }),
  });

  const text = await res.text();
  if (!res.ok) {
    console.error(`Registration failed (HTTP ${res.status}):`);
    console.error(text);
    process.exit(1);
  }

  console.log('Registered /userstats (global -- can take up to an hour to appear):');
  console.log(text);
}

main().catch((e) => {
  console.error('Registration failed:', e.message);
  process.exit(1);
});
