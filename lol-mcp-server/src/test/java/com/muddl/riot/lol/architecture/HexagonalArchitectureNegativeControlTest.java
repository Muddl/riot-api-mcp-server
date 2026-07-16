package com.muddl.riot.lol.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves the package-string-dependent rules in {@link HexagonalArchitectureTest} actually bite.
 *
 * <p>A green build is not evidence for these rules, because the failure mode <em>is</em> a green
 * build: {@code only_analytics_and_the_account_tool_use_the_account_library} carries its package in
 * the rule's <em>condition</em>, so if that package moves the condition matches nothing, zero
 * violations are found, and the rule passes while guarding nothing. That is exactly how the
 * prohibition this rule replaced was silently retired once already (see the rule's javadoc).
 *
 * <p>This test is inverted on purpose: it asserts the rule FAILS when fed a violation. If it ever
 * goes green by <em>not</em> throwing, the rule has stopped enforcing anything. That also covers the
 * condition's matcher specifically — point it at a package that does not exist and this test stops
 * throwing, which is the whole failure being guarded against.
 */
class HexagonalArchitectureNegativeControlTest {

    @Test
    void account_library_rule_rejects_a_non_allowlisted_context() {
        JavaClasses violating = new ClassFileImporter().importClasses(ArchFixtureIllegalAccountUser.class);

        assertThatThrownBy(() ->
                        HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_library.check(
                                violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ArchFixtureIllegalAccountUser");
    }
}
