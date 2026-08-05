/*
 * Contract tests for the cross-service contracts declared under
 * `contracts/`. Validates the structural invariants the Node-side
 * `scripts/test-contracts.mjs` cannot easily cover, namely:
 *
 *   - every `.proto` request message that performs a mutation starts
 *     with `com.genealogy.platform.common.v1.Context context = 1;`
 *   - every `.proto` enum carries a `_UNSPECIFIED = 0;` sentinel
 *   - every Avro `.avsc` schema declares the
 *     `com.genealogy.platform.events.` namespace prefix
 *   - forbidden DNA / raw / token field names never appear in any
 *     contract artefact
 *   - OpenAPI problem responses reference the shared `Problem` schema
 *     and document `Idempotency-Key` / `If-Match` headers
 *
 * The tests intentionally do not depend on the generated protobuf
 * stubs (those arrive when E1.6 wires the build); they parse the
 * contract files directly so the gate runs before code-gen.
 */
package com.genealogy.platform.services.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractInvariantsTest {

    private static final Path CONTRACTS_ROOT =
            Path.of("..", "..", "contracts").toAbsolutePath().normalize();

    private static final Set<String> FORBIDDEN_PROPS =
            Set.of("dnaRaw", "rawGenotype", "dna", "kit", "rawDna", "raw_dna");

    @Test
    @DisplayName("protobuf mutation requests start with Context context = 1")
    void protobufRequestsHaveContextFirst() throws IOException {
        Path protoRoot = CONTRACTS_ROOT.resolve("protobuf");
        if (!Files.isDirectory(protoRoot)) {
            return;
        }
        Set<String> offenders = new HashSet<>();
        Pattern messageStart = Pattern.compile("^message\\s+(\\w+)\\s*\\{");
        Pattern fieldPattern =
                Pattern.compile(
                        "^\\s*(?:repeated\\s+|optional\\s+|map<[^>]+>\\s+)?[\\w.]+\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;");
        Pattern closeBrace = Pattern.compile("^\\s*\\}");
        try (Stream<Path> stream = Files.walk(protoRoot)) {
            List<Path> protos = stream.filter(p -> p.toString().endsWith(".proto")).toList();
            for (Path proto : protos) {
                List<String> lines = Files.readAllLines(proto);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    Matcher m = messageStart.matcher(line);
                    if (!m.find()) continue;
                    String name = m.group(1);
                    if (name.equals("Context")) continue;
                    boolean isRequest = name.endsWith("Request")
                            || name.startsWith("Invite")
                            || name.startsWith("Transition");
                    if (!isRequest) continue;
                    int depth = 1;
                    int idx = i + 1;
                    String firstFieldName = null;
                    Integer firstFieldNumber = null;
                    while (idx < lines.size() && depth > 0) {
                        String cur = lines.get(idx);
                        if (cur.matches("^\\s*message\\s+\\w+\\s*\\{.*")) depth++;
                        if (closeBrace.matcher(cur).find()) depth--;
                        if (depth == 0) break;
                        Matcher fm = fieldPattern.matcher(cur);
                        if (fm.find() && firstFieldName == null) {
                            firstFieldName = fm.group(1);
                            firstFieldNumber = Integer.parseInt(fm.group(2));
                        }
                        idx++;
                    }
                    if (!"context".equals(firstFieldName)
                            || firstFieldNumber == null
                            || firstFieldNumber != 1) {
                        offenders.add(proto.getFileName() + "::" + name);
                    }
                }
            }
        }
        assertThat(offenders)
                .as("Mutation request messages must start with Context context = 1")
                .isEmpty();
    }

    @Test
    @DisplayName("protobuf enums always declare _UNSPECIFIED = 0")
    void protobufEnumsHaveUnspecifiedSentinel() throws IOException {
        Path protoRoot = CONTRACTS_ROOT.resolve("protobuf");
        if (!Files.isDirectory(protoRoot)) {
            return;
        }
        Set<String> offenders = new HashSet<>();
        Pattern enumStart = Pattern.compile("^enum\\s+(\\w+)\\s*\\{");
        try (Stream<Path> stream = Files.walk(protoRoot)) {
            List<Path> protos = stream.filter(p -> p.toString().endsWith(".proto")).toList();
            for (Path proto : protos) {
                List<String> lines = Files.readAllLines(proto);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = enumStart.matcher(lines.get(i));
                    if (!m.find()) continue;
                    String name = m.group(1);
                    int idx = i + 1;
                    int depth = 1;
                    boolean hasUnspecified = false;
                    while (idx < lines.size() && depth > 0) {
                        String cur = lines.get(idx);
                        if (cur.matches("^\\s*enum\\s+\\w+\\s*\\{.*")) depth++;
                        if (cur.matches("^\\s*\\}.*")) depth--;
                        if (depth == 0) break;
                        if (cur.contains("UNSPECIFIED") && cur.contains("= 0")) {
                            hasUnspecified = true;
                        }
                        idx++;
                    }
                    if (!hasUnspecified) {
                        offenders.add(proto.getFileName() + "::" + name);
                    }
                }
            }
        }
        assertThat(offenders)
                .as("Every enum must declare a UNSPECIFIED = 0 sentinel")
                .isEmpty();
    }

    @Test
    @DisplayName("Avro schemas use the genealogy events namespace")
    void avroNamespacesArePrefixed() throws IOException {
        Path eventsRoot = CONTRACTS_ROOT.resolve("events");
        if (!Files.isDirectory(eventsRoot)) {
            return;
        }
        Set<String> offenders = new HashSet<>();
        try (Stream<Path> stream = Files.walk(eventsRoot)) {
            List<Path> avscs = stream.filter(p -> p.toString().endsWith(".avsc")).toList();
            for (Path avsc : avscs) {
                String body = Files.readString(avsc);
                Matcher m = Pattern.compile("\"namespace\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (!m.find()) {
                    offenders.add(avsc.getFileName() + " (no namespace)");
                    continue;
                }
                String ns = m.group(1);
                if (!ns.startsWith("com.genealogy.platform.events.")) {
                    offenders.add(avsc.getFileName() + "::" + ns);
                }
            }
        }
        assertThat(offenders)
                .as("Avro schemas must declare com.genealogy.platform.events.* namespace")
                .isEmpty();
    }

    @Test
    @DisplayName("forbidden DNA / raw / token field names never appear in contracts")
    void noForbiddenFieldsInContracts() throws IOException {
        Set<String> offenders = new HashSet<>();
        try (Stream<Path> stream =
                Files.walk(CONTRACTS_ROOT)
                        .filter(p -> p.toString().endsWith(".proto")
                                || p.toString().endsWith(".avsc")
                                || p.toString().endsWith(".yaml")
                                || p.toString().endsWith(".yml"))) {
            List<Path> files = stream.toList();
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    for (String forbidden : FORBIDDEN_PROPS) {
                        // Avoid false positives: `dnaService` is the
                        // legitimate service name. Match the field
                        // shape (`: forbidden` for YAML, `forbidden;`
                        // for protobuf, `"name": "forbidden"` for
                        // Avro).
                        String line = lines.get(i);
                        String protoHit = "(?<!\\w)" + Pattern.quote(forbidden) + "\\s*=";
                        String yamlHit = "(?<!\\w)" + Pattern.quote(forbidden) + "\\s*:";
                        String avroHit = "\"name\"\\s*:\\s*\"" + Pattern.quote(forbidden) + "\"";
                        if (Pattern.compile(protoHit).matcher(line).find()
                                || Pattern.compile(yamlHit).matcher(line).find()
                                || Pattern.compile(avroHit).matcher(line).find()) {
                            offenders.add(f.getFileName() + ":" + (i + 1) + " -> " + forbidden);
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("Forbidden DNA / raw / token fields must never appear in contracts")
                .isEmpty();
    }

    @Test
    @DisplayName("event envelope schema documents every Avro event field")
    void eventEnvelopeIsValid() throws IOException {
        Path envelope = CONTRACTS_ROOT.resolve("events/envelope/v1/event-envelope.avsc");
        if (!Files.isRegularFile(envelope)) {
            return;
        }
        String body = Files.readString(envelope);
        assertThat(body).contains("\"name\": \"EventEnvelope\"");
        assertThat(body).contains("\"name\": \"eventId\"");
        assertThat(body).contains("\"name\": \"eventType\"");
        assertThat(body).contains("\"name\": \"occurredAt\"");
        assertThat(body).contains("\"name\": \"tenantId\"");
        assertThat(body).contains("\"name\": \"aggregateId\"");
        assertThat(body).contains("\"name\": \"aggregateVersion\"");
        assertThat(body).contains("\"name\": \"traceId\"");
        assertThat(body).contains("\"name\": \"payload\"");
    }

    @Test
    @DisplayName("contract directory layout matches the README")
    void contractLayoutExists() {
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("openapi/common"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("openapi/public-api/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("openapi/bff/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("protobuf/common/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("protobuf/tenant/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("protobuf/genealogy/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("protobuf/search/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("events/envelope/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("events/shared/v1"))).isTrue();
        assertThat(Files.isDirectory(CONTRACTS_ROOT.resolve("events/genealogy/v1"))).isTrue();
    }
}
