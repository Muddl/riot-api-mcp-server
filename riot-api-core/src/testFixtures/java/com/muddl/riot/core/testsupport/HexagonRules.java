package com.muddl.riot.core.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagon rules, shared by every module in the monorepo. These are not LoL-specific:
 * each module's architecture test declares them as {@code @ArchTest} fields and supplies its
 * own scan root via {@code @AnalyzeClasses}, so a new game server inherits the architecture
 * instead of copy-pasting it.
 * <p>
 * Cross-context rules are NOT here — they are per-module (a server's context graph is its own
 * business). See each module's architecture test for its slice rule.
 */
public final class HexagonRules {

    private static final String MCP_TOOL_ANNOTATION = "org.springframework.ai.mcp.annotation.McpTool";

    private HexagonRules() {}

    /** Inward-only layering: adapter -> application -> domain. */
    public static final ArchRule LAYERS_RESPECT_INWARD_DEPENDENCY_RULE = layeredArchitecture()
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

    /** RestClient only in outbound Riot adapters and the shared client factory. */
    public static final ArchRule RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS = noClasses()
            .that()
            .resideOutsideOfPackages("..adapter.out.riot..", "..core.http..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient");

    /** @McpTool methods only in inbound MCP adapters. */
    public static final ArchRule MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS = methods()
            .that()
            .areAnnotatedWith(MCP_TOOL_ANNOTATION)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..adapter.in.mcp..");

    /**
     * For library modules: no MCP tools at all. Each game server owns its own inbound adapters
     * so tool names can be namespaced per game without colliding when two servers are installed
     * into the same client.
     */
    public static final ArchRule NO_MCP_TOOLS_AT_ALL = noMethods().should().beAnnotatedWith(MCP_TOOL_ANNOTATION);

    /**
     * Everything in ..application.port.. is an interface. Checks the Port invariant from the
     * package side: it catches a concrete class dumped into the port package.
     * <p>
     * Deliberately kept alongside {@link #PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES}, which checks the
     * same invariant from the naming side. Neither subsumes the other — this one misses a {@code *Port}
     * declared outside the package, and that one misses a non-{@code *Port} class declared inside it.
     */
    public static final ArchRule PORTS_ARE_INTERFACES =
            classes().that().resideInAPackage("..application.port..").should().beInterfaces();

    /** Ports are interfaces named *Port residing in ..application.port.. Naming-side counterpart to
     * {@link #PORTS_ARE_INTERFACES}. */
    public static final ArchRule PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES = classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .resideInAPackage("..application.port..")
            .andShould()
            .beInterfaces();

    public static final ArchRule SERVICES_LIVE_IN_APPLICATION =
            classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage("..application..");

    public static final ArchRule TOOLS_LIVE_IN_INBOUND_ADAPTERS =
            classes().that().haveSimpleNameEndingWith("Tool").should().resideInAPackage("..adapter.in.mcp..");

    public static final ArchRule ADAPTERS_LIVE_IN_OUTBOUND_RIOT =
            classes().that().haveSimpleNameEndingWith("Adapter").should().resideInAPackage("..adapter.out.riot..");
}
