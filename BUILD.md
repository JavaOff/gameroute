# Building GameRoute from source

## Prerequisites

- **JDK 21** — e.g. [Eclipse Temurin 21](https://adoptium.net/). Verify with:
  ```
  java -version
  ```
- **Maven 3.9+**. Verify with:
  ```
  mvn -version
  ```
  If you don't have Maven installed and don't want a system-wide install,
  you can use a portable copy: download `apache-maven-3.9.9-bin.zip` from
  https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/, extract it
  anywhere, and add its `bin/` folder to `PATH` for your current shell
  session:
  ```powershell
  $env:PATH = "C:\path\to\apache-maven-3.9.9\bin;$env:PATH"
  ```

Both `JAVA_HOME` and `mvn`/`java` must resolve to **Java 21**, not an older
JRE that may already be on your `PATH` (check with `mvn -version`, which
prints the Java version it's actually using).

- **WiX Toolset v3** — only needed to build the installer (`GameRouteSetup.exe`);
  skip this if you only want `gameroute.jar`. Get the portable binaries (no
  install needed) from
  https://github.com/wixtoolset/wix3/releases/download/wix314rtm/wix314-binaries.zip,
  extract anywhere, e.g. `%USERPROFILE%\tools\wix314` (the default
  `packaging\build-installer.ps1` looks there; override with `-WixHome`).
- **.NET Framework's `csc.exe`** — ships with every Windows install already
  (used to compile the small `Uninstall.exe` stub); nothing to install.

## Build

From the project root (the folder containing `pom.xml`):

```powershell
mvn compile          # just compile
mvn test             # compile + run unit tests
mvn package           # compile + test + produce target\gameroute.jar
```

`package` produces two jars in `target/`:

- `gameroute.jar` — the runnable fat jar (all dependencies bundled), built
  by the shade plugin. This is the one you distribute/run.
- `original-gameroute.jar` — the thin jar without dependencies (intermediate
  artifact, not meant to be run directly).

## Run during development

```powershell
mvn javafx:run
```

This uses the `javafx-maven-plugin` to launch `com.gameroute.Main` directly
with the JavaFX module path set up, which gives better error messages than
running the fat jar and is the fastest edit/test loop.

## Run the packaged jar

```powershell
java -jar target\gameroute.jar
```

(See [INSTALL.md](INSTALL.md) for end-user installation, including why the
jar's main class is `Launcher` rather than `Main`.)

## Generate JavaDoc

```powershell
mvn javadoc:javadoc
```

Output goes to `target\site\apidocs\index.html` — open it in a browser.

## Build the Windows installer (GameRouteSetup.exe) — recommended

```powershell
mvn clean package
```

That's it — on Windows this one command produces
**`target\dist\GameRouteSetup.exe`**, a real installer (not just a copied
folder): it installs to `C:\Program Files\GameRoute\`, bundles its own Java
runtime (end users never install Java), creates Start Menu and Desktop
shortcuts, registers an Add/Remove Programs entry with the app icon, ships
a local `Uninstall.exe`, requests elevation via UAC, and supports upgrading
in place (installing a newer build over an existing one, no manual
uninstall first). See [INSTALL.md](INSTALL.md) for what running it actually
does, and the "Installer architecture" section below for how it's built.

If you only want the jar (e.g. no WiX toolchain installed yet), skip the
installer step with:
```powershell
mvn package "-P!windows-installer"
```

### Installer architecture

`mvn clean package` builds the jar as before, then (on Windows, via the
`windows-installer` Maven profile bound to the `package` phase, running
after the shade plugin) `packaging\build-installer.ps1` chains together:

1. **jpackage** (`packaging\package-exe.ps1`, `--type app-image`) — wraps
   `gameroute.jar` with a bundled JRE into `target\dist\GameRoute\GameRoute.exe`
   plus a `runtime\` folder. This alone is enough to *run* GameRoute without
   installing anything (see "Run the native exe directly" in
   [INSTALL.md](INSTALL.md)) — it's also the input the installer packages up.
2. **csc.exe** compiles `installer\Uninstall.cs` into a small native
   `Uninstall.exe` stub. It doesn't reimplement uninstall logic — it looks up
   whichever GameRoute is currently registered (via the installer's stable
   `UpgradeCode`, so this keeps working across version upgrades without
   being rebuilt) and hands off to `msiexec /x`, the real Windows Installer
   uninstall path also reachable from *Apps & Features*.
3. **`heat.exe`** (WiX's harvester) recursively turns the entire app-image
   folder (the JRE has ~350 files) into WiX `<Component>` entries
   automatically — hand-authoring those would be impractical. Component
   GUIDs are auto-generated (`Guid="*"`) rather than pre-generated randomly,
   so they stay *stable* across rebuilds of the same file tree, which is
   what makes in-place upgrades reliable.
4. **`candle.exe` + `light.exe`** compile `installer\Product.wxs` (directory
   layout, shortcuts, Add/Remove Programs metadata, feature selection for
   the optional desktop shortcut) plus the harvested components into
   `GameRoute.msi`.
5. **`candle.exe` + `light.exe`** again compile `installer\Bundle.wxs` (a
   WiX Burn bootstrapper) wrapping that MSI into the final
   `GameRouteSetup.exe` — this is what actually makes it a single `.exe`
   with its own icon/version resource and UAC prompt; an MSI alone isn't one.

Two GUIDs in `installer\Product.wxs` (`<Product UpgradeCode>`) and
`installer\Bundle.wxs` (`<Bundle UpgradeCode>`) **must never change** between
releases — that's how Windows Installer recognizes "newer version of the
same product" and upgrades in place instead of installing side-by-side.

### Code signing

`GameRouteSetup.exe` builds **unsigned** by default — GameRoute never
generates or applies a self-signed/fake certificate. If you have a real
code-signing certificate, `signtool.exe` (Windows SDK) can sign it as part
of the build. Since the installer step runs through a Maven profile, the
simplest way to pass signing credentials is via environment variables
before building:

```powershell
$env:GAMEROUTE_SIGN_CERT = "C:\path\to\your-cert.pfx"
$env:GAMEROUTE_SIGN_PASSWORD = "your-pfx-password"
mvn clean package
```

Or run the packaging script directly with `-SignCertificate`/`-SignPassword`
parameters (see `packaging\build-installer.ps1`). Never commit a `.pfx` or
its password to source control.

The icon itself (`packaging\gameroute.ico`, and `src\main\resources\icons\gameroute.png`
used for the in-app window/tray icon) was generated procedurally with Java2D
rather than downloaded from anywhere -- see `packaging\IconGen.java`. To
tweak the artwork and regenerate both files:

```powershell
javac packaging\IconGen.java -d packaging
java -cp packaging IconGen packaging\gameroute.ico src\main\resources\icons\gameroute.png
```

(`IconGen` isn't part of the Maven build; it's a one-off tool, run manually
whenever the icon changes.)

## Project layout

```
pingperformance/
├── pom.xml                           windows-installer profile wires this all up
├── README.md / BUILD.md / INSTALL.md
├── packaging/
│   ├── gameroute.ico                 app icon (multi-resolution)
│   ├── IconGen.java                  icon generator source (Java2D, no assets)
│   ├── package-exe.ps1               jpackage wrapper -> GameRoute.exe (app-image)
│   └── build-installer.ps1           full pipeline -> GameRouteSetup.exe
├── installer/
│   ├── Product.wxs                   MSI: install dir, shortcuts, ARP metadata
│   ├── Bundle.wxs                    Burn bootstrapper -> the .exe wrapper
│   ├── Uninstall.cs                  local Uninstall.exe stub source
│   └── License.rtf                   installer wizard's license/disclosure page
├── src/main/java/com/gameroute/     application source
├── src/main/resources/
│   ├── css/dark.css                 theme
│   ├── icons/                        gameroute.png (window/tray icon)
│   ├── i18n/                        message bundle scaffold
│   └── logback.xml                  logging configuration
└── src/test/java/com/gameroute/     unit tests
```

## Troubleshooting the build

- **"release version 21 not supported"** — your `JAVA_HOME`/`PATH` is
  pointing at an older JDK. Fix `JAVA_HOME` and re-run.
- **JavaFX dependency resolution warnings** ("6 problems were encountered
  building the effective model for org.openjfx:javafx-controls") — these are
  benign classifier-resolution notices from the `org.openjfx` parent POM, not
  build failures. Ignore them as long as the build ends in `BUILD SUCCESS`.
- **`mvn` not recognized** — Maven's `bin/` isn't on `PATH` for this shell
  session; see the portable-Maven instructions above.
- **`candle.exe`/`heat.exe` not found** — set `-WixHome` (or edit the default
  in `packaging\build-installer.ps1`) to your WiX Toolset v3 extraction path.
- **"Application destination directory ... already exists"** from jpackage —
  a previous `GameRoute.exe` is still running and holding a file lock; close
  it (or `Stop-Process -Name GameRoute -Force`) and rebuild.
- **ICE38/ICE43 errors from `light.exe`** about a shortcut component's
  `KeyPath` — if you add more Start Menu/Desktop shortcuts to `Product.wxs`,
  each shortcut-only `<Component>` needs a `<RegistryValue Root="HKCU" ...
  KeyPath="yes">`, not a file, as its key path (the two shipped shortcut
  components already follow this pattern — copy them as a template).
- **"in comment after two dashes"** from `candle.exe` (or Maven itself) —
  XML comments can't contain a literal `--`; check any comment you've edited
  in `installer\*.wxs` or `pom.xml`.
