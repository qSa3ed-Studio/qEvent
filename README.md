# qEvent

**Cinematic event announcements for Minecraft (Paper) — the announcer's real skin head, a centred animated title and sound, broadcast to every online player.**
By **qSa3ed Studio**.

```
/event announce HALLOWEEN EVENT STARTED! Join the lobby now!
```

Every online player (including the sender) instantly sees a title + subtitle announcement with the exact
message, the announcer's attribution, a sound cue, and — where supported — the announcer's **actual
Minecraft head** floating in front of the viewer's camera.

---

## Features

- `/event announce <message>` — broadcast to all online players.
- `/event announce <player> <message>` — announce with another player's head (online, or quoted
  for names the server has not seen yet: `/event announce "Notch" …`).
- `/event title <title> <subtitle>` — custom title/subtitle.
- `/event preview` — preview the announcement privately for yourself.
- `/event reload`, `/event help` — with full tab completion.
- **Real skin head** rendered as a player-head item on a per-viewer display entity (see
  [docs/RENDERING.md](docs/RENDERING.md)). Never a fake Steve/Alex head — if the real skin cannot be
  retrieved, the head is skipped and the announcement still shows.
- Skin resolution: online profile → local cache → Mojang API (async, with timeouts and a disk cache).
- Configurable duration, colors, MiniMessage **and** legacy `&` formatting, gradients, animation style,
  sounds, head position/scale, resource-pack delivery, language file, cooldown and queueing.
- Optional artwork resource pack (branded display font + glyphs) with graceful fallback for players
  who decline it — see [docs/RESOURCE_PACK.md](docs/RESOURCE_PACK.md).
- No NMS, no scoreboard spam, no chat broadcast — one plugin, one JAR.

---

## Requirements

| | |
|---|---|
| Server | Paper (or a Paper fork) **1.20.5 – 26.3** — see [COMPATIBILITY.md](COMPATIBILITY.md) |
| Java | **21+** (Paper 1.20.5 requires Java 21; the 26.x line requires Java 25) |
| Build | JDK 21+ and Maven 3.8+ |

---

## Installation

1. Build (or download from the [Releases page](https://github.com/qSa3ed-Studio/qEvent/releases)) `qEvent-<version>.jar`.
2. Drop it into your server's `plugins/` folder.
3. Start the server. `config.yml` and `lang/en.yml` are generated on first start.
4. Stop the server, edit `plugins/qEvent/config.yml`, start again (or run `/event reload`).
5. Optional: publish `resourcepack/qEvent-ResourcePack-1.0.0.zip` (or a `dist/` build) on your own
   host and set `resource-pack.url` + `resource-pack.sha1`.

```bash
mvn -B package          # builds target/qEvent-<version>.jar and runs the unit tests
```

---

## Commands and permissions

| Command | Description | Permission |
|---|---|---|
| `/event announce <message>` | Announce to every online player | `qevent.announce` |
| `/event announce <player> <message>` | Announce with another player's head | `qevent.announce` |
| `/event title <title> <subtitle>` | Custom title + subtitle (`"` quotes or ` \| ` allowed) | `qevent.announce` |
| `/event preview` | Preview for yourself only | `qevent.preview` |
| `/event reload` | Reload config + language files | `qevent.reload` |
| `/event help` | Help screen | everyone |

| Permission | Default | Notes |
|---|---|---|
| `qevent.announce` | op | Broadcast announcements and titles |
| `qevent.preview` | op | Private preview |
| `qevent.reload` | op | Reload configuration |
| `qevent.admin` | op | Implies all of the above and bypasses the cooldown |

The console can run every subcommand except `preview`. Console announcements use
`announcement.console-name`; set `skin.console-skin` to a real player name if the console should
have a head.

---

## Configuration (summary)

Everything is documented inline in [src/main/resources/config.yml](src/main/resources/config.yml):

- `announcement.*` — duration, cooldown, queueing, message length limit, console name.
- `title.*` / `subtitle.*` / `action-bar.*` / `boss-bar.*` — templates, fade timing, colors,
  gradients, per-player artwork variant.
- `sounds.play` — a list of sound cues with volume, pitch and delay.
- `head.*` — mode (`AUTO`/`ENTITY`/`TEXT_ONLY`), distance, screen offset, scale, item transform,
  billboard, brightness, glow, missing-skin behaviour.
- `animation.*` — entrance/exit ticks, style (`NONE`, `SCALE`, `SLIDE`, `SCALE_SLIDE`).
- `skin.*` — network lookup on/off, timeouts, cache TTL, API templates.
- `resource-pack.*` — URL, SHA-1, required/optional, prompt, fallback behaviour.
- `admin.*` — cooldown bypass, announcement logging.

Text accepts **MiniMessage tags** (`<gradient:#FFD166:#FF6B6B>…</gradient>`, `<bold>`, `<#RRGGBB>`)
and **legacy codes** (`&6`, `&l`, `&#RRGGBB`) in every text field.

---

## Documentation

- [COMPATIBILITY.md](COMPATIBILITY.md) — what was actually tested, what is untested, what is unsupported.
- [docs/RENDERING.md](docs/RENDERING.md) — why the head is a display entity, not a HUD image.
- [docs/RESOURCE_PACK.md](docs/RESOURCE_PACK.md) — pack contents, hosting, SHA-1, refusal handling.
- [CHANGELOG.md](CHANGELOG.md) — release history.
- [SECURITY.md](SECURITY.md) — reporting vulnerabilities.

---

## Building from source

```bash
git clone https://github.com/qSa3ed-Studio/qEvent.git
cd qEvent
mvn -B clean package        # unit tests run automatically
```

Output: `target/qEvent-<version>.jar` (no runtime dependencies are shaded — the plugin is
dependency-free at runtime).

Resource-pack artwork is generated by `tools/GeneratePackAssets.java` and committed to the repo:

```bash
java tools/GeneratePackAssets.java
```

---

## Contributing & security

Bug reports go to [GitHub Issues](https://github.com/qSa3ed-Studio/qEvent/issues).
Security issues: see [SECURITY.md](SECURITY.md) — please do not open a public issue for those.

## License

MIT — see [LICENSE](LICENSE). © 2026 qSa3ed Studio.