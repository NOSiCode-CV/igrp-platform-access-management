package cv.igrp.platform.access_management.shared.application.constants;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v3-13 (#21): the scope values and how a key's scopes combine (OR, spec "Data scopes"). */
class PermissionScopeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "department                 | department",
            "own;by_role                | by_role,own",
            "by_role;own                | by_role,own",
            "own;own                    | own",
            "own;all                    | all",
            "all;department;own;by_role | all",
            "department;own;by_role     | by_role,department,own",
    })
    void unionIsSortedAndAllWins(String scopes, String expected) {
        List<PermissionScope> parsed = Arrays.stream(scopes.split(";")).map(String::trim)
                .map(PermissionScope::fromCode).toList();

        assertThat(PermissionScope.union(parsed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "own", "by_role", "department"})
    void codesRoundTrip(String code) {
        assertThat(PermissionScope.fromCode(code).code()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ALL", "everything", "by-role", " own"})
    void unknownCodesAreRefused(String code) {
        assertThatThrownBy(() -> PermissionScope.fromCode(code)).isInstanceOf(IllegalArgumentException.class);
    }
}
