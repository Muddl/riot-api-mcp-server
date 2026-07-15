package com.wkaiser.riotapimcpserver.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the bounded-context hexagon defined in Decision 1 (see ARCHITECTURE.md). Only production
 * classes are analyzed ({@link ImportOption.DoNotIncludeTests}); the architecture suite itself and all
 * other test scaffolding are excluded from analysis.
 */
@AnalyzeClasses(packages = "com.wkaiser.riotapimcpserver", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "com.wkaiser.riotapimcpserver.";
    private static final String ACCOUNT = ROOT + "account..";
    private static final String SUMMONER = ROOT + "summoner..";
    private static final String MATCH = ROOT + "match..";
    private static final String SPECTATOR = ROOT + "spectator..";
    private static final String ANALYTICS = ROOT + "analytics..";

    // --- Rule 1: inward-only layering (adapter -> application -> domain). ---
    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Application")
            .definedBy("..application..")
            .layer("Adapter")
            .definedBy("..adapter..")
            .whereLayer("Adapter")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Adapter");

    // --- Rule 2: RestClient only in outbound Riot adapters and the shared client factory. ---
    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters_and_shared_http = noClasses()
            .that()
            .resideOutsideOfPackages("..adapter.out.riot..", "..shared.http..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient");

    // --- Rule 3: @McpTool methods only in inbound MCP adapters. ---
    @ArchTest
    static final ArchRule mcp_tools_only_in_inbound_adapters = methods()
            .that()
            .areAnnotatedWith("org.springframework.ai.mcp.annotation.McpTool")
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..adapter.in.mcp..");

    // --- Rule 4: ports are interfaces residing in ..application.port.. ---
    @ArchTest
    static final ArchRule ports_are_interfaces =
            classes().that().resideInAPackage("..application.port..").should().beInterfaces();

    // --- Rule 5: cross-context composition is restricted (leaf providers stay independent). ---
    @ArchTest
    static final ArchRule account_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(ACCOUNT)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SUMMONER, MATCH, SPECTATOR, ANALYTICS);

    @ArchTest
    static final ArchRule summoner_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(SUMMONER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, MATCH, SPECTATOR, ANALYTICS);

    @ArchTest
    static final ArchRule match_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(MATCH)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, SUMMONER, SPECTATOR, ANALYTICS);

    // spectator composes summoner (LiveGameTool -> SummonerService); no other cross-context deps.
    @ArchTest
    static final ArchRule spectator_only_composes_summoner = noClasses()
            .that()
            .resideInAPackage(SPECTATOR)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, MATCH, ANALYTICS);

    // analytics composes account/summoner/match; it must never touch spectator.
    @ArchTest
    static final ArchRule analytics_does_not_depend_on_spectator = noClasses()
            .that()
            .resideInAPackage(ANALYTICS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SPECTATOR);

    // --- Rule 6: naming conventions bind a type to its layer. ---
    @ArchTest
    static final ArchRule services_live_in_application =
            classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters =
            classes().that().haveSimpleNameEndingWith("Tool").should().resideInAPackage("..adapter.in.mcp..");

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot =
            classes().that().haveSimpleNameEndingWith("Adapter").should().resideInAPackage("..adapter.out.riot..");

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces = classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .resideInAPackage("..application.port..")
            .andShould()
            .beInterfaces();
}
