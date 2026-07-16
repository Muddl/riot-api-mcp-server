package com.muddl.riot.core.architecture;

import com.muddl.riot.core.testsupport.HexagonRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules for the shared infrastructure library.
 *
 * <p>Before the monorepo split, one scan root covered {@code com.muddl.riotapimcpserver}, so the
 * naming rules governed what is now this module. Extracting it to its own Gradle module moved it
 * outside every remaining scan root and silently left it ungoverned — no rule was deleted, but
 * nothing was checking here any more, and no test failed to say so. This restores that coverage.
 *
 * <p>The rules are deliberately a subset. This library is infrastructure, not a hexagon: it has no
 * {@code domain}/{@code application}/{@code adapter} layering, so the layering and port rules have
 * nothing to bind to. What it must not acquire is a service, a tool, or an adapter — the moment it
 * does, it has started holding domain, which is the shared-kernel junk-drawer failure this module's
 * existence is meant to prevent. {@link #no_mcp_tools_in_this_library} states that from the tool
 * side: game servers own their inbound adapters so tool names can be namespaced per game.
 *
 * <p>{@link ImportOption.DoNotIncludeGradleTestFixtures} accompanies {@link
 * ImportOption.DoNotIncludeTests} because this module's own test fixtures are compiled to {@code
 * build/classes/java/testFixtures}, which the latter's pattern does not match.
 *
 * <p><b>On {@code allowEmptyShould(true)}:</b> ArchUnit fails a {@code classes().that(...).should(...)}
 * rule whose {@code that(...)} filter matches nothing, on the sound assumption that a rule governing
 * zero classes is usually a mistake. Here it is the point. This module contains no {@code *Service},
 * {@code *Tool}, {@code *Adapter}, or {@code *Port} — and must not acquire one. These rules are guards
 * against a class that should never exist, so "matches nothing" is success, not a vacuous rule.
 * Verified by planting a {@code ProbeService} in this module: the rule fails as intended. The opt-in is
 * applied here at the use site, never in {@link HexagonRules}, so the same constants stay
 * empty-intolerant in {@code lol-mcp-server}, where they do have subjects to govern.
 */
@AnalyzeClasses(
        packages = "com.muddl.riot.core",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeGradleTestFixtures.class})
class RiotApiCoreArchitectureTest {

    /** Applies to all methods, so it is never empty and needs no opt-in. */
    @ArchTest
    static final ArchRule no_mcp_tools_in_this_library = HexagonRules.NO_MCP_TOOLS_AT_ALL;

    @ArchTest
    static final ArchRule services_live_in_application =
            HexagonRules.SERVICES_LIVE_IN_APPLICATION.allowEmptyShould(true);

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters =
            HexagonRules.TOOLS_LIVE_IN_INBOUND_ADAPTERS.allowEmptyShould(true);

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot =
            HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT.allowEmptyShould(true);

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES.allowEmptyShould(true);
}
