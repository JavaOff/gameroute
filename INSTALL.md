# Installing / running GameRoute

## Option 1: GameRouteSetup.exe (recommended)

Build it with:
```powershell
mvn clean package
```
This produces `target\dist\GameRouteSetup.exe` — a real installer, not just
a copied folder. Run it and it will:

- Ask for **Administrator** privileges via the standard Windows UAC prompt
  (installing to `C:\Program Files\` requires this).
- Show a short license/disclosure page (GameRoute is unofficial, not
  affiliated with Riot Games) you need to accept before Install is enabled.
- Let you choose whether to create a **Desktop shortcut** (a **Start Menu**
  shortcut is always created).
- Install GameRoute — with its own bundled Java runtime, so **you never need
  Java installed separately** — into `C:\Program Files\GameRoute\`, with
  `GameRoute.exe` at the root.
- Register an **Add/Remove Programs** ("Apps & Features") entry, with the
  app icon, so it shows up like any other installed application.
- Optionally launch GameRoute immediately (checkbox on the finish page).

Running `GameRouteSetup.exe` again later — for a newer version — upgrades
the existing install in place; you don't need to uninstall first.

See [BUILD.md](BUILD.md) for prerequisites (WiX Toolset v3, in addition to
the JDK/Maven needed for every build) and how the installer is put together.

## Option 2: Native GameRoute.exe without installing

If you'd rather not install anything system-wide — just run it from a
folder you manage yourself:
```powershell
mvn package
.\packaging\package-exe.ps1
```
This produces `target\dist\GameRoute\GameRoute.exe` — the same self-contained,
Java-bundled executable the installer above packages up, just not installed
anywhere. Copy the whole `GameRoute\` folder (not just the `.exe` — it needs
the `runtime\` and `app\` folders next to it) wherever you like, and run it
by double-clicking `GameRoute.exe`, or right-click it → **Run as
administrator** to launch elevated.

## Option 3: Run the pre-built jar

1. Make sure a **Java 21 runtime** is installed (Eclipse Temurin 21 JRE or
   JDK: https://adoptium.net/). Check with:
   ```
   java -version
   ```
2. Copy `gameroute.jar` (from `target/` after building — see
   [BUILD.md](BUILD.md)) anywhere you like.
3. Run it:
   ```
   java -jar gameroute.jar
   ```
   or double-click the jar if `.jar` files are associated with `javaw.exe`
   on your system.

### Why the jar launches via `Launcher`, not `Main`

`Main` extends `javafx.application.Application`. Launching a class that
extends `Application` directly as a fat jar's main class fails on some
setups with *"Error: JavaFX runtime components are missing"*, because the
JVM's module check runs before the classpath is fully assembled. The jar's
manifest instead points at `com.gameroute.Launcher`, a plain class with a
`main()` that simply calls `Main.main()` — this sidesteps the check. You
don't need to do anything differently; `java -jar gameroute.jar` just works.

## Option 4: Build and run from source

See [BUILD.md](BUILD.md) for full instructions:

```powershell
mvn package
java -jar target\gameroute.jar
```

## First run

- GameRoute creates `%USERPROFILE%\.gameroute\` on first launch, containing:
  - `settings.properties` — your preferences
  - `stats\ping-history.csv` — ping history (append-only, human-readable)
  - `logs\gameroute.log` — rolling application log
- This folder lives under your user profile, **not** inside
  `C:\Program Files\GameRoute\` — so it's untouched by upgrading or
  uninstalling the app (see "Preserving your data" below).
- Beyond the installer itself asking for elevation up front, GameRoute makes
  no other system changes automatically. Everything that touches Windows
  settings afterward (auto-start, DNS, QoS policies, process priority,
  adapter cycling) only happens after you click **Run** on that specific
  action in the Optimizer or Settings tab and confirm the dialog that
  explains exactly what it will do.

## Running as Administrator

Several Optimizer actions (QoS policy creation, DNS changes, network adapter
renew, TCP auto-tuning) require an elevated process. If GameRoute isn't
running as Administrator, those actions will fail with a clear error message
in the Action Log rather than silently doing nothing. To run elevated:

- **If installed via `GameRouteSetup.exe`** (Option 1): find GameRoute in the
  Start Menu, right-click it → **Run as administrator** — or right-click
  `C:\Program Files\GameRoute\GameRoute.exe` directly.
- **Using the standalone `GameRoute.exe`** (Option 2): right-click it →
  **Run as administrator**.
- **Using the jar** (Option 3): right-click a shortcut to `javaw -jar
  gameroute.jar` → **Run as administrator** (the option only appears on a
  shortcut, not the jar file itself), or launch it from an already-elevated
  PowerShell/Command Prompt.

Process-priority and CPU-affinity changes to the *game's own* process do
**not** require elevation.

## Preserving your data across upgrades

`%USERPROFILE%\.gameroute\` (settings, ping history, logs) is completely
outside `C:\Program Files\GameRoute\`, so both installing a newer version
over an older one and uninstalling entirely leave it alone automatically —
there's nothing you need to do to preserve it, and nothing in the installer
or uninstaller ever touches that folder.

## Uninstalling

- **If installed via `GameRouteSetup.exe`**: use any of these (they all do
  the same thing — remove whatever GameRoute version is currently
  registered via Windows Installer):
  - **Settings → Apps → Apps & Features** (or the older Control Panel
    "Programs and Features") → GameRoute → **Uninstall**.
  - The **Uninstall GameRoute** shortcut in the Start Menu.
  - `C:\Program Files\GameRoute\Uninstall.exe` directly.
- **If using the standalone `GameRoute.exe`** (Option 2) or the jar (Option
  3): just delete the folder/jar (and any shortcut you made) — nothing was
  registered with Windows to begin with.
- If you enabled **Start with Windows** in Settings, turning it off first
  removes the `HKCU\...\Run` entry cleanly, but uninstalling doesn't require
  this — Windows Installer removes it either way.
- Ping history, settings and logs under `%USERPROFILE%\.gameroute\` are
  **not** deleted by uninstalling (see above) — remove that folder yourself
  if you want a completely clean slate.
- If you created a QoS policy via the Optimizer tab, remove it from an
  elevated PowerShell prompt (uninstalling GameRoute doesn't do this
  automatically, since it's a Windows networking policy, not an app file):
  ```powershell
  Remove-NetQosPolicy -Name "GameRoute-LoL" -Confirm:$false
  ```
