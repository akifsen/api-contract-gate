package tr.com.akifsen.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class GateTest {
    @TempDir
    Path temp;

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);

    Path fixture(String name) {
        return Path.of("fixtures", name + ".json");
    }

    @Test
    void compatibleAndBreakingUseRealDiffEngine() throws Exception {
        assertEquals(
                0,
                Gate.compare(fixture("base"), fixture("compatible"), null, CLOCK)
                        .exitCode());
        var report = Gate.compare(fixture("base"), fixture("breaking"), null, CLOCK);
        assertEquals(1, report.exitCode());
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("REQUEST_PARAMETERS")));
        assertEquals(report, Gate.compare(fixture("base"), fixture("breaking"), null, CLOCK));
    }

    @Test
    void waiverMetadataRequiresStringsAndExpiryUsesUtc() throws Exception {
        var report = Gate.compare(fixture("base"), fixture("breaking"), null, CLOCK);
        var array = Gate.JSON.createArrayNode();
        for (var finding : report.findings())
            array.addObject()
                    .put("fingerprint", finding.fingerprint())
                    .put("owner", "team")
                    .put("reason", "Reviewed migration")
                    .put("expires", "2026-09-15");
        Path waiver = temp.resolve("typed.json");
        Files.writeString(waiver, array.toString());
        Clock east = Clock.fixed(Instant.parse("2026-09-14T23:30:00Z"), ZoneOffset.ofHours(2));
        assertEquals(
                "WAIVED",
                Gate.compare(fixture("base"), fixture("breaking"), waiver, east).status());
        ((com.fasterxml.jackson.databind.node.ObjectNode) array.get(0)).put("owner", 123);
        Files.writeString(waiver, array.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> Gate.compare(fixture("base"), fixture("breaking"), waiver, CLOCK));
    }

    @Test
    void exactWaiversExpireAndDoNotCoverAnotherChange() throws Exception {
        var report = Gate.compare(fixture("base"), fixture("breaking"), null, CLOCK);
        var array = Gate.JSON.createArrayNode();
        for (var f : report.findings())
            array.addObject()
                    .put("fingerprint", f.fingerprint())
                    .put("owner", "api-team")
                    .put("reason", "Synthetic coordinated migration fixture")
                    .put("expires", "2026-10-01");
        Path waivers = temp.resolve("waivers.json");
        Files.writeString(waivers, array.toString());
        assertEquals(
                "WAIVED",
                Gate.compare(fixture("base"), fixture("breaking"), waivers, CLOCK)
                        .status());
        assertThrows(
                IllegalArgumentException.class,
                () -> Gate.compare(
                        fixture("base"),
                        fixture("breaking"),
                        waivers,
                        Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Gate.compare(fixture("base"), fixture("removed"), waivers, CLOCK));
    }

    @Test
    void invalidAndExternalAndMissingRefsNeverPass() throws Exception {
        for (String name : new String[] {"invalid", "external-ref", "missing-ref"})
            assertThrows(Exception.class, () -> Gate.compare(fixture("base"), fixture(name), null, CLOCK));
        Path duplicate = temp.resolve("duplicate.json");
        Files.writeString(duplicate, "{\"openapi\":\"3.0.3\",\"openapi\":\"3.0.2\"}");
        assertThrows(Exception.class, () -> Gate.document(duplicate));
        Path huge = temp.resolve("huge.json");
        Files.writeString(huge, " ".repeat(262145));
        assertThrows(Exception.class, () -> Gate.document(huge));
    }

    @Test
    void responseDirectionIsDifferentFromRequestDirection() throws Exception {
        assertEquals(
                1,
                Gate.compare(fixture("response-required"), fixture("response-optional"), null, CLOCK)
                        .exitCode());
        assertEquals(
                0,
                Gate.compare(fixture("response-optional"), fixture("response-required"), null, CLOCK)
                        .exitCode());
    }

    @Test
    void internalReferenceResolvesButRecursiveReferenceIsRejected() throws Exception {
        assertEquals(
                0,
                Gate.compare(fixture("internal-ref"), fixture("internal-ref"), null, CLOCK)
                        .exitCode());
        assertThrows(Exception.class, () -> Gate.document(fixture("recursive-ref")));
    }
}
