package cv.igrp.platform.access_management.routes.domain.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** v3-02 (#10): which registration's version is the newer one. */
class ServiceVersionsTest {

    @ParameterizedTest(name = "{0} < {1}")
    @CsvSource({
            "1.4.0, 1.4.1",
            "1.9.0, 1.10.0",
            "1.4, 1.4.1",
            "1.4.0-beta.1, 1.4.0",
            "1.4.0-alpha, 1.4.0-beta",
            "0.9.9, 1.0.0",
            "2026.11.09, 2026.11.10",
            "abc, abd"})
    void older(String older, String newer) {
        assertThat(ServiceVersions.compare(older, newer)).isNegative();
        assertThat(ServiceVersions.compare(newer, older)).isPositive();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "1.4.0, 1.4.0",
            "1.4, 1.4.0",
            "1.4.0+build.7, 1.4.0",
            "' 1.4.0 ', 1.4.0"})
    void same(String a, String b) {
        assertThat(ServiceVersions.compare(a, b)).isZero();
    }
}
