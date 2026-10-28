# qEvent compatibility matrix

qEvent is compiled against the **Paper 1.20.6 API** with `--release 21`, the oldest API in its
supported range. Class files target Java 21, so the same JAR loads on any server whose runtime is
Java 21 or newer. Only public Bukkit/Paper/Adventure APIs are used — no NMS, no per-version adapters,
no separate compatibility JARs.

## Confirmed — verified on a real server during development

| Minecraft | Paper build | Java | Status | What was verified |
|---|---|---|---|---|
| 1.20.6 | `paper-1.20.6-151` | 21.0.11 (Zulu) | **Supported & tested** | Plugin loads and enables; `/event help`, `announce`, `title`, `preview` (console guard), `reload`, unknown-subcommand and empty-message errors all behave; Mojang skin lookup returned a signed skin; a per-viewer `item_display` head was spawned; the client received `set_title_text`, `set_title_subtitle` and `set_title_time` (8/98/14) packets; missing-skin fallback message shown for an unknown name; cooldown enforced with `admin.bypass-cooldown: false` |

Unit tests (25) cover configuration parsing/clamping, safe legacy config migration, text formatting and escaping,
version parsing and SHA-1 decoding; they run in `mvn test` and in CI on every push.

## Untested — expected to work, not verified here

| Minecraft | Paper | Java | Notes |
|---|---|---|---|
| 1.20.5 | Paper 1.20.5 | 21 | Same API generation as 1.20.6 |
| 1.21.x (1.21 – 1.21.11) | Paper `paper-1.21.8-60` | 21.0.11 (Zulu) | **Booted & commands verified**: plugin loaded/enabled with no errors; `/event help`, `announce` and `reload` answered correctly over the console. The full client-packet check (title + head entity) was only run on 1.20.6 |
| 26.1 / 26.2 | Paper 26.x | 25 | Same API generation as 26.3, not booted |
| 26.3 | Paper `paper-26.3-226` | 25.0.3 (Zulu) | **Booted & commands verified**: plugin loaded/enabled with no errors; `announce`, unknown-subcommand error and `reload` answered correctly over the console. API symbols also checked with `javap` against the `26.3` API JAR |

The API surface qEvent relies on (`ItemDisplay`, `Display#setTransformation`, `Player#hideEntity`,
`SkullMeta#setPlayerProfile`, `com.destroystokyo.paper.profile.*`, `setResourcePack(UUID,…,boolean)`,
`PlayerResourcePackStatusEvent`, Adventure `Title`/`BossBar`/`Sound`/`Style#font`) was compared
against both the `1.20.6` and `26.3` Paper API JARs: every symbol exists in both.

## Unsupported

| Minecraft | Reason |
|---|---|
| 1.20.4 and older (incl. 1.19.x, 1.17–1.18, 1.16.5) | Paper requires Java 17 (or 8/16) there; qEvent ships Java 21 class files. Also outside the API baseline. |
| Spigot / CraftBukkit without Adventure | qEvent uses Adventure components for titles and text. Paper or a Paper fork is required. |
| Folia | `folia-supported: false`; qEvent uses the standard Bukkit scheduler. |

## Known limitations

- A client that declines (or fails to download) the optional artwork resource pack loses only the
  branded font/glyphs; the announcement itself still renders with vanilla-safe text.
- Skin lookups need outbound HTTPS to `api.mojang.com` / `sessionserver.mojang.com`; with
  `skin.network.enabled: false` or when offline, the head is skipped and the text announcement shows.
- Head rendering uses one short-lived display entity per viewer; it is hidden from other players with
  `Player#hideEntity` and removed when the announcement ends.