# ADR-006: Chain of Responsibility for Print Processing

## Context
Checking print request validity, resolving identity, extracting page metadata, and deducting quota are tightly coupled steps. Hardcoding these checks inside a single service method creates a "God class" that is hard to maintain, test, and extend.

## Decision
We implement a Chain of Responsibility pattern. We define a `PipelineStage` interface and register sequential stages (`ProtocolValidationStage`, `IdentityResolutionStage`, `MetadataExtractionStage`, `QuotaEvaluationStage`, `PrintTransactionStage`) ordered by Spring's `@Order` annotation.

## Consequences
- **Pros**: Clean code separation (SOLID). Stages are independently testable. Easier to introduce new stages (e.g. device checks or billing stages) without rewriting core services.
- **Cons**: Adds interface abstractions and context object creation overhead.
