# How qEvent renders the announcement (and the head)

## What Minecraft actually allows

1. **Titles, subtitles, action bars and boss bars accept a `Component`, not an image.** Text can carry
   a custom resource-pack *font*, and a font's bitmap provider can display a PNG — but that PNG must
   live inside the pack, so it is static and identical for every player.
2. **A player's skin is not a texture in the pack.** Mojang's skin lives at a per-account
   `textures.minecraft.net` URL referenced by a signed `textures` profile property. A pack cannot
   reference it, and generating a per-announcement pack would force every player to re-download a pack
   mid-game — unacceptable UX.

Therefore there is **no API that draws an arbitrary image, let alone another player's skin, inside a
title/HUD element.** The only vanilla mechanism that renders a real, per-account skin is the actual
`player_head` item model.

## Chosen approach: a private, camera-relative item display

For every viewer, qEvent:

1. builds a `player_head` `ItemStack` carrying the announcer's signed `textures` property
   (`HeadItems`), so the client renders the announcer's own skin;
2. spawns an `ItemDisplay` in the viewer's world with `billboard = CENTER`, `itemTransform = HEAD`,
   full brightness and no shadow (`ItemDisplayHead`);
3. immediately hides that entity from every other player with `Player#hideEntity(Plugin, Entity)`
   (`ViewVisibility`), giving each viewer a private head;
4. re-teleports it every tick to an anchor computed from the viewer's eyes and look direction
   (`Viewport`), which pins it to a fixed spot on screen while the player looks around;
5. interpolates `Transformation` scale/translation for the entrance and exit animations.

The vertical anchor is deliberately conservative. Minecraft's vertical field of view is narrower
than its horizontal field of view on common widescreen displays; an offset that looks small in
blocks can place the head completely above the visible frame. The default (`distance: 2.2`,
`offset-y: 0.45`, `scale: 0.8`) is calibrated to keep the head in the upper screen area on typical
16:9 setups. It is still affected by client FOV and aspect ratio, so server owners can adjust those
three settings if their clients use unusual display settings.

Because the entity is short-lived and removed in `cleanup()`, nothing is left behind — even if the
plugin is disabled mid-announcement (`onDisable` → `AnnouncementService.shutdown()`).

### Fallbacks

| Situation | Behaviour |
|---|---|
| Display entities or per-viewer hiding unavailable | `ItemDisplayHead.isSupported()` false → text-only presentation |
| `head.mode: TEXT_ONLY` | No entity at all |
| Skin cannot be resolved (offline player, network down, rate-limited, disabled) | Head skipped, announcement still shown, admin gets a reason; **never a fake Steve/Alex** |
| `head.on-missing-skin: TEXT_ONLY` | Same, but always logged |
| Player declined the resource pack | Vanilla-safe templates instead of the branded ones |

## Skin resolution order (`SkinResolver`)

1. **Online player profile** — the server already holds the signed textures; no network call.
2. **Local disk/memory cache** (`plugins/qEvent/skin-cache/<uuid>.json`, TTL `skin.network.cache-hours`).
3. **Mojang API** (async, daemon threads, connect/read timeouts, custom User-Agent):
   `api.mojang.com/users/profiles/minecraft/<name>` → UUID, then
   `sessionserver.mojang.com/session/minecraft/profile/<uuid>` → signed `textures` property.

Failures are classified (`NOT_FOUND`, `RATE_LIMITED`, `FAILED`, `DISABLED`) and reported with a
detail string in the log so administrators can diagnose them.

## Why not a resource-pack font glyph?

A bitmap font glyph *is* used — but only for the **static** branded artwork (display font, badge,
brackets, divider), because that is a shared asset. It can never show a specific player's head.
See [RESOURCE_PACK.md](RESOURCE_PACK.md).