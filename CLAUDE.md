# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Riot API MCP Server** - a Spring Boot application that serves as middleware between AI models and the Riot Games API. It exposes tools for retrieving and analyzing League of Legends game data through the Model Context Protocol (MCP), allowing AI models to access Riot API functionality via standardized tool calls.

The project now includes a comprehensive collection of 83 specialized Claude Code subagents that provide domain-specific expertise across software development, infrastructure, and business operations.

## Common Development Commands

### Build and Test
- `./gradlew build` - Build the project and run all tests
- `./gradlew test` - Run JUnit 5 tests only
- `./gradlew clean` - Clean build artifacts
- `./gradlew bootRun` - Run the Spring Boot application locally

### Development Notes
- Integration tests are disabled by default (`@Disabled`) as they require valid Riot API keys
- The application runs on Java 21 with Spring Boot 3.4.4
- Lombok is used throughout for reducing boilerplate code

## Architecture Overview

### Tool-Based MCP Architecture
The application exposes functionality to AI models through Spring AI's `@Tool` annotation pattern:

- **RiotAccountTool**: Account lookup by Riot ID or PUUID
- **SummonerTool**: League of Legends summoner information
- **AnalyticsTool**: Advanced analytics combining multiple API endpoints
- **LiveGameTool**: Real-time live game monitoring and spectator data

### Service Layer Structure
```
├── riot/
│   ├── account/ - Riot account management (cross-game)
│   ├── lol/
│   │   ├── summoner/ - LoL summoner data
│   │   ├── match/ - Match history and details
│   │   ├── analytics/ - Advanced analytics engine
│   │   └── spectator/ - Live game monitoring (NEW)
└── shared/ - Configuration, exceptions, enums
```

### Regional Architecture
- **Region Enums**: `RiotApiRegionUri` (AMERICAS, EUROPE, ASIA, SEA) for match/account data
- **Platform Enums**: `RiotApiPlatformUri` (NA1, EUW1, etc.) for summoner-specific data
- **RestClient Configuration**: Automatic regional routing with API key headers

### Analytics Engine
The `AnalyticsService` demonstrates the application's core value proposition:
1. Combines data from Account, Summoner, and Match APIs
2. Calculates comprehensive statistics (KDA, win rate, champion preferences)
3. Provides formatted, AI-friendly analytics responses
4. Handles edge cases (zero games, zero deaths for KDA)

### Claude Code Subagents Integration
The project leverages Claude Code subagents for coordinated development and production planning:
- **Core Agent Collection** with key agents like agent-organizer, java-pro, mcp-developer retained
- **Multi-Agent Orchestration** proven effective for complex feature development
- **Production Team Assembly** with specialized agents for cloud architecture, security, performance
- **Coordinated Workflows** demonstrating successful agent collaboration patterns

## Configuration

### Required Environment Setup
- **Riot API Key**: Set in `application.yml` under `riot.apiKey`
- **Region**: Configure default region in `riot.region`
- **Anthropic API Key**: Required for AI integration (already configured)

### Key Configuration Files
- `application.yml`: Main configuration including API keys and MCP server settings
- `build.gradle`: Dependencies including Spring AI MCP starter
- `RiotApiConfiguration.java`: RestClient setup with error handling
- `.claude/agents/`: Core subagent configurations for multi-agent coordination

## Development Patterns

### Tool Implementation Pattern
```java
@Tool(name = "tool_name", description = "Clear description for AI")
public ReturnType methodName(Parameters params) {
    log.info("MCP Tool - Action description");
    return service.performAction(params);
}
```

### Service Integration Pattern
Services orchestrate multiple API calls:
1. Account lookup (cross-game identifier)
2. Platform-specific data retrieval
3. Data aggregation and analysis
4. Formatted response generation

### Error Handling
- Custom `RiotApiException` for API errors
- `GlobalExceptionHandler` for consistent error responses
- HTTP status code preservation from Riot API

## Testing Strategy

- **Unit Tests**: Test individual service and tool methods
- **Integration Tests**: Disabled by default, require live API keys
- **Test Data**: Use placeholder Riot IDs in format "GameName#TAG"
- **Mocking**: Mock Riot API responses for reliable unit testing

## MCP Server Details

- **Type**: SYNC (synchronous request/response)
- **Endpoint**: `/mcp/messages` for SSE communication
- **Tools**: Auto-discovered via Spring AI's `@Tool` annotation scanning
- **Integration**: Designed for Claude and other AI models supporting MCP

## CI/CD & GitHub Integration

The project includes automated GitHub workflows:
- **Claude Code Review**: Automated code review workflow for pull requests
- **Claude PR Assistant**: Pull request management and assistance
- **Build & Test**: Automated testing with Gradle

## Multi-Agent Development Success

### Proven Agent Coordination
The project demonstrates successful multi-agent coordination for complex development tasks:

**Live Game Monitor Implementation Team:**
- **java-pro**: Lead development of DTOs, service layer, and Spring Boot integration
- **mcp-developer**: MCP tool implementation and AI model optimization
- **test-automator**: Comprehensive test suite creation and validation
- **agent-organizer**: Team coordination and workflow optimization

**Production Deployment Planning Team:**
- **cloud-architect**: AWS infrastructure design and scaling strategies
- **security-auditor**: Security implementation and compliance frameworks
- **performance-engineer**: Caching, optimization, and performance monitoring
- **deployment-engineer**: CI/CD pipelines and deployment automation

### Coordination Patterns
```
# Feature Development Workflow
Feature Request → agent-organizer → specialized team assembly → coordinated execution

# Complex Implementation Example
Live Game Monitoring:
Phase 1: java-pro (DTOs) → Phase 2: java-pro (Service) →
Phase 3: mcp-developer (Tools) → Phase 4: test-automator (Testing)

# Production Planning Example
Production Deployment:
6 Phases × 8-12 Agents → Infrastructure + Security + Performance + Monitoring
```

## Live Game State Monitor Tool

### New Feature: Riot Spectator API Integration
The project now includes a complete **Live Game State Monitor Tool** that provides real-time access to ongoing League of Legends matches through the Riot Spectator API v4.

#### Architecture
```
riot/lol/spectator/
├── dto/                     - Data Transfer Objects for live game data
│   ├── CurrentGameInfo.java        - Main live game information
│   ├── CurrentGameParticipant.java - Player data in live games
│   ├── BannedChampion.java         - Champion ban information
│   ├── Observer.java               - Spectator/observer data
│   ├── FeaturedGames.java          - Featured games list
│   ├── Perks.java                  - Rune/mastery information
│   └── GameCustomizationObject.java - Game customization data
├── service/
│   └── SpectatorService.java       - Service layer for Spectator API calls
└── tool/
    └── LiveGameTool.java           - MCP tools for AI model integration
```

#### MCP Tools Available
- `get_current_game_by_summoner_name` - Get live game by summoner name
- `get_current_game_by_summoner_id` - Get live game by encrypted summoner ID
- `get_featured_games` - Retrieve featured games for a platform
- `check_if_summoner_in_game` - Boolean check for game status

#### Key Features
- **Real-time Data**: Access live game information including participants, bans, and game metadata
- **Platform Support**: Works across all League of Legends platforms (NA1, EUW1, etc.)
- **Error Handling**: Graceful handling of "summoner not in game" scenarios (404 responses)
- **AI Integration**: Optimized for AI model consumption through standardized MCP tools

#### Data Limitations
The Spectator API provides game metadata and participant information but **does not include**:
- Real-time CS (Creep Score), KDA, or item builds
- Current gold amounts or live statistics
- Real-time positioning or objective states

## Production Deployment Planning

### New Addition: Comprehensive Production Deployment Plan
The project now includes a detailed **745-line production deployment plan** that provides a complete roadmap for taking the development-ready Riot API MCP Server to production-grade infrastructure.

#### Production Plan Highlights
- **6-Phase Implementation**: 12-16 week timeline with coordinated multi-agent execution
- **AWS Infrastructure**: ECS Fargate, load balancing, auto-scaling, and security layers
- **Enterprise Security**: WAF, SSL, API key management with AWS Secrets Manager
- **Performance Optimization**: Redis caching, rate limiting, and performance monitoring
- **Operational Excellence**: CI/CD pipelines, monitoring, alerting, and disaster recovery
- **Cost Management**: $2,000-5,000/month operational budget with optimization strategies

#### Production Architecture Components
```yaml
Target Infrastructure:
  - Container Orchestration: AWS ECS Fargate (2-20 instances)
  - Load Balancing: Application Load Balancer with SSL termination
  - Caching: Redis ElastiCache with intelligent TTL strategies
  - Security: WAF, IAM roles, encrypted API keys
  - Monitoring: Prometheus, Grafana, comprehensive alerting
  - CI/CD: GitHub Actions with blue-green deployments
```

## Recent Updates (Updated: 2025-01-20)

### Major Feature Addition: Live Game State Monitor Tool
- **Complete Spectator API Integration**: Added full Riot Spectator API v4 support with DTOs, service layer, and MCP tools
- **Multi-Agent Development**: Successfully implemented using coordinated team of specialized agents (java-pro, mcp-developer, test-automator)
- **Comprehensive Testing**: Full test suite including unit tests and integration test framework
- **Production Ready**: Includes proper error handling, authentication, and logging

### Production Deployment Planning Complete
- **Comprehensive Production Plan**: 745-line detailed deployment roadmap in PLAN.md
- **Multi-Agent Coordination**: 8-12 specialized agents coordinated across 6 phases
- **Enterprise Architecture**: AWS-based infrastructure with auto-scaling and security
- **Operational Excellence**: Complete monitoring, CI/CD, and disaster recovery procedures
- **Budget Planning**: Detailed cost analysis and optimization strategies

### Agent Collection Updates
- **Core Agents Retained**: Key agents like agent-organizer, java-pro, mcp-developer maintained
- **Agent Coordination Success**: Demonstrated effective multi-agent orchestration for complex projects
- **Production Team Identified**: Specific agents assigned for cloud architecture, security, performance, and deployment

### File Structure Changes
- **New Spectator Module**: Added complete `riot/lol/spectator/` package with DTOs, service, and tools
- **Test Coverage**: Added comprehensive test suite for all spectator components
- **Production Plan**: Completely updated PLAN.md with production deployment roadmap
- Enhanced CLAUDE.md with production planning and live game monitoring capabilities

### Development Workflow Enhancements
- **Live Game Monitoring**: AI models can now access real-time League of Legends match data
- **Production-Ready Development**: Clear path from development to production deployment
- **Multi-Agent Coordination**: Proven coordination strategies for complex feature development
- **Enhanced MCP Integration**: Additional tools for live game analysis and monitoring
- **Enterprise Deployment**: Complete infrastructure and operational procedures defined

## File Structure

```
├── .claude/
│   ├── agents/          - Claude Code subagents (core agents retained)
│   └── settings.local.json (local configuration)
├── .github/
│   └── workflows/       - GitHub automation workflows
├── src/
│   ├── main/java/
│   │   └── com/wkaiser/riotapimcpserver/
│   │       ├── riot/
│   │       │   ├── account/     - Riot account management (cross-game)
│   │       │   └── lol/
│   │       │       ├── summoner/    - LoL summoner data
│   │       │       ├── match/       - Match history and details
│   │       │       ├── analytics/   - Advanced analytics engine
│   │       │       └── spectator/   - **NEW: Live game monitoring**
│   │       │           ├── dto/     - Live game data structures (7 DTOs)
│   │       │           ├── service/ - SpectatorService API integration
│   │       │           └── tool/    - LiveGameTool MCP tools
│   │       └── shared/      - Configuration, exceptions, enums
│   └── test/java/
│       └── com/wkaiser/riotapimcpserver/
│           └── riot/lol/spectator/  - **NEW: Comprehensive test suite**
│               ├── service/     - SpectatorService unit & integration tests
│               └── tool/        - LiveGameTool unit & integration tests
├── CLAUDE.md           - Project documentation (this file)
├── PLAN.md             - **UPDATED: Production deployment roadmap**
└── build.gradle        - Build configuration
```