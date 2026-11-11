package cv.igrp.platform.access_management.routes.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** v3-02 (#10): the key rules AM re-checks on every registration. */
class RouteKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "faturacao-api.faturas.list",
            "faturacao-api.faturas.linhas.patch",
            "faturacao-api.root.list",
            "faturacao-api.faturas.aprovar.update-all",
            "faturacao-api.faturas.options"})
    void validKeys(String key) {
        assertThat(RouteKeys.problem("faturacao-api", key)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "other-api.faturas.list           | does not start with the service name",
            "faturacao-api.list               | needs a resource and an action",
            "faturacao-api.faturas.approve    | not one of",
            "faturacao-api..list              | empty or invalid segment",
            "faturacao-api.fat uras.list      | empty or invalid segment",
            "faturacao-apix.faturas.list      | does not start with the service name"})
    void invalidKeys(String key, String reason) {
        assertThat(RouteKeys.problem("faturacao-api", key)).contains(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "with.dot", "with space", "igrp", "IGRP"})
    void invalidServiceNames(String service) {
        assertThat(RouteKeys.isValidService(service)).isFalse();
    }

    @Test
    void aMissingOrOverlongKeyIsRejected() {
        assertThat(RouteKeys.problem("svc", null)).contains("no key");
        assertThat(RouteKeys.problem("svc", "svc." + "a".repeat(250) + ".list")).contains("longer than");
        assertThat(RouteKeys.isValidService("faturacao-api")).isTrue();
    }

    @Test
    void namesInTheLegacyNamespaceAreRejected() {
        assertThat(RouteKeys.nameProblem("igrp.users.list")).contains("reserved");
        assertThat(RouteKeys.nameProblem("IGRP.Users")).contains("reserved");
        assertThat(RouteKeys.nameProblem("Aprovar fatura")).isNull();
        assertThat(RouteKeys.nameProblem(null)).isNull();
    }
}
