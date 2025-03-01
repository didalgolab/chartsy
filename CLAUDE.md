# Chartsy Development Guidelines

## Build Commands
- Build project: `mvn clean install`
- Run application: `mvn nbm:cluster-app nbm:run-platform -f application/pom.xml`
- Run tests: `mvn test`
- Run single test: `mvn test -Dtest=ClassName#methodName`
- Run with code coverage: `mvn test -Pcoverage`

## Code Style Guidelines
- Java version: Java 21
- Standard naming: CamelCase for classes, lowerCamelCase for methods and variables
- Interface methods: Use Java interface defaults where appropriate for common implementations
- Documentation: All public APIs should have Javadoc with @author and parameter descriptions
- Error handling: Use checked exceptions for recoverable errors, unchecked for programming errors
- Testing: JUnit 5 with descriptive method names like `methodName_scenario_gives_expected_behavior()`
- Imports: Avoid wildcard imports except for static imports of test assertions
- Typing: Prefer interfaces over concrete implementations in method signatures
- Functional style: Use Java functional interfaces (Function, Supplier, etc.) when appropriate

## Architecture Principles
- Clean separation of UI and business logic
- Interface-based design with dependency injection
- Use of immutable objects where possible
- Time handling with nanosecond precision using Chronological interface