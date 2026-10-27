package studio.q.qevent.resourcepack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourcePackServiceTest {

    @Test
    @DisplayName("a valid SHA-1 hex string is decoded")
    void decodesValidSha1() {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        byte[] decoded = ResourcePackService.parseSha1(sha1);

        assertEquals(20, decoded.length);
        assertEquals(0x01, decoded[0] & 0xFF, "first byte comes from chars 0-1");
        assertEquals(0x23, decoded[9] & 0xFF, "byte 9 comes from chars 18-19");
        assertEquals(0x45, decoded[10] & 0xFF, "byte 10 comes from chars 20-21");
        assertEquals(0x67, decoded[19] & 0xFF, "last byte comes from chars 38-39");
    }

    @Test
    @DisplayName("dashes and mixed case are accepted")
    void toleratesFormattingInSha1() {
        byte[] withDashes = ResourcePackService.parseSha1(
                "01234567-89abcdef-01234567-89abcdef-01234567");
        byte[] plain = ResourcePackService.parseSha1(
                "0123456789abcdef0123456789abcdef01234567");

        assertEquals(20, withDashes.length);
        for (int i = 0; i < plain.length; i++) {
            assertEquals(plain[i], withDashes[i], "byte " + i + " must match");
        }
        assertEquals(20, ResourcePackService.parseSha1("ABCDEF0123456789ABCDEF0123456789ABCDEF01").length);
    }

    @Test
    @DisplayName("empty, short or non-hex values are rejected instead of producing garbage")
    void rejectsInvalidSha1() {
        assertNull(ResourcePackService.parseSha1(null));
        assertNull(ResourcePackService.parseSha1(""));
        assertNull(ResourcePackService.parseSha1("not-a-hash"));
        assertNull(ResourcePackService.parseSha1("0123456789abcdef0123456789abcdef0123456"), "39 chars");
        assertNull(ResourcePackService.parseSha1("zz23456789abcdef0123456789abcdef01234567"));
    }
}