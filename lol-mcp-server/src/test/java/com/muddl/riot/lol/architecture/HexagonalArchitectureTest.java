package com.muddl.riot.lol.architecture;

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
        packages = "com.muddl.riot.lol",
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
     * Contexts are independent except for two deliberate composition edges. This replaces the
     * hand-maintained N-by-N matrix that preceded it: one rule that stays correct as contexts are
     * added, rather than one rule per context each enumerating every other.
     * <p>
     * The edges are analytics -> summoner and analytics -> match: analytics composes those two
     * contexts' application services. The spectator -> summoner edge was retired in Plan C, which
     * moved spectator to Spectator-V5 (PUUID-keyed) and dropped its by-name tools — removing
     * LiveGameTool's dependency on SummonerService.
     * <p>
     * analytics -> account needs no exception here: RiotAccountService lives in
     * com.muddl.riot.account (riot-account-core), outside this matcher. That same fact is why
     * {@link #only_analytics_and_the_account_tool_use_the_account_domain} exists — see below.
     */
    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other = slices().matching("..riot.lol.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.summoner.."))
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.match.."));

    /**
     * Only analytics (which composes it) and this server's thin account tool may reach into the
     * shared account <em>domain</em>. Identity resolution is deliberately excluded from this
     * confinement — see the {@code identity} carve-out below.
     * <p>
     * Before the monorepo split, the account context lived under this server's package root, so the
     * cross-context matrix forbade summoner/match/spectator from touching it. Extracting it to the
     * account library moved it outside {@link #contexts_do_not_depend_on_each_other}'s matcher,
     * which silently retired those three prohibitions — nothing violated them, so nothing failed.
     * This restores the guarantee.
     * <p>
     * The condition confines {@code ..riot.account..} but excludes {@code ..riot.account.identity..}:
     * {@code PlayerIdentityResolver} is the one part of the account library every player-keyed
     * context is <em>supposed</em> to depend on (ADR-0008). It returns a plain PUUID string, not a
     * {@code RiotAccount}, so opening it does not open the account domain through its return type.
     * Widening the allowlist instead would have thrown the domain guarantee away.
     * <p>
     * Matchers here are deliberately relative ({@code ..riot.account..}, not a fully-qualified
     * name). This rule's package sits in its <em>condition</em>, not its selector, so a
     * fully-qualified name would make the rule pass vacuously the moment the group changed — the
     * same silent-retirement failure it exists to prevent. {@link
     * HexagonalArchitectureNegativeControlTest} proves both halves still bite: the domain stays
     * forbidden, the resolver stays allowed.
     * <p>
     * riot-account-core is a domain context, not infrastructure (that distinction is why it is its
     * own module rather than part of riot-api-core), so "any module may consume it" is not the
     * intent. Stated as deny-by-default: a context added later is forbidden from the domain until
     * listed here.
     */
    @ArchTest
    static final ArchRule only_analytics_and_the_account_tool_use_the_account_domain = noClasses()
            .that()
            .resideOutsideOfPackages("..lol.analytics..", "..lol.account..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage("..riot.account..").and(resideOutsideOfPackages("..riot.account.identity..")))
            .as("only analytics and the account tool use the account domain "
                    + "(identity resolution is open to every context)");
}
