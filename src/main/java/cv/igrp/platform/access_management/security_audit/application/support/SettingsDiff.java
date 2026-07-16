package cv.igrp.platform.access_management.security_audit.application.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates the changed fields of an {@code EDIT} settings action into a pair of
 * compact strings suitable for the {@code previous_value} / {@code new_value}
 * columns of the Settings Report (see {@code requirements.md} §1.5.3, and the
 * {@code plan.md} §"previousValue / newValue capture" note).
 *
 * <p>Only fields whose value actually changed are recorded. Each side is rendered
 * as {@code field=value; field=value}; e.g. for a rename plus a status flip:
 * <pre>
 *   previousValue() → "name=Old; status=ACTIVE"
 *   newValue()      → "name=New; status=INACTIVE"
 * </pre>
 * When nothing changed both accessors return {@code null} so the event carries no
 * spurious diff.
 */
public final class SettingsDiff {

    private final List<String> previous = new ArrayList<>();
    private final List<String> current = new ArrayList<>();

    /**
     * Records {@code field} if {@code oldValue} and {@code newValue} differ.
     *
     * @return {@code this} for chaining.
     */
    public SettingsDiff compare(String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            previous.add(field + "=" + stringify(oldValue));
            current.add(field + "=" + stringify(newValue));
        }
        return this;
    }

    public boolean isEmpty() {
        return previous.isEmpty();
    }

    /** Old side of the diff, or {@code null} when nothing changed. */
    public String previousValue() {
        return isEmpty() ? null : String.join("; ", previous);
    }

    /** New side of the diff, or {@code null} when nothing changed. */
    public String newValue() {
        return isEmpty() ? null : String.join("; ", current);
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }
}
