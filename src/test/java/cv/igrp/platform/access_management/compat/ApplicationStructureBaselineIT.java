package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 4 migration gate — the application's shape, independent of the
 * framework underneath it:
 *
 * <ul>
 *   <li><b>Beans.</b> Every bean whose class lives in {@code cv.igrp} (this
 *       service and the iGRP libraries). Retiring {@code @ComponentScan("cv.igrp")}
 *       in favour of auto-configuration must not lose one; new beans are
 *       allowed, missing ones fail.</li>
 *   <li><b>Routes.</b> Every path and HTTP method published in
 *       {@code /v3/api-docs}. The springdoc 2 → 3 upgrade may change the
 *       document's details, but not which operations exist.</li>
 * </ul>
 *
 * <p>Re-record (only for a deliberate, reviewed change):
 * {@code mvn verify -Dit.test=ApplicationStructureBaselineIT -Dcontract.baseline.record=true}
 */
class ApplicationStructureBaselineIT extends AbstractCompatIT {

    private static final Path BASELINE_DIR = Paths.get("src", "test", "resources", "compat-baseline");
    private static final boolean RECORD = Boolean.getBoolean("contract.baseline.record");

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    @DisplayName("No cv.igrp bean disappears")
    void igrpBeans() throws IOException {
        TreeSet<String> beans = new TreeSet<>();
        var factory = context.getBeanFactory();
        for (String name : factory.getBeanDefinitionNames()) {
            BeanDefinition definition = factory.getBeanDefinition(name);
            Class<?> type = factory.getType(name, false);
            String typeName = type != null ? type.getName() : String.valueOf(definition.getBeanClassName());
            if (typeName.startsWith("cv.igrp.") && !typeName.contains(".compat.")) {
                beans.add(name + " : " + typeName.replaceAll("\\$\\$.*$", ""));
            }
        }
        compareAsSuperset("beans.txt", beans);
    }

    @Test
    @DisplayName("Every published route still exists with the same HTTP methods")
    @SuppressWarnings("unchecked")
    void routes() throws IOException {
        CompatSupport api = new CompatSupport(context);
        HttpResponse<String> docs = api.get("/v3/api-docs", null);
        assertEquals(200, docs.statusCode(), docs.body());

        Map<String, Object> paths = (Map<String, Object>) CompatSupport.jsonObject(docs.body()).get("paths");
        TreeSet<String> routes = new TreeSet<>();
        paths.forEach((path, operations) -> ((Map<String, Object>) operations).keySet()
                .forEach(method -> routes.add(method.toUpperCase() + " " + path)));
        compareExactly("routes.txt", routes);
    }

    private void compareAsSuperset(String file, TreeSet<String> actual) throws IOException {
        List<String> expected = baseline(file, actual);
        List<String> missing = new ArrayList<>();
        for (String line : expected) {
            if (!actual.contains(line)) {
                missing.add(line);
            }
        }
        assertTrue(missing.isEmpty(), "beans missing compared with the baseline:\n" + String.join("\n", missing));
    }

    private void compareExactly(String file, TreeSet<String> actual) throws IOException {
        List<String> expected = baseline(file, actual);
        assertEquals(String.join("\n", expected), String.join("\n", actual), "route set changed");
    }

    private List<String> baseline(String file, TreeSet<String> actual) throws IOException {
        Path golden = BASELINE_DIR.resolve(file);
        if (RECORD) {
            Files.createDirectories(BASELINE_DIR);
            Files.writeString(golden, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(golden), "no baseline " + golden + "; record with -Dcontract.baseline.record=true");
        return Arrays.stream(Files.readString(golden, StandardCharsets.UTF_8).split("\n"))
                .filter(line -> !line.isBlank())
                .toList();
    }
}
