package com.muddl.riot.tft.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.muddl.riot.core.testsupport.HexagonRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the bounded-context hexagon for the TFT server. Layering/placement/naming rules come from
 * {@link HexagonRules} (shared via riot-api-core test fixtures). Only the cross-context rule and the
 * account-domain confinement are local, because a server's context graph is its own business.
 */
@AnalyzeClasses(
        packages = "com.muddl.riot.tft",
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
    static final ArchRule ports_are_interfaces = HexagonRules.PORTS_ARE_INTERFACES;

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
     * Contexts are independent except for the two deliberate composition edges analytics -> summoner
     * and analytics -> match: analytics composes those two contexts' application services.
     */
    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other = slices().matching("..riot.tft.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(resideInAPackage("..tft.analytics.."), resideInAPackage("..tft.summoner.."))
            .ignoreDependency(resideInAPackage("..tft.analytics.."), resideInAPackage("..tft.match.."));

    /**
     * Only analytics (which composes it) and this server's thin account tool may reach into the
     * shared account <em>domain</em>. Identity resolution ({@code ..riot.account.identity..}) is
     * excluded from the confinement — every player-keyed context is supposed to depend on it
     * (ADR-0008); it returns a plain PUUID string, not a {@code RiotAccount}.
     *
     * <p>Unlike the LoL server, TFT's summoner, match, and league contexts also depend on {@code
     * PlayerIdentityResolver} directly (each resolves a PUUID before calling the Riot API). That is
     * legal under this rule as written: the identity carve-out lives in the rule's <em>condition</em>
     * (what account-domain classes are forbidden), not in the confinement's package selector (who is
     * exempt). So any context — allowlisted or not — may depend on {@code ..riot.account.identity..}
     * without tripping this rule; only a dependency on the rest of the account domain (e.g. {@code
     * RiotAccount}, {@code RiotAccountService}) from outside {@code tft.analytics}/{@code tft.account}
     * is forbidden.
     */
    @ArchTest
    static final ArchRule only_analytics_and_the_account_tool_use_the_account_domain = noClasses()
            .that()
            .resideOutsideOfPackages("..tft.analytics..", "..tft.account..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage("..riot.account..").and(resideOutsideOfPackages("..riot.account.identity..")))
            .as("only analytics and the account tool use the account domain "
                    + "(identity resolution is open to every context)");
}
