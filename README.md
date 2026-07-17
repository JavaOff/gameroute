# GameRoute

A Windows desktop companion for **League of Legends** that monitors your
network connection in real time and applies safe, user-confirmed tuning to
reduce ping, jitter and packet loss.

GameRoute is an independent, unofficial tool. It is **not affiliated with,
endorsed by, or a client of Riot Games**, and it does **not** route your
traffic through any private backbone or relay network the way commercial
tools like ExitLag do. Everything it does is visible, local, and reversible.

---

## What it actually does (and doesn't)

GameRoute is built to be honest about what's technically possible without
proprietary network infrastructure:

| Feature | How it really works |
|---|---|
| Ping monitoring | Runs the Windows `ping` tool once a second and parses the reported round-trip time. |
| Traceroute / route analysis | Runs `tracert` and parses every hop; flags hops with abnormal latency jumps or timeouts. |
| Region latency ("Servers" tab) | Pings `<platform>.api.riotgames.com` — Riot's own [documented Developer API gateway](https://developer.riotgames.com) for each platform. This is the only per-region hostname Riot publishes and guarantees to keep working; Riot no longer publishes a public list of raw game-server IPs. That gateway is Cloudflare-fronted for DDoS protection, so the measured number reflects your route to Riot's nearest edge network for that platform — **correlated with, but not identical to, live in-game latency**. Use it to compare regions relatively, not as an exact prediction. |
| Game detection | Uses `ProcessHandle` to detect `League of Legends.exe` / `LeagueClientUx.exe`. |
| Region detection | Reads the `Region=` key from the game's own `Config/game.cfg` next to the detected executable — no network calls, no reverse engineering of Riot's private APIs. |
| Process priority / CPU affinity | Sets the game process's Windows scheduling priority / core affinity via PowerShell. Resets when the game restarts (Windows does not persist this). |
| DNS tools | Measures a few public resolvers (Cloudflare, Google, Quad9, OpenDNS) and can switch an adapter to the fastest one, or flush the local DNS cache. |
| QoS tagging | Creates a real Windows QoS policy (`New-NetQosPolicy`) that DSCP-tags the game's traffic. This only influences how **your machine and QoS-aware routers on your own network** prioritize the traffic — it has **no effect** on the public internet path to Riot's servers. |
| "Optimize for League of Legends" | Runs a fixed, disclosed sequence of the above (priority, background-app scan, DNS flush + fastest-DNS switch). Every step is logged. |
| FPS | **Not implemented.** Reading real in-game FPS requires hooking DirectX/OpenGL (like RivaTuner Statistics Server does); GameRoute does not inject into the game process, so the Dashboard shows "N/A" for this tile rather than fake data. |
| Background app "optimization" | **Read-only.** GameRoute lists commonly bandwidth-heavy apps it finds running (OneDrive, Steam, etc.) so you can close them yourself — it never force-closes anything. |
| Private backbone / traffic rerouting | **Not implemented, and not claimed.** This is the core of what ExitLag actually sells (privately operated relay servers) and requires infrastructure GameRoute does not have. The "Smart Route Simulation" in the Statistics/Dashboard views only compares locally-measurable options (adapters, DNS resolvers) — it never reroutes your traffic. |

**GameRoute never changes a Windows setting without an explicit confirmation
dialog that states exactly what will change.** Actions requiring
Administrator privileges are labeled as such; if GameRoute isn't running
elevated, those actions will fail with a clear error rather than silently
doing nothing.

---

## Features

- **Live Dashboard** — current/average/min/max ping, jitter, packet loss,
  upload/download throughput, CPU and RAM usage, and a rolling latency graph.
- **Optimizer** — one-click "Optimize for League of Legends" plus every
  individual action (priority, affinity, DNS, QoS, TCP tuning, adapter
  renew) with its own confirmation and logged result.
- **Servers** — all 11 Riot platform regions (EUW, EUNE, NA, KR, BR, LAN,
  LAS, OCE, JP, TR, RU) with live-measured latency.
- **Traceroute** — hop-by-hop path analysis with problem-hop highlighting
  and route-change detection.
- **Statistics** — daily/weekly ping, jitter and packet-loss history with
  one-click CSV export.
- **Settings** — dark mode, auto-start with Windows, start minimized to
  tray, language (English/German scaffold), notification toggle.
- **Logs** — live tail of GameRoute's own log file, in-app.

## Design

GameRoute uses a fully custom, borderless "Ultra Dark" UI rather than a
default JavaFX window/tab-pane look:

- **Borderless window** with rounded corners, a soft drop shadow, and a
  custom title bar (brand mark, profile/notifications/settings icons,
  minimize/maximize/close) — dragging, resizing from any edge, and
  double-click-to-maximize are all hand-implemented (`ui/TitleBar.java`,
  `ui/WindowResizer.java`), since an undecorated `StageStyle.TRANSPARENT`
  window gets none of that from Windows for free.
- **Left navigation rail** (`ui/Sidebar.java`) with a glowing red indicator
  that slides between items, and a pinned, pulsing "OPTIMIZE NOW" shortcut.
- **Glass cards** throughout (`.glass-card` in `dark.css`): soft gradient
  fill, hairline border, drop shadow, gentle hover elevation.
- **Neon glow line charts** (`charts/GlowLineChart.java`) — a Canvas-based
  chart (multi-pass blurred stroke + gradient area fill) used for live ping,
  CPU/RAM/network sparklines and upload/download throughput, since restyling
  JavaFX's built-in `LineChart` to a bloom/glow look isn't practical.
- **Motion** (`ui/components/Animations.java`): JavaFX CSS has no
  `transition`/keyframe support, so every hover-scale, fade-in, staggered
  card entrance, glow pulse and page crossfade is driven from Java code
  rather than the stylesheet.
- **Hand-authored SVG icon set** (`ui/icons/Icons.java`) — `SVGPath` data
  drawn on a 24x24 grid (no bitmap assets, no icon-font dependency).

Two known simplifications versus a native OS window: dragging the title
bar while maximized doesn't auto-restore-then-drag, and the borderless
window's rounded corners are picked via CSS background-radius rather than a
hard geometric clip, so the very corner pixels are still technically
clickable/hoverable as part of the window.

**Fonts**: the stylesheet requests Inter and JetBrains Mono with graceful
fallback (`"Segoe UI Variable Text", "Inter", "Segoe UI"` for body text;
`"JetBrains Mono", "Cascadia Mono", "Consolas"` for numeric/mono text), but
neither font ships with GameRoute or Windows by default — installing them
wasn't done automatically since that means downloading and installing font
files on your system. Install both yourself (they're free/open-source) if
you want the exact typography from the design brief; otherwise GameRoute
renders cleanly with Segoe UI Variable (Windows 11) or Segoe UI/Consolas.

---

## Architecture

```
com.gameroute
├── Main / Launcher        application entry points
├── ui/                    JavaFX shell (MainView, AppServices, TitleBar, Sidebar,
│                          StatusBar, WindowResizer), tabs/, components/, icons/
├── network/               PingService, TracerouteService, DnsService,
│                          NetworkInterfaceService, QosService, RouteAnalyzer
├── monitor/                GameProcessMonitor, SystemMonitor, PingMonitor
├── optimizer/              OptimizationAction + implementations, OptimizerService
├── model/                   Immutable data records (PingSample, TracerouteHop, ...)
├── service/                 StatisticsService, CsvExportService, NotificationService, AutoStartService
├── charts/                  GlowLineChart (custom Canvas-based glow line chart)
├── config/                  Constants, AppConfig, ServerDatabase
└── utils/                   CommandRunner, ProcessUtils, OsUtils
```

Design notes:

- **Clean separation of concerns**: UI never shells out to the OS directly —
  it calls into `network`/`optimizer`/`service` classes, all of which funnel
  external process execution through the single `CommandRunner` choke point.
- **Dependency injection** is done manually via the `AppServices` record
  built once in `Main` — no DI framework is needed at this scale.
- **`OptimizationAction`** is a small interface (name, description, warning,
  admin requirement, `execute()`); every optimization is a standalone,
  independently testable implementation of it, so the UI never contains
  optimization logic itself.
- **Multithreading**: each monitor (`PingMonitor`, `SystemMonitor`,
  `GameProcessMonitor`) owns its own single-thread `ScheduledExecutorService`
  and only ever touches JavaFX nodes via `Platform.runLater`.
- **Logging**: SLF4J + Logback, console + rolling file appender at
  `~/.gameroute/logs/`.

---

## Requirements

- Windows 10/11 (the optimizer/network features shell out to `ping`,
  `tracert`, `netsh`, `ipconfig`, `reg` and PowerShell — all Windows-only).
- To just *run* the installed app: nothing else — it bundles its own Java
  runtime, and `GameRouteSetup.exe` installs everything it needs.
- To build from source: Java 21 (tested against Eclipse Temurin 21) and
  Maven 3.9+; add WiX Toolset v3 if you want `mvn clean package` to also
  produce the installer (optional — see [BUILD.md](BUILD.md)).

See [BUILD.md](BUILD.md) and [INSTALL.md](INSTALL.md) for step-by-step
instructions. `mvn clean package` on Windows produces a real installer,
**`GameRouteSetup.exe`**: installs to `C:\Program Files\GameRoute\` with its
own bundled Java runtime, Start Menu/Desktop shortcuts, an Add/Remove
Programs entry, a local `Uninstall.exe`, UAC elevation, and in-place
upgrade support (run a newer `GameRouteSetup.exe` over an existing install,
no need to uninstall first).

## Data & privacy

All data GameRoute collects (ping history, settings) stays on your machine
under `%USERPROFILE%\.gameroute\`. Nothing is uploaded anywhere.
