package tr.com.akifsen.contract;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.*;

public final class Gate {
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(40)
                    .maxStringLength(65536)
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public record Finding(
            String fingerprint,
            String rule,
            String location,
            String consumerImpact,
            boolean waived,
            String owner,
            String reason,
            String expires,
            String evidence) {}

    public record Report(
            String status,
            int exitCode,
            String oldSha256,
            String newSha256,
            List<Finding> findings,
            List<String> warnings) {}

    record Document(JsonNode tree, OpenAPI model, String hash) {}

    record Waiver(String fingerprint, String owner, String reason, LocalDate expires) {}

    static JsonNode read(Path file) throws Exception {
        try (var in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(262145);
            if (bytes.length > 262144) throw new IllegalArgumentException("Input exceeds 256 KiB");
            try (var parser = JSON.createParser(bytes)) {
                JsonNode tree = JSON.readTree(parser);
                if (tree == null || parser.nextToken() != null)
                    throw new IllegalArgumentException("Expected one JSON document");
                return tree;
            }
        }
    }

    static String hash(String text) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var result = JSON.createObjectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, sorted(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JSON.createArrayNode();
            node.forEach(value -> result.add(sorted(value)));
            return result;
        }
        return node;
    }

    static void validateRefs(JsonNode node, JsonNode root, int[] count, Set<String> active, int depth) {
        if (++count[0] > 20000 || depth > 40)
            throw new IllegalArgumentException("Document/reference complexity budget exceeded");
        if (node.isObject() && node.has("$ref")) {
            JsonNode ref = node.get("$ref");
            String value = ref.asText();
            if (!ref.isTextual() || !value.startsWith("#/"))
                throw new IllegalArgumentException("Only internal JSON Pointer refs are supported");
            JsonNode target = root.at(value.substring(1));
            if (target.isMissingNode()) throw new IllegalArgumentException("Unresolved internal reference");
            if (!active.add(value)) throw new IllegalArgumentException("Recursive schemas are outside v1 scope");
            validateRefs(target, root, count, active, depth + 1);
            active.remove(value);
        }
        if (node.isContainerNode()) node.forEach(child -> validateRefs(child, root, count, active, depth + 1));
    }

    static Document document(Path file) throws Exception {
        JsonNode tree = read(file);
        if (!tree.isObject()
                || !tree.path("openapi").asText().matches("3\\.0\\.[0-3]")
                || !tree.path("paths").isObject()
                || tree.path("paths").size() > 100)
            throw new IllegalArgumentException("Expected OpenAPI 3.0.0 through 3.0.3 JSON with at most 100 paths");
        validateRefs(tree, tree, new int[] {0}, new HashSet<>(), 0);
        var options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        var parsed = new OpenAPIV3Parser().readContents(tree.toString(), null, options);
        if (parsed.getOpenAPI() == null
                || (parsed.getMessages() != null && !parsed.getMessages().isEmpty()))
            throw new IllegalArgumentException("OpenAPI parser validation failed");
        return new Document(tree, parsed.getOpenAPI(), hash(sorted(tree).toString()));
    }

    static Map<String, Waiver> waivers(Path file, LocalDate today) throws Exception {
        if (file == null) return Map.of();
        JsonNode tree = read(file);
        if (!tree.isArray() || tree.size() > 128)
            throw new IllegalArgumentException("Expected at most 128 exact waivers");
        Map<String, Waiver> result = new HashMap<>();
        for (JsonNode node : tree) {
            if (!node.isObject() || node.size() != 4)
                throw new IllegalArgumentException("Waiver needs fingerprint, owner, reason, expires only");
            String fingerprint = node.path("fingerprint").asText(),
                    owner = node.path("owner").asText(),
                    reason = node.path("reason").asText();
            if (!fingerprint.matches("[0-9a-f]{64}")
                    || owner.isBlank()
                    || owner.length() > 120
                    || reason.isBlank()
                    || reason.length() > 500) throw new IllegalArgumentException("Invalid exact waiver");
            LocalDate expires = LocalDate.parse(node.path("expires").asText());
            if (!today.isBefore(expires))
                throw new IllegalArgumentException("Expired waiver (expiry date is exclusive UTC)");
            if (result.put(fingerprint, new Waiver(fingerprint, owner, reason, expires)) != null)
                throw new IllegalArgumentException("Duplicate waiver");
        }
        return result;
    }

    public static Report compare(Path before, Path after, Path exceptions, Clock clock) throws Exception {
        Document old = document(before), next = document(after);
        var waivers = waivers(exceptions, LocalDate.now(clock));
        var diff = OpenApiCompare.fromSpecifications(old.model(), next.model());
        List<Finding> findings = new ArrayList<>();
        for (var missing : diff.getMissingEndpoints())
            add(
                    findings,
                    "ENDPOINT_REMOVED",
                    missing.getMethod() + " " + missing.getPathUrl(),
                    "Existing callers can no longer invoke this endpoint.",
                    old,
                    next,
                    waivers);
        for (var op : diff.getChangedOperations()) {
            String location = op.getHttpMethod() + " " + op.getPathUrl();
            int count = findings.size();
            category(
                    findings,
                    "REQUEST_PARAMETERS",
                    location,
                    op.getParameters(),
                    "Previously valid request parameters may be rejected.",
                    old,
                    next,
                    waivers);
            category(
                    findings,
                    "REQUEST_BODY",
                    location,
                    op.getRequestBody(),
                    "Previously accepted request bodies may no longer satisfy the input contract.",
                    old,
                    next,
                    waivers);
            category(
                    findings,
                    "RESPONSE",
                    location,
                    op.getApiResponses(),
                    "Consumers may receive a response shape/status outside the previous guarantee.",
                    old,
                    next,
                    waivers);
            category(
                    findings,
                    "SECURITY",
                    location,
                    op.getSecurityRequirements(),
                    "Existing callers may need different credentials or authorization requirements.",
                    old,
                    next,
                    waivers);
            if (op.isIncompatible() && findings.size() == count)
                add(
                        findings,
                        "OPERATION",
                        location,
                        "Engine reports another incompatible operation change; inspect both contracts.",
                        old,
                        next,
                        waivers);
        }
        int schemaIndex = 0;
        for (var schema : diff.getChangedSchemas()) {
            if (schema.isIncompatible())
                add(
                        findings,
                        "SCHEMA",
                        "schema-diff-" + schemaIndex,
                        "Engine reports an incompatible component schema change; review its request/response usage.",
                        old,
                        next,
                        waivers);
            schemaIndex++;
        }
        category(
                findings,
                "EXTENSION",
                "document",
                diff.getChangedExtensions(),
                "Engine reports an incompatible extension change.",
                old,
                next,
                waivers);
        // Do not accidentally accept an incompatibility outside the mapped operation categories.
        if (diff.isIncompatible() && findings.isEmpty())
            add(
                    findings,
                    "ENGINE_OTHER",
                    "document",
                    "Engine reports a schema/extension incompatibility outside mapped operation categories.",
                    old,
                    next,
                    waivers);
        Set<String> used = new HashSet<>();
        findings.stream().filter(Finding::waived).forEach(f -> used.add(f.fingerprint()));
        if (!used.containsAll(waivers.keySet()))
            throw new IllegalArgumentException("Unused or stale waiver; remove or review it");
        findings.sort(Comparator.comparing(Finding::location).thenComparing(Finding::rule));
        boolean denied = findings.stream().anyMatch(f -> !f.waived());
        return new Report(
                denied ? "BREAKING" : findings.isEmpty() ? "COMPATIBLE" : "WAIVED",
                denied ? 1 : 0,
                old.hash(),
                next.hash(),
                List.copyOf(findings),
                List.of(
                        "OpenAPI Diff 2.1.7 default compatibility rules; runtime/business semantics are not proven.",
                        "Fingerprints bind category/location and the exact canonical contract pair; unrelated edits conservatively invalidate waivers.",
                        "JSON OpenAPI 3.0.0–3.0.3 only; external refs and recursive schemas are rejected. Compatible does not mean unchanged."));
    }

    static void category(
            List<Finding> findings,
            String rule,
            String location,
            Changed changed,
            String impact,
            Document old,
            Document next,
            Map<String, Waiver> waivers)
            throws Exception {
        if (changed != null && changed.isIncompatible()) add(findings, rule, location, impact, old, next, waivers);
    }

    static void add(
            List<Finding> findings,
            String rule,
            String location,
            String impact,
            Document old,
            Document next,
            Map<String, Waiver> waivers)
            throws Exception {
        String fingerprint = hash("gate-v1\n" + rule + "\n" + location + "\n" + old.hash() + "\n" + next.hash());
        Waiver waiver = waivers.get(fingerprint);
        findings.add(new Finding(
                fingerprint,
                rule,
                location,
                impact,
                waiver != null,
                waiver == null ? "" : waiver.owner(),
                waiver == null ? "" : waiver.reason(),
                waiver == null ? "" : waiver.expires().toString(),
                "Before: " + excerpt(old.tree(), location) + "\nAfter: " + excerpt(next.tree(), location)));
    }

    static String excerpt(JsonNode tree, String location) {
        int separator = location.indexOf(' ');
        JsonNode source = separator > 0 ? tree.path("paths").path(location.substring(separator + 1)) : tree;
        String value = sorted(source).toString();
        return value.length() > 4096 ? value.substring(0, 4096) + " [TRUNCATED; inspect input contract]" : value;
    }

    static String safe(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("`", "&#96;")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    public static void write(Report report, Path directory) throws Exception {
        Files.createDirectories(directory);
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        StringBuilder md = new StringBuilder(
                "# API compatibility report\n\nStatus: **" + report.status() + "**; exit " + report.exitCode()
                        + ".\n\nOld SHA-256: " + report.oldSha256() + "\n\nNew SHA-256: " + report.newSha256() + "\n");
        for (Finding f : report.findings()) {
            md.append("\n## ")
                    .append(f.rule())
                    .append(" — ")
                    .append(safe(f.location()))
                    .append("\n\n")
                    .append(safe(f.consumerImpact()))
                    .append("\n\nFingerprint: ")
                    .append(f.fingerprint())
                    .append("\n\nDecision: ")
                    .append(f.waived() ? "WAIVED" : "BLOCKED")
                    .append("\n\n<pre>")
                    .append(safe(f.evidence()))
                    .append("</pre>\n");
            if (f.waived())
                md.append("\nOwner: ")
                        .append(safe(f.owner()))
                        .append("; expires: ")
                        .append(f.expires())
                        .append("; reason: ")
                        .append(safe(f.reason()))
                        .append("\n");
        }
        for (String warning : report.warnings()) md.append("\n- ").append(safe(warning));
        if (json.length() > 1_048_576 || md.length() > 1_048_576)
            throw new IllegalArgumentException("Report budget exceeded");
        Files.writeString(directory.resolve("report.json"), json);
        Files.writeString(directory.resolve("report.md"), md + "\n");
    }
}
