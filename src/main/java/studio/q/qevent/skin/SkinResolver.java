package studio.q.qevent.skin;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import studio.q.qevent.config.PluginSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Resolves the real skin of an announcer.
 *
 * <p>Resolution order, cheapest and most reliable first:</p>
 * <ol>
 *   <li>An online player's game profile - the server already holds the signed textures, so
 *       no network request is needed at all.</li>
 *   <li>The paper profile of a cached player ({@link OfflinePlayer#getPlayerProfile()}).</li>
 *   <li>The local disk cache, keyed by UUID and valid for {@code skin.network.cache-hours}.</li>
 *   <li>The Mojang profile API, when {@code skin.network.enabled} is true.</li>
 * </ol>
 *
 * <p>All network work happens on a dedicated daemon executor; the Bukkit API and the
 * plugin's own state are only touched on the server thread.</p>
 */
public final class SkinResolver implements AutoCloseable {

    private static final String USER_AGENT =
            "qEvent/1.0 (+https://github.com/qSa3ed-Studio/qEvent)";
    private static final int MAX_CACHED_SKINS = 512;

    private final Plugin plugin;
    private final Path cacheDirectory;
    private final Map<UUID, CachedSkin> memoryCache = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    private volatile PluginSettings.SkinSection settings;
    private volatile HttpClient httpClient;

    public SkinResolver(Plugin plugin, PluginSettings.SkinSection settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.cacheDirectory = plugin.getDataFolder().toPath().resolve("skin-cache");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "qEvent-SkinResolver");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(2, factory);
        this.httpClient = buildClient(settings);
        createCacheDirectory();
        loadMemoryCache();
    }

    public void applySettings(PluginSettings.SkinSection updated) {
        this.settings = updated;
        this.httpClient = buildClient(updated);
    }

    private HttpClient buildClient(PluginSettings.SkinSection section) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(section.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private void createCacheDirectory() {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not create the skin cache directory at " + cacheDirectory, exception);
        }
    }

    private void loadMemoryCache() {
        if (!Files.isDirectory(cacheDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.list(cacheDirectory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(this::readCacheFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.FINE, "Could not read the skin cache directory.", exception);
        }
    }

    private void readCacheFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            CachedSkin skin = CachedSkin.fromJson(json);
            if (skin != null && skin.uuid() != null) {
                memoryCache.put(skin.uuid(), skin);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "Discarding unreadable skin cache entry " + path, exception);
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Nothing further we can do; the entry is simply ignored.
            }
        }
    }

    /**
     * Resolves a skin.
     *
     * @param name   player name to resolve, may be null when only a UUID is known
     * @param uuid   known UUID, may be null
     * @param online the online player when the announcer is connected, else null
     */
    public CompletableFuture<SkinLookup> resolve(String name, UUID uuid, Player online) {
        if (online != null) {
            Optional<SkinProfile> fromProfile = fromPaperProfile(
                    online.getPlayerProfile(), online.getUniqueId(), online.getName());
            if (fromProfile.isPresent()) {
                return CompletableFuture.completedFuture(
                        SkinLookup.found(fromProfile.get(), SkinLookup.Reason.ONLINE_PROFILE));
            }
        }

        UUID effectiveId = uuid != null ? uuid : (online != null ? online.getUniqueId() : null);
        Optional<SkinProfile> cached = fromCache(effectiveId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(SkinLookup.found(cached.get(), SkinLookup.Reason.CACHE));
        }

        if (!settings.networkEnabled() || (name == null || name.isBlank()) && effectiveId == null) {
            return CompletableFuture.completedFuture(SkinLookup.missing(SkinLookup.Reason.DISABLED));
        }

        PluginSettings.SkinSection snapshot = settings;
        return CompletableFuture.supplyAsync(() -> fetchFromNetwork(name, effectiveId, snapshot), executor);
    }

    /** Extracts the signed textures property from a Bukkit/Paper profile. */
    public static Optional<SkinProfile> fromPaperProfile(
            com.destroystokyo.paper.profile.PlayerProfile profile,
            UUID fallbackId,
            String fallbackName
    ) {
        if (profile == null) {
            return Optional.empty();
        }
        Optional<ProfileProperty> textures = profile.getProperties().stream()
                .filter(property -> "textures".equals(property.getName()))
                .findFirst();
        if (textures.isEmpty() || textures.get().getValue() == null || textures.get().getValue().isBlank()) {
            return Optional.empty();
        }
        UUID id = profile.getId() != null ? profile.getId() : fallbackId;
        String name = profile.getName() != null ? profile.getName() : fallbackName;
        ProfileProperty property = textures.get();
        return Optional.of(new SkinProfile(id, name, property.getValue(), property.getSignature()));
    }

    private Optional<SkinProfile> fromCache(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        CachedSkin cached = memoryCache.get(uuid);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.isExpired(settings.cacheHours())) {
            memoryCache.remove(uuid);
            try {
                Files.deleteIfExists(cacheFile(uuid));
            } catch (IOException ignored) {
                // A stale file is harmless: it stays invisible because it is no longer cached.
            }
            return Optional.empty();
        }
        return Optional.of(cached.toProfile());
    }

    private SkinLookup fetchFromNetwork(String name, UUID knownId, PluginSettings.SkinSection section) {
        try {
            UUID uuid = knownId;
            String resolvedName = name;
            if (uuid == null) {
                LookupResponse lookup = lookupByName(name, section);
                if (lookup.error() != null) {
                    return SkinLookup.missing(lookup.error(), lookup.detail());
                }
                uuid = lookup.uuid();
                resolvedName = lookup.name();
            }

            SkinProfile profile = lookupProfile(uuid, resolvedName, section);
            if (profile == null) {
                return SkinLookup.missing(SkinLookup.Reason.NOT_FOUND,
                        "the profile has no signed textures property");
            }
            cache(profile);
            return SkinLookup.found(profile, SkinLookup.Reason.NETWORK);
        } catch (RateLimitedException exception) {
            return SkinLookup.missing(SkinLookup.Reason.RATE_LIMITED);
        } catch (ProfileLookupException exception) {
            return SkinLookup.missing(SkinLookup.Reason.FAILED, exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "Skin lookup failed for " + name, exception);
            return SkinLookup.missing(SkinLookup.Reason.FAILED, exception.toString());
        }
    }

    private LookupResponse lookupByName(String name, PluginSettings.SkinSection section) throws Exception {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        String url = section.lookupUrlTemplate().replace("%name%", encoded);
        HttpResponse<String> response = send(url, section);
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return new LookupResponse(null, null, SkinLookup.Reason.NOT_FOUND);
        }
        if (response.statusCode() == 429) {
            return new LookupResponse(null, null, SkinLookup.Reason.RATE_LIMITED);
        }
        if (response.statusCode() != 200) {
            return new LookupResponse(null, null, SkinLookup.Reason.FAILED,
                    "name lookup returned HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = json.has("id") ? json.get("id").getAsString() : null;
        String resolved = json.has("name") ? json.get("name").getAsString() : name;
        if (id == null) {
            return new LookupResponse(null, null, SkinLookup.Reason.FAILED);
        }
        return new LookupResponse(fromUndashed(id), resolved, null);
    }

    private SkinProfile lookupProfile(UUID uuid, String name, PluginSettings.SkinSection section) throws Exception {
        String url = section.profileUrlTemplate().replace("%uuid%", uuid.toString().replace("-", ""));
        HttpResponse<String> response = send(url, section);
        if (response.statusCode() == 429) {
            throw new RateLimitedException();
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new ProfileLookupException("profile lookup returned HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String resolvedName = json.has("name") ? json.get("name").getAsString() : name;
        JsonArray properties = json.has("properties") ? json.getAsJsonArray("properties") : new JsonArray();
        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String value = property.has("value") ? property.get("value").getAsString() : null;
            String signature = property.has("signature") ? property.get("signature").getAsString() : null;
            if (value == null || value.isBlank()) {
                continue;
            }
            return new SkinProfile(uuid, resolvedName, value, signature);
        }
        return null;
    }

    private HttpResponse<String> send(String url, PluginSettings.SkinSection section) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(section.readTimeoutMs()))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void cache(SkinProfile profile) {
        CachedSkin entry = new CachedSkin(profile.uuid(), profile.name(), profile.value(), profile.signature(),
                Instant.now().getEpochSecond());
        memoryCache.put(profile.uuid(), entry);
        Path file = cacheFile(profile.uuid());
        try {
            Files.createDirectories(cacheDirectory);
            Files.writeString(file, entry.toJson(), StandardCharsets.UTF_8);
            pruneCache();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.FINE, "Could not write the skin cache entry.", exception);
        }
    }

    private void pruneCache() {
        if (memoryCache.size() <= MAX_CACHED_SKINS) {
            return;
        }
        memoryCache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().fetchedAt()))
                .limit(memoryCache.size() - MAX_CACHED_SKINS)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(uuid -> {
                    memoryCache.remove(uuid);
                    try {
                        Files.deleteIfExists(cacheFile(uuid));
                    } catch (IOException ignored) {
                        // Pruning is best-effort.
                    }
                });
    }

    private Path cacheFile(UUID uuid) {
        return cacheDirectory.resolve(uuid.toString() + ".json");
    }

    private static UUID fromUndashed(String raw) {
        String dashed = raw.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    /** Number of skins currently held in memory. */
    public int cachedSkinCount() {
        return memoryCache.size();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record LookupResponse(UUID uuid, String name, SkinLookup.Reason error, String detail) {
        LookupResponse(UUID uuid, String name, SkinLookup.Reason error) {
            this(uuid, name, error, null);
        }
    }

    private static final class RateLimitedException extends Exception {
        private RateLimitedException() {
            super("skin API rate limit reached");
        }
    }

    private static final class ProfileLookupException extends Exception {
        private ProfileLookupException(String message) {
            super(message);
        }
    }

    /** One cached skin, serialised to JSON on disk. */
    private record CachedSkin(UUID uuid, String name, String value, String signature, long fetchedAt) {

        boolean isExpired(int cacheHours) {
            if (cacheHours <= 0) {
                return true;
            }
            long age = Instant.now().getEpochSecond() - fetchedAt;
            return age > cacheHours * 3600L;
        }

        SkinProfile toProfile() {
            return new SkinProfile(uuid, name, value, signature);
        }

        String toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", uuid == null ? null : uuid.toString());
            json.addProperty("name", name);
            json.addProperty("value", value);
            json.addProperty("signature", signature);
            json.addProperty("fetchedAt", fetchedAt);
            return json.toString();
        }

        static CachedSkin fromJson(String raw) {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            UUID uuid = json.has("uuid") && !json.get("uuid").isJsonNull()
                    ? UUID.fromString(json.get("uuid").getAsString())
                    : null;
            String name = json.has("name") && !json.get("name").isJsonNull()
                    ? json.get("name").getAsString()
                    : null;
            String value = json.has("value") && !json.get("value").isJsonNull()
                    ? json.get("value").getAsString()
                    : null;
            String signature = json.has("signature") && !json.get("signature").isJsonNull()
                    ? json.get("signature").getAsString()
                    : null;
            long fetchedAt = json.has("fetchedAt") ? json.get("fetchedAt").getAsLong() : 0L;
            if (uuid == null || value == null || value.isBlank()) {
                return null;
            }
            return new CachedSkin(uuid, name, value, signature, fetchedAt);
        }
    }
}
