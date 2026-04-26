# WormMine Bio-Sources

WormMine Bio-Sources is a collection of InterMine data converters and configurations specifically designed for integrating WormBase data into an InterMine instance. It enables the ingestion of various biological data formats (AceDB XML, GFF3, OBO/OWL) into the WormMine data warehouse.

## Project Structure

The project is organized as a multi-module Gradle project, with each sub-module representing a distinct biological data source (bio-source):

- **wormbase-acedb**: The primary converter for AceDB XML data. It uses a custom mapping-based parser to translate complex AceDB objects into InterMine items. (Cleaned up in April 2026: removed legacy debug code and dead parser classes).
- **wormbase-gff**: Handles GFF3 formatted genomic data for WormBase.
- **anatomy_ontology**, **disease_ontology**, **phenotype_ontology**, **worm-development-ontology**: Process and load various ontologies into the InterMine model.

## Technical Stack

- **Language**: Java 8 (sourceCompatibility = 1.8)
- **Build System**: Gradle (InterMine version 5.0.6)
- **Framework**: InterMine Bio-core and Bio-model
- **Data Mapping**: XPath-based mapping in the AceDB converter

## Getting Started

### Prerequisites

- Java 8 JDK
- Gradle (provided via `./gradlew`)
- InterMine properties configured in `~/.intermine/intermine-bio-test.properties` (for tests)

### Build and Test Commands

- **Build everything**: `./gradlew build`
- **Run all tests**: `./gradlew test`
- **Generate test report**: `./gradlew testReport` (results in `build/reports/allTests`)
- **Run a specific test**: `./gradlew :<subproject>:test --tests "<fully.qualified.TestClassName>"`
- **Clean build**: `./gradlew clean`

## Development Conventions

### Data Model Extensions

InterMine models are extended by adding XML definitions to `src/main/resources/*_additions.xml` in the respective sub-project. These files define new classes, attributes, and references specific to WormBase data.

### Key Definitions

Key definitions for object merging and synchronization are located in `src/main/resources/*_keys.properties`.

### Testing

- Tests for converters should extend `ItemsTestCase` or use standard InterMine testing patterns.
- Mock data and expected XML output are typically stored in `src/test/resources`.
- Ensure all test classes end with `Test.java` to be automatically picked up by the Gradle test task.

### Coding Style

- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods/variables).
- Use Log4j for logging (available via InterMine dependencies).
- Avoid wildcard imports.

## Recent Model Improvements (feature/model-improvements branch)

- **Bidirectional Relationships**: Added reverse references to `OntologyTerm` (parents/children) to improve transitive closure queries.
- **Structural Standardization**: 
    - `AnatomyTerm`, `WBSOterm`, and `WBProcess` now correctly extend `OntologyTerm`.
    - Added `organism` reference to `BioEntity` for better multi-species support.
- **Enhanced Identification**: Added `secondaryIdentifier` to `Gene` and updated `keys.properties` to support multi-part keys for Genes and Proteins.
- **Model Enrichment**: Added reverse references for expression patterns and clusters to ensure full navigability in the Query Builder.

