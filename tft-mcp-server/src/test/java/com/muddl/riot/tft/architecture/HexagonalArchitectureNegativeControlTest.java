package com.muddl.riot.tft.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Proves the split account rule in {@link HexagonalArchitectureTest} bites on both sides. */
class HexagonalArchitectureNegativeControlTest {

    @Test
    void account_domain_rule_rejects_a_non_allowlisted_context_using_the_domain() {
        JavaClasses violating = new ClassFileImporter().importClasses(ArchFixtureIllegalAccountUser.class);

        assertThatThrownBy(() ->
                        HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_domain.check(
                                violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ArchFixtureIllegalAccountUser");
    }

    @Test
    void account_domain_rule_allows_a_non_allowlisted_context_using_the_identity_resolver() {
        JavaClasses legal = new ClassFileImporter().importClasses(ArchFixtureLegalResolverUser.class);

        assertThatCode(() -> HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_domain.check(
                        legal))
                .doesNotThrowAnyException();
    }
}
