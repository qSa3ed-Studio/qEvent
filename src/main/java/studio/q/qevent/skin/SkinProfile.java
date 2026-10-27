package studio.q.qevent.skin;

import java.util.UUID;

/**
 * A resolved Minecraft skin.
 *
 * @param uuid      owner of the skin
 * @param name      last known player name
 * @param value     Base64 {@code textures} property value (contains the skin and cape URLs)
 * @param signature Mojang signature over {@code value}, may be absent for cached data
 */
public record SkinProfile(UUID uuid, String name, String value, String signature) {

    public boolean hasTextures() {
        return value != null && !value.isBlank();
    }

    public SkinProfile withoutSignature() {
        return new SkinProfile(uuid, name, value, null);
    }
}
