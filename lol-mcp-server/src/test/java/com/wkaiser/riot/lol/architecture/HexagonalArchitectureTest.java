package com.wkaiser.riot.lol.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wkaiser.riot.core.testsupport.HexagonRules;

/**
 * Enforces the bounded-context hexagon for the LoL server. The layering, placement, and naming
 * rules come from {@link HexagonRules} in riot-api-core's test fixtures — they are shared with
 * every other module. Only the cross-context rule is local, because a server's context graph is
 * its own business.
 *
 * <p>{@link ImportOption.DoNotIncludeGradleTestFixtures} is required alongside {@link
 * ImportOption.DoNotIncludeTests}: cross-module test doubles consumed via {@code testImplementation
 * testFixtures(...)} (e.g. account's {@code InMemoryRiotAccountPort}) land on this module's test
 * classpath from a dependency's {@code build/classes/java/testFixtures} output, which {@code
 * DoNotIncludeTests} alone does not filter out.
 */
@AnalyzeClasses(
        packages = "com.wkaiser.riot.lol",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeGradleTestFixtures.class})
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE;

    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters =
            HexagonRules.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule mcp_tools_only_in_inbound_adapters = HexagonRules.MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES;

    @ArchTest
    static final ArchRule services_live_in_application = HexagonRules.SERVICES_LIVE_IN_APPLICATION;

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters = HexagonRules.TOOLS_LIVE_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT;

    /**
     * Contexts are independent except for two deliberate composition edges. This replaces the
     * hand-maintained N-by-N matrix that preceded it: one rule that stays correct as contexts are
     * added, rather than one rule per context each enumerating every other.
     * <p>
     * analytics -> account needs no exception: RiotAccountService lives in com.wkaiser.riot.account
     * (riot-account-core), outside this matcher.
     */
    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other = slices().matching("com.wkaiser.riot.lol.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(resideInAPackage("..lol.spectator.."), resideInAPackage("..lol.summoner.."))
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.summoner.."))
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.match.."));
}
