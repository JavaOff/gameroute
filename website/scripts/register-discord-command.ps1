# Registers the /userstats global slash command for the GameRoute Discord app.
# Reads DISCORD_BOT_TOKEN from website\.env so the token never has to be
# typed into a command or shell history. Run this once (re-run any time you
# want to update the command's name/description).

$envPath = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path $envPath)) {
    Write-Error "Missing $envPath -- copy your bot token into it as DISCORD_BOT_TOKEN=... first."
    exit 1
}

$token = $null
foreach ($line in Get-Content $envPath) {
    if ($line -match '^\s*DISCORD_BOT_TOKEN\s*=\s*(.+)\s*$') {
        $token = $matches[1].Trim()
    }
}

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Error "DISCORD_BOT_TOKEN is empty in $envPath -- paste your bot token there first."
    exit 1
}

$applicationId = "1528300009945169970"
$body = @{ name = "userstats"; description = "Show GameRoute usage stats"; type = 1 } | ConvertTo-Json

$headers = @{
    Authorization = "Bot $token"
    "Content-Type" = "application/json"
    # Discord's Cloudflare front-end rejects requests with PowerShell's default
    # User-Agent (error 40333, "CloudflareIsBlockingYourRequest") -- a descriptive
    # bot User-Agent, as Discord's own docs recommend, avoids that entirely.
    "User-Agent" = "GameRouteBot (https://gameroute.app, 1.0)"
}

try {
    $response = Invoke-RestMethod `
        -Uri "https://discord.com/api/v10/applications/$applicationId/commands" `
        -Method Post `
        -Headers $headers `
        -Body $body

    Write-Output "Registered /userstats (global -- can take up to an hour to appear):"
    $response | ConvertTo-Json
} catch {
    Write-Error "Registration failed:"
    if ($_.ErrorDetails.Message) {
        Write-Error $_.ErrorDetails.Message
    } else {
        Write-Error $_.Exception.Message
    }
    exit 1
}
