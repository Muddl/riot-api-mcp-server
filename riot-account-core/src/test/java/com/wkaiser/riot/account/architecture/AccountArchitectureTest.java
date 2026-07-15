package com.wkaiser.riot.account.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wkaiser.riot.core.testsupport.HexagonRules;

/**
 * Architecture rules for the shared account library. The headline rule is
 * {@code no_mcp_tools_in_this_library}: account-v1 is cross-game, so exposing a tool here would
 * put an identically-named tool in every installed game server and collide inside the client.
 * Each server owns its own inbound adapter instead.
 *
 * <p>{@link ImportOption.DoNotIncludeGradleTestFixtures} is required alongside {@link
 * ImportOption.DoNotIncludeTests}: this module's own {@code testFixtures} source set (e.g. {@code
 * InMemoryRiotAccountPort}, a class named {@code *Port} living outside {@code ..application.port..})
 * lands on the test classpath from {@code build/classes/java/testFixtures}, which {@code
 * DoNotIncludeTests} alone does not filter out, and would otherwise trip the port-naming rule.
 */
@AnalyzeClasses(
        packages = "com.wkaiser.riot.account",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeGradleTestFixtures.class})
class AccountArchitectureTest {

    @ArchTest
    static final ArchRule no_mcp_tools_in_this_library = HexagonRules.NO_MCP_TOOLS_AT_ALL;

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE;

    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters =
            HexagonRules.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES;

    @ArchTest
    static final ArchRule services_live_in_application = HexagonRules.SERVICES_LIVE_IN_APPLICATION;

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT;
}
