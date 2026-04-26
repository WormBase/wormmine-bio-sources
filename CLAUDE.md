# WormMine Bio-Sources Development Guide

## Build/Test Commands
- Build all: `./gradlew build`
- Run all tests: `./gradlew test`
- Test report: `./gradlew testReport`
- Run single test: `./gradlew :subproject:test --tests "org.intermine.bio.dataconversion.TestClassName"`
- Clean: `./gradlew clean`

## Code Style Guidelines
- **Language**: Java 8 (sourceCompatibility = 1.8)
- **Naming**:
  - Classes: PascalCase (e.g., `WormbaseAcedbConverter`)
  - Methods/Variables: camelCase
  - Test classes must end with "Test.java"
- **Imports**: Standard Java ordering, avoid wildcards
- **Error handling**: Use appropriate logging with Log4j
- **Testing**: Tests extend ItemsTestCase for InterMine item testing

## Project Structure
- Each bio-source is a separate subproject with standard Maven structure
- Configuration in *_keys.properties and *_additions.xml files
- Converter classes handle data transformation to InterMine model
- Test resources contain sample data files and expected outputs

InterMine version: 5.0.6