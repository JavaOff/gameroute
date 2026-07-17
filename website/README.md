# GameRoute website

The marketing/landing site for GameRoute — plain HTML5 + Tailwind CSS +
vanilla JavaScript (no framework, no build-time templating). Everything
needed to preview or deploy it is in this folder.

## Quick start

```powershell
cd website
npm install       # one-time: installs the Tailwind CLI
npm run build     # compiles src\input.css -> dist\styles.css (minified)
```

Then open `index.html` directly, or serve the folder with any static file
server, e.g.:

```powershell
python -m http.server 8080
```

and visit `http://localhost:8080/`. During design work, `npm run watch`
rebuilds `dist\styles.css` automatically as you edit `src\input.css` or
`tailwind.config.js`.

There's no dev server needed beyond that — no React, no bundler, no
templating step. `index.html` is the whole page.

## Folder structure

```
website/
├── index.html                 the entire page (all 10 sections + nav + footer)
├── package.json                 Tailwind CLI build scripts
├── tailwind.config.js           theme: dark palette, accent red, animations
├── postcss.config.js
├── src/
│   ├── input.css                Tailwind directives + custom components (glass cards, neon buttons, reveal-on-scroll)
│   └── js/main.js               mobile menu, scroll reveal, animated counters, FAQ accordion, particle background
├── dist/
│   └── styles.css                built output (generated -- don't hand-edit)
├── assets/img/
│   ├── favicon.ico, icon-192.png, icon-512.png, logo-mark.png   (generated from packaging/IconGen.java in the main repo)
│   ├── og-image.png              Open Graph / Twitter card preview image
│   └── screenshots/               real screenshots captured from the running app
├── manifest.json
├── robots.txt
└── sitemap.xml
```

## Screenshots: real vs. illustrative

`assets/img/screenshots/dashboard.png`, `servers.png` and `statistics.png`
are genuine screenshots captured from the actual running GameRoute app —
nothing staged. The Traceroute and Optimizer panels in the gallery section
are instead hand-built HTML/CSS mockups styled to match the real app
exactly: a real traceroute screenshot would reveal the machine's actual
home router name and ISP hop hostnames, which isn't something to publish
on a public marketing page, so those two use placeholder data
(`router.local`, `isp-edge-1.example.net`, etc.) instead.

## Placeholders you need to replace before deploying

This was built without a real domain or GitHub repository to point at, so
the following are placeholders — search for them and swap in the real
values:

- **`https://github.com/JavaOff/gameroute`** — every GitHub link (nav,
  footer, download release link) uses this placeholder org/repo path.
- **`https://gameroute.app/`** — canonical URL, Open Graph URLs, and
  `robots.txt`'s sitemap reference.
- **Download link** — currently points at
  `.../releases/latest/download/GameRouteSetup.exe`, which assumes you'll
  publish `GameRouteSetup.exe` (see the main repo's `BUILD.md`) as a GitHub
  Release asset. Point it wherever you actually host the installer.
- **License** — the main repo doesn't currently have a `LICENSE` file, so
  the footer's "License" link and the download panel's "License: See
  GitHub" line both assume one will exist at
  `github.com/JavaOff/gameroute/blob/main/LICENSE`. Add one (MIT,
  GPL, source-available, etc. — your call) or update those links.
- **"Privacy Notice" footer link** — currently `href="#"`. The actual
  privacy claims are already written out in full in the page's Privacy
  section (`#privacy`-equivalent); wire this to a dedicated page if you
  want a separate one, or point it at that section instead.

## Design notes

- **Palette**: matte black (`ink-950` `#050506` through `ink-600`), single
  red accent (`accent` `#ff3b3b` / `accent-bright` `#ff5f5f`) — defined in
  `tailwind.config.js`, not hardcoded through the page.
- **Glassmorphism**: the `.glass` component class (translucent background +
  blur + hairline border + shadow) is the one surface style used
  everywhere — cards, the comparison table, FAQ items, all share it.
  `.glass-hover` adds the lift-on-hover interaction.
  Backdrop-blur is genuinely expensive on some low-end GPUs; if you ever
  see jank on target hardware, that's the first thing to profile.
- **Motion**: everything (scroll reveal, counters, FAQ accordion,
  particles) is in `src/js/main.js`, not inline `<script>` tags, and all
  respects `prefers-reduced-motion` for the particle canvas. Reveal
  animations degrade gracefully (contents are simply always-visible) if
  `IntersectionObserver` isn't available.
- **Honesty over hype**: the "How It Works" and comparison sections were
  written to match exactly what the real app does and doesn't do (see the
  main repo's `README.md`) — no invented usage statistics, no
  overstated claims about a competitor. If you update the app's feature
  set, update this page's claims to match.

## Performance / accessibility checklist

- Images use explicit `width`/`height` (prevents layout shift) and
  `loading="lazy"` on everything below the fold.
- The hero image uses `fetchpriority="high"` since it's the largest
  above-the-fold asset.
- Google Fonts is loaded with `preconnect` hints.
- Focus states are visible (`:focus-visible` outline in `input.css`) —
  don't remove this for aesthetics; it's required for keyboard navigation.
- Color contrast was chosen for WCAG AA against the dark background;
  double-check if you introduce new color combinations.
- Run Lighthouse yourself before shipping — this was hand-verified in a
  headless preview, not benchmarked against a live Lighthouse score.
