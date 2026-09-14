package tr.com.akifsen.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class GateIT {
    @TempDir
    Path temp;

    int cli(String name, Path output, Path waivers) throws Exception {
        String java = Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
        var args = new ArrayList<>(List.of(
                java,
                "-Xmx192m",
                "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn",
                "-jar",
                Path.of("target/api-contract-gate-0.1.0-SNAPSHOT.jar")
                        .toAbsolutePath()
                        .toString(),
                "fixtures/base.json",
                "fixtures/" + name + ".json",
                output.toString()));
        if (waivers != null) args.add(waivers.toString());
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(temp.resolve(UUID.randomUUID() + ".log").toFile())
                .start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void packagedCliWritesReportsForPassBreakWaiverExpiryAndInvalid() throws Exception {
        Path output = temp.resolve("reports");
        assertEquals(0, cli("compatible", output, null));
        assertTrue(Files.readString(output.resolve("report.md")).contains("COMPATIBLE"));
        assertEquals(1, cli("breaking", output, null));
        var report = Gate.read(output.resolve("report.json"));
        assertEquals("BREAKING", report.path("status").asText());
        var array = Gate.JSON.createArrayNode();
        for (var f : report.path("findings"))
            array.addObject()
                    .put("fingerprint", f.path("fingerprint").asText())
                    .put("owner", "fixture-owner")
                    .put("reason", "Temporary synthetic coordinated migration")
                    .put(
                            "expires",
                            java.time.LocalDate.now(Clock.systemUTC())
                                    .plusDays(10)
                                    .toString());
        Path waiver = temp.resolve("waiver.json");
        Files.writeString(waiver, array.toString());
        assertEquals(0, cli("breaking", output, waiver));
        array.forEach(n -> ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("expires", "2000-01-01"));
        Files.writeString(waiver, array.toString());
        assertEquals(2, cli("breaking", output, waiver));
        assertEquals(2, cli("invalid", output, null));
        assertEquals(
                "ERROR", Gate.read(output.resolve("report.json")).path("status").asText());
    }
}
