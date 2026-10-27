# qEvent resource pack (optional)

The artwork pack is **optional**. qEvent works fully without it; the pack only adds the branded
display font and decorative glyphs for players who load it.

## Contents

```
resourcepack/
├── pack.mcmeta                    pack_format 46 + supported_formats 15..999
├── pack.png                       128×128 icon
└── assets/qevent/
    ├── font/display.json          bitmap providers + a reference to minecraft:default
    └── textures/
        ├── font/caps.png          A–Z sheet (12×16 cells)
        ├── font/lower.png         a–z sheet (10×16 cells)
        ├── font/digits.png        0–9 sheet (12×16 cells)
        ├── font/narrow.png        !?.,;:'"()[]-+ sheet (7×16 cells)
        └── gui/
            ├── badge.png          16×16 accent mark (glyph )
            ├── bracket_left.png   7×20 bracket (glyph )
            ├── bracket_right.png  7×20 bracket (glyph )
            └── divider.png        24×6 divider (glyph )
```

`display.json` ends with `{"type":"reference","id":"minecraft:default"}`, so **any character the pack
does not map falls back to the vanilla font** — no missing-glyph boxes.

Font usage in `config.yml` (`title.pack-template`):

```yaml
pack-template: '<gradient:#FFD166:#FF6B6B><font:qevent:display>EVENT ANNOUNCEMENT</font></gradient>'
```

## Packaging and hosting

```bash
cd resourcepack
jar --create --file ../qEvent-ResourcePack-1.0.0.zip .
cd ..
sha1sum qEvent-ResourcePack-1.0.0.zip        # put this in config.yml
```

Host the ZIP anywhere with a **public, direct HTTPS link** (your own CDN, a static file host, or a
GitHub Release asset such as
`https://github.com/qSa3ed-Studio/qEvent/releases/download/v1.0.0/qEvent-ResourcePack-1.0.0.zip`).
Do not link to an HTML download page — the client needs the file itself.

Then in `config.yml`:

```yaml
resource-pack:
  enabled: true
  url: 'https://…/qEvent-ResourcePack-1.0.0.zip'
  sha1: '<40 hex characters from sha1sum>'
  required: false          # false = players may decline; true = they are kicked if they decline
  prompt: '<gold>qEvent</gold> uses a small resource pack for its announcement artwork.'
  send-on-join: true
  fallback-when-unavailable: true
```

The SHA-1 is what clients on 1.20.3+ require for a verified download; qEvent logs a warning when
`required` is set but the hash is missing or malformed.

## Refusal and failure handling

qEvent tracks `PlayerResourcePackStatusEvent` per player:

| Client state | What qEvent does |
|---|---|
| `SUCCESSFULLY_LOADED` | Player gets the branded `pack-template` and glyphs |
| `ACCEPTED` / `DOWNLOADED` | Still downloading — vanilla-safe template until it finishes |
| `DECLINED` | Vanilla-safe template, announcement unaffected (or the player is kicked by the client if `required: true`) |
| `FAILED_DOWNLOAD` / `FAILED_RELOAD` / `DISCARDED` | Vanilla-safe template |
| Another plugin's pack UUID | Ignored — qEvent only tracks its own pack |

Because the fallback template is the *default*, a refused or failed pack can never produce an ugly
missing-glyph screen.

## Regenerating the artwork

```bash
java tools/GeneratePackAssets.java
```

The PNGs are committed to the repository so builds never depend on the fonts installed on a build
machine; run this only when you change the artwork.