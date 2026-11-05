package cv.igrp.platform.access_management.shared.i18n;

import cv.igrp.platform.access_management.security_audit.application.export.ReportColumnDefinition;
import cv.igrp.platform.access_management.security_audit.application.export.ReportColumns;
import cv.igrp.platform.access_management.security_audit.application.export.ReportDescriptor;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * BE-15 (FR-5, FR-35) — static consistency of the API message bundles. Runs with
 * the ordinary {@code mvn test}; no CI change (NFR-2).
 *
 * <ul>
 *   <li>every {@link IgrpErrorCode} has {@code error.<CODE>} in {@code messages.properties},
 *       with the same text as the enum template (the enum is the seed + last-resort fallback);</li>
 *   <li>every key used by code ({@code {validation.*}} annotations, {@code error.handler.*},
 *       {@code report.*}) exists in the base bundle;</li>
 *   <li>{@code messages_pt} / {@code messages_fr} have no orphan keys and the same
 *       {@code {n}} placeholders as the base;</li>
 *   <li>every file is valid UTF-8 and every value is a valid MessageFormat pattern
 *       with no stray single quote;</li>
 *   <li>per-locale coverage is printed (informational only).</li>
 * </ul>
 */
class MessageBundleConsistencyTest {

    private static final String BASE = "i18n/messages.properties";
    private static final List<String> TRANSLATIONS = List.of("i18n/messages_pt.properties", "i18n/messages_fr.properties");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\s*(\\d+)\\s*(?:,[^}]*)?}");
    private static final Pattern VALIDATION_KEY_IN_SOURCE = Pattern.compile("\"\\{(validation\\.[A-Za-z0-9_.]+)}\"");
    private static final Pattern MESSAGE_KEY_IN_SOURCE = Pattern.compile("messages\\.get(?:OrDefault)?\\(\"([a-zA-Z0-9_.]+)\"");

    private static Map<String, String> base;
    private static final Map<String, Map<String, String>> translations = new LinkedHashMap<>();

    @BeforeAll
    static void load() throws IOException {
        base = load(BASE);
        for (String file : TRANSLATIONS) {
            translations.put(file, load(file));
        }
    }

    private static Map<String, String> load(String resource) throws IOException {
        byte[] bytes;
        try (InputStream in = MessageBundleConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("bundle %s on the classpath", resource).isNotNull();
            bytes = in.readAllBytes();
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new AssertionError(resource + " is not valid UTF-8", ex);
        }
        Properties props = new Properties();
        props.load(new StringReader(text));
        Map<String, String> map = new LinkedHashMap<>();
        props.stringPropertyNames().stream().sorted().forEach(k -> map.put(k, props.getProperty(k)));
        return map;
    }

    @Test
    void everyErrorCodeHasABaseMessageMatchingTheEnumTemplate() {
        List<String> problems = new ArrayList<>();
        for (IgrpErrorCode code : IgrpErrorCode.values()) {
            String value = base.get(code.messageKey());
            if (value == null) {
                problems.add("missing " + code.messageKey());
            } else if (!value.equals(code.getMessageTemplate())) {
                problems.add(code.messageKey() + " differs from the enum template:\n  bundle: " + value
                        + "\n  enum:   " + code.getMessageTemplate());
            }
        }
        assertThat(problems).as("error.<CODE> keys in %s", BASE).isEmpty();
    }

    @Test
    void translationsHaveNoOrphanKeys() {
        translations.forEach((file, messages) -> {
            Set<String> orphans = new TreeSet<>(messages.keySet());
            orphans.removeAll(base.keySet());
            assertThat(orphans).as("keys in %s that do not exist in %s", file, BASE).isEmpty();
        });
    }

    @Test
    void placeholdersMatchTheBase() {
        List<String> problems = new ArrayList<>();
        translations.forEach((file, messages) -> messages.forEach((key, value) -> {
            if (!base.containsKey(key)) {
                return;
            }
            Set<String> expected = placeholders(base.get(key));
            Set<String> actual = placeholders(value);
            if (!expected.equals(actual)) {
                problems.add(file + " " + key + ": expected " + expected + " but was " + actual);
            }
        }));
        assertThat(problems).isEmpty();
    }

    @Test
    void everyValueIsAValidMessageFormatWithoutStrayQuotes() {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        all.put(BASE, base);
        all.putAll(translations);
        all.forEach((file, messages) -> messages.forEach((key, value) -> {
            try {
                new MessageFormat(value);
            } catch (IllegalArgumentException ex) {
                problems.add(file + " " + key + ": invalid MessageFormat (" + ex.getMessage() + ")");
            }
            // spring.messages.always-use-message-format=true: a single ' starts a quoted
            // section and silently swallows text. Literal quotes must be written as ''.
            if (value.replace("''", "").contains("'")) {
                problems.add(file + " " + key + ": unescaped single quote (write '' instead)");
            }
        }));
        assertThat(problems).isEmpty();
    }

    @Test
    void keysUsedByCodeExistInTheBaseBundle() throws IOException {
        Set<String> used = new TreeSet<>();
        Path sources = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                collect(VALIDATION_KEY_IN_SOURCE.matcher(source), used);
                collect(MESSAGE_KEY_IN_SOURCE.matcher(source), used);
            }
        }
        for (ReportDescriptor<?> descriptor : List.of(ReportColumns.AUDIT, ReportColumns.ACCESS, ReportColumns.SETTINGS)) {
            used.add(descriptor.titleMessageKey());
            for (ReportColumnDefinition<?> column : descriptor.columns()) {
                assertThat(column.labelKey()).as("column %s has an i18n key", column.label()).isNotNull();
                used.add(column.labelKey());
            }
        }
        used.add("report.generatedOn");

        assertThat(used).as("keys referenced by code").contains("validation.email.required", "error.handler.validationErrors");
        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(base.keySet());
        assertThat(missing).as("keys referenced by code but missing from %s", BASE).isEmpty();
    }

    @Test
    void baseBundleIsTheOnlyEnglishFile() {
        assertThat(getClass().getClassLoader().getResource("i18n/messages_en.properties"))
                .as("en is the module default: it lives in messages.properties, not messages_en.properties")
                .isNull();
    }

    @Test
    void printCoverage() {
        StringBuilder report = new StringBuilder("i18n coverage (informational) — base en: ")
                .append(base.size()).append(" keys");
        translations.forEach((file, messages) -> {
            long shared = messages.keySet().stream().filter(base::containsKey).count();
            String lang = file.substring(file.lastIndexOf('_') + 1, file.lastIndexOf('.'));
            report.append(String.format("%n  %s: %d/%d (%.0f%%)", lang, shared, base.size(), 100.0 * shared / base.size()));
        });
        System.out.println(report);
        if (base.isEmpty()) {
            fail("empty base bundle");
        }
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            result.add("{" + m.group(1) + "}");
        }
        return result;
    }
}
