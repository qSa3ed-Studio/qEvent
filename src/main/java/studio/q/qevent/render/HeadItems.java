package studio.q.qevent.render;

import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import studio.q.qevent.skin.SkinProfile;

import java.util.Optional;

/**
 * Builds a real player head item from a resolved skin.
 *
 * <p>The signed {@code textures} property is applied to a Paper profile, which is what makes
 * the client render the announcer's own skin. If no signed texture is available this returns
 * an empty result on purpose: qEvent never falls back to a generic Steve or Alex head.</p>
 */
public final class HeadItems {

    private HeadItems() {
    }

    public static Optional<ItemStack> create(SkinProfile skin) {
        if (skin == null || !skin.hasTextures()) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (!(stack.getItemMeta() instanceof SkullMeta meta)) {
            return Optional.empty();
        }
        if (!applySkin(meta, skin)) {
            return Optional.empty();
        }
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    /**
     * Applies the skin to a skull meta.
     *
     * <p>{@code setPlayerProfile} is the Paper API that exists across the whole supported
     * range (verified against both the 1.20.6 and 26.3 APIs).</p>
     */
    @SuppressWarnings("deprecation")
    public static boolean applySkin(SkullMeta meta, SkinProfile skin) {
        try {
            var profile = skin.uuid() != null
                    ? Bukkit.createProfile(skin.uuid(), skin.name())
                    : Bukkit.createProfile(skin.name());
            if (profile == null) {
                return false;
            }
            profile.setProperty(skin.signature() == null
                    ? new ProfileProperty("textures", skin.value())
                    : new ProfileProperty("textures", skin.value(), skin.signature()));
            meta.setPlayerProfile(profile);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
