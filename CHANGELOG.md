# Changelog

All notable changes to the Riot API MCP Server project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **Gradle Test Compilation**: Fixed `:compileTestJava` task failures across all DTO classes (2025-01-28)
  - Standardized Lombok annotations with `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` pattern
  - Resolved nested class builder conflicts in `Perks.java` static inner classes
  - Updated test helper methods to match actual DTO field structures
  - Added `CompilationVerificationTest.java` to prevent future regressions

### Added
- **FEATURES.md**: Comprehensive features documentation and roadmap (2025-01-28)
- **README.md**: Complete project documentation with quick start guide (2025-01-28)
- **LICENSE**: MIT License for open source usage (2025-01-28)

## [1.0.0] - 2025-01-20

### Added
- **Live Game State Monitor Tool** - Complete Riot Spectator API v4 integration
  - `LiveGameTool` with 4 MCP methods for real-time game monitoring
  - Complete DTO suite: `CurrentGameInfo`, `CurrentGameParticipant`, `BannedChampion`, `Observer`, `FeaturedGames`, `Perks`, `GameCustomizationObject`
  - `SpectatorService` with platform-specific RestClient and error handling
  - Comprehensive test suite with unit and integration tests
  - Support for all League of Legends platforms (NA1, EUW1, etc.)
  - Graceful handling of "summoner not in game" scenarios

- **Production Deployment Planning**
  - 745-line comprehensive production deployment roadmap in `PLAN.md`
  - 6-phase implementation strategy with multi-agent coordination
  - AWS infrastructure design (ECS Fargate, load balancing, auto-scaling)
  - Enterprise security (WAF, SSL, API key management with AWS Secrets Manager)
  - Performance optimization (Redis caching, rate limiting, monitoring)
  - Operational excellence (CI/CD pipelines, monitoring, alerting, disaster recovery)
  - Budget planning ($2,000-5,000/month operational budget with optimization strategies)

- **Claude Code Subagents Integration**
  - Collection of 83 specialized Claude Code subagents for domain-specific expertise
  - Core agents retained: `agent-organizer`, `java-pro`, `mcp-developer`, `test-automator`
  - Multi-agent orchestration patterns proven effective for complex feature development
  - Production team assembly with specialized agents for cloud architecture, security, performance

- **Enhanced Documentation**
  - Updated `CLAUDE.md` with live game monitoring and production planning sections
  - Multi-agent coordination examples and success patterns
  - Updated architecture documentation and file structure

### Changed
- **Agent Collection Optimization**
  - Removed unused subagent configurations to focus on core coordination
  - Streamlined to essential agents for proven multi-agent development workflows
  - Updated documentation to reflect production-ready development approach

## [0.3.0] - 2024-07-XX

### Added
- **GitHub Actions Workflows**
  - Claude Code Review workflow for automated code review on pull requests
  - Claude PR Assistant workflow for pull request management and assistance
  - Build and test automation with Gradle

### Fixed
- **Gradle Permissions**: Multiple fixes for GitHub Actions gradlew permissions
  - Resolved execute permissions for `gradlew` script in CI/CD environment

## [0.2.0] - 2024-07-XX

### Added
- **Core MCP Architecture**
  - `RiotAccountTool`: Account lookup by Riot ID or PUUID
  - `SummonerTool`: League of Legends summoner information
  - `AnalyticsTool`: Advanced analytics combining multiple API endpoints
  - Service layer structure with account, summoner, match, and analytics modules

### Changed
- **Code Quality Improvements**
  - Cleaned up initial implementation
  - Improved error handling and logging
  - Enhanced service integration patterns

## [0.1.0] - 2024-07-XX

### Added
- **Initial Project Setup**
  - Spring Boot 3.4.4 application with Java 21
  - Basic Riot API integration configuration
  - Model Context Protocol (MCP) server setup
  - Regional architecture with `RiotApiRegionUri` and `RiotApiPlatformUri` enums
  - RestClient configuration with automatic regional routing
  - Custom `RiotApiException` for API error handling
  - `GlobalExceptionHandler` for consistent error responses

- **Development Infrastructure**
  - Gradle build configuration with Spring AI MCP starter
  - Lombok integration for boilerplate reduction
  - JUnit 5 testing framework
  - Application configuration with `application.yml`

### Documentation
- Initial `CLAUDE.md` with project overview and development patterns
- Basic `PLAN.md` with initial project planning

---

## Legend

### Change Types
- **Added** for new features
- **Changed** for changes in existing functionality
- **Deprecated** for soon-to-be removed features
- **Removed** for now removed features
- **Fixed** for any bug fixes
- **Security** for vulnerability fixes

### Versioning Strategy
- **Major version** (x.0.0): Breaking changes, major feature additions
- **Minor version** (0.x.0): New features, backwards compatible
- **Patch version** (0.0.x): Bug fixes, backwards compatible

### Multi-Agent Development Notes
This project demonstrates successful multi-agent coordination patterns:
- **Complex Features**: Live Game Monitor implemented with java-pro, mcp-developer, test-automator coordination
- **Production Planning**: 8-12 specialized agents coordinated across infrastructure, security, performance, deployment
- **Agent Organization**: agent-organizer proven effective for team assembly and workflow optimization

For detailed feature information, see [FEATURES.md](FEATURES.md).
For production deployment details, see [PLAN.md](PLAN.md).
For development guidance, see [CLAUDE.md](CLAUDE.md).