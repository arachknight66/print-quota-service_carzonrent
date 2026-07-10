# ADR-010: Isolation of the IPP Codec Library

## Context
Embedding binary Internet Printing Protocol (IPP) parsing directly into Spring Boot makes the code hard to test and reuse.

## Decision
We decouple the IPP binary decoding/encoding into a standalone Java library module (`ipp-codec`). It uses zero external framework dependencies.

## Consequences
- **Pros**: Standalone testing. Reusable library. Faster local compile times.
- **Cons**: Requires building multiple Maven artifacts.
