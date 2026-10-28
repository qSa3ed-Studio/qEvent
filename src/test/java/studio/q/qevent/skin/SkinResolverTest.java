package studio.q.qevent.skin;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.Plugin;
import studio.q.qevent.config.PluginSettings;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for skin resolution against a local stand-in for Mojang's endpoints.
 *
 * <p>The scenario under test is an offline-mode server ({@code online-mode=false}): the player
 * has a UUID derived from their username, which Mojang does not know. The profile endpoint
 * answers "no profile" for it, so the resolver must fall back to the username instead of
 * giving up and rendering no head at all.</p>
 */
class SkinResolverTest {

    /** The username Mojang knows. */
    private static final String NAME = "Notch";

    /** Bukkit's offline UUID for {@code NAME}: MD5 of {@code "OfflinePlayer:" + NAME}. */
    private static final UUID OFFLINE_UUID = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));

    /** The real Mojang account UUID the stand-in reports for {@link #NAME}. */
    private static final UUID MOJANG_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static final String TEXTURES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly9leGFtcGxlIn19fQ==";
    private static final String SIGNATURE = "c2lnbmF0dXJl";

    @TempDir
    Path dataFolder;

    private HttpServer server;
    private SkinResolver resolver;

    /** Counts every request so a cache hit can be proven to skip the network. */
    private final AtomicInteger profileRequests = new AtomicInteger();
    private final AtomicInteger nameRequests = new AtomicInteger();

    @BeforeEach
    void startStandInMojang() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status;
            String body;
            if (path.startsWith("/name/")) {
                nameRequests.incrementAndGet();
                if (NAME.equalsIgnoreCase(path.substring("/name/".length()))) {
                    status = 200;
                    body = "{\"id\":\"" + MOJANG_UUID.toString().replace("-", "") + "\",\"name\":\"" + NAME + "\"}";
                } else {
                    status = 204;
                    body = "";
                }
            } else if (path.startsWith("/profile/")) {
                profileRequests.incrementAndGet();
                String uuid = path.substring("/profile/".length());
                if (uuid.equals(MOJANG_UUID.toString().replace("-", ""))) {
                    status = 200;
                    body = "{\"id\":\"" + uuid + "\",\"name\":\"" + NAME + "\",\"properties\":["
                            + "{\"name\":\"textures\",\"value\":\"" + TEXTURES + "\","
                            + "\"signature\":\"" + SIGNATURE + "\"}]}";
                } else {
                    // What the sessionserver answers for an unknown (offline-mode) UUID.
                    status = 204;
                    body = "";
                }
            } else {
                status = 404;
                body = "";
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        PluginSettings.SkinSection settings = new PluginSettings.SkinSection(
                true, 2_000, 2_000, 24,
                base + "/name/%name%",
                base + "/profile/%uuid%",
                "");
        resolver = new SkinResolver(pluginStub(), settings);
    }

    @AfterEach
    void stopStandInMojang() {
        if (resolver != null) {
            resolver.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("an offline-mode UUID that Mojang does not know still resolves through the username")
    void fallsBackToNameForOfflineUuid() throws Exception {
        SkinLookup first = resolver.resolve(NAME, OFFLINE_UUID, null).get(10, TimeUnit.SECONDS);

        assertTrue(first.succeeded(), "offline-mode servers must still get a real head: " + first);
        assertEquals(SkinLookup.Reason.NETWORK, first.reason());
        assertEquals(MOJANG_UUID, first.profile().orElseThrow().uuid());
        assertEquals(TEXTURES, first.profile().orElseThrow().value());
        assertEquals(SIGNATURE, first.profile().orElseThrow().signature());
        assertTrue(first.profile().orElseThrow().hasTextures());

        assertTrue(profileRequests.get() >= 1, "the supplied UUID must be tried first");
        assertEquals(1, nameRequests.get(), "the username fallback must be used exactly once");
    }

    @Test
    @DisplayName("the resolved skin is cached under the offline UUID, so the next call is local")
    void secondLookupIsServedFromCache() throws Exception {
        assertTrue(resolver.resolve(NAME, OFFLINE_UUID, null).get(10, TimeUnit.SECONDS).succeeded());

        int profileCalls = profileRequests.get();
        int nameCalls = nameRequests.get();

        SkinLookup second = resolver.resolve(NAME, OFFLINE_UUID, null).get(10, TimeUnit.SECONDS);

        assertTrue(second.succeeded());
        assertEquals(SkinLookup.Reason.CACHE, second.reason());
        assertEquals(profileCalls, profileRequests.get(), "a cache hit must not call the profile endpoint");
        assertEquals(nameCalls, nameRequests.get(), "a cache hit must not call the name endpoint");
    }

    @Test
    @DisplayName("an unknown username is reported instead of silently dropping the head")
    void unknownNameIsReported() throws Exception {
        SkinLookup lookup = resolver.resolve("DefinitelyNotARealPlayerName", OFFLINE_UUID, null)
                .get(10, TimeUnit.SECONDS);

        assertFalse(lookup.succeeded());
        assertEquals(SkinLookup.Reason.NOT_FOUND, lookup.reason());
    }

    /** Minimal {@link Plugin} stub: skin resolution only needs the data folder and a logger. */
    private Plugin pluginStub() {
        Logger logger = Logger.getLogger("qEvent-skin-test");
        logger.setUseParentHandlers(false);
        File folder = dataFolder.toFile();
        return (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> folder;
                    case "getLogger" -> logger;
                    case "getName" -> "qEvent";
                    case "isEnabled" -> true;
                    case "toString" -> "qEvent-skin-test";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "Plugin." + method.getName() + " is not stubbed");
                });
    }
}