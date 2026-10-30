package cv.igrp.platform.access_management.shared.infrastructure.json;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The mapper for JSON whose exact bytes end up in the database: the M2M
 * structural hashes (which decide whether a sync is skipped), the custom-field
 * column, and the audit context.
 *
 * <p>Deliberately separate from the HTTP mapper. Its output must not move when
 * {@code IGRP_JACKSON2_DEFAULTS} changes the API's wire format, or every stored
 * hash would stop matching. Jackson 2 defaults, ISO dates. Pinned by
 * {@code PersistedJsonFormatTest}.
 */
public final class PersistedJson {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .configureForJackson2()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private PersistedJson() {
    }
}
