package cv.igrp.platform.access_management.page_access.domain.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Next.js page path matching (#34), as a table: request path → the pattern that serves it. */
class PagePathsTest {

    private static final List<String> PATTERNS = List.of(
            "/",
            "/settings",
            "/settings/users",
            "/settings/users/[id]",
            "/settings/users/new",
            "/(home)/profile",
            "/docs/[...slug]",
            "/shop/[[...filters]]",
            "/files/[folder]/[name]",
            "/catalog",
            "/catalog/[[...filters]]",
            "/@modal/inbox",
            "/x/[slug]",
            "/x/[id]");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(nullValues = "NONE", value = {
            "/,                              /",
            "/settings,                      /settings",
            "/settings/,                     /settings",
            "/settings?tab=1,                /settings",
            "/settings#top,                  /settings",
            "/settings/users,                /settings/users",
            "/settings/users/42,             /settings/users/[id]",
            "/settings/users/new,            /settings/users/new",
            "/profile,                       /(home)/profile",
            "/docs/a,                        /docs/[...slug]",
            "/docs/a/b/c,                    /docs/[...slug]",
            "/docs,                          NONE",
            "/shop,                          /shop/[[...filters]]",
            "/shop/red/xl,                   /shop/[[...filters]]",
            "/files/a/b,                     /files/[folder]/[name]",
            "/files/a,                       NONE",
            "/settings/users/42/edit,        NONE",
            "/unknown,                       NONE",
            "/Settings,                      NONE",
            "/catalog,                       /catalog",
            "/catalog/a,                     /catalog/[[...filters]]",
            "/inbox,                         /@modal/inbox",
            "/x/1,                           /x/[id]",
            "/docs/../settings/users,        NONE",
            "/docs/./a,                      NONE",
            "/settings//users,               NONE",
            "/docs/a%2Fb,                    NONE",
            "/docs/a%2fb,                    NONE",
            "/docs/%2e%2e,                   NONE",
            "/docs\\a,                       NONE",
            "settings,                       NONE"})
    void matches(String path, String expected) {
        assertThat(PagePaths.match(path, PATTERNS)).isEqualTo(Optional.ofNullable(expected));
    }

    @ParameterizedTest(name = "{0} valid={1}")
    @CsvSource({
            "/settings/users/[id],     true",
            "/docs/[...slug],          true",
            "/docs/[...slug]/edit,     false",
            "/shop/[[...f]]/x,         false",
            "settings,                 false"})
    void validity(String pattern, boolean valid) {
        assertThat(PagePaths.isValid(pattern)).isEqualTo(valid);
    }
}
