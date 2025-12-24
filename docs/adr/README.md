# Architecture Decision Records (ADR)

This directory contains Architecture Decision Records (ADRs) for the Roman Numeral Service.

## What is an ADR?

An Architecture Decision Record captures a significant architectural decision made along with its context and consequences. ADRs provide:

- **Transparency**: Document why decisions were made
- **Onboarding**: Help new team members understand the architecture
- **History**: Track the evolution of the system
- **Accountability**: Record the reasoning at decision time

## ADR Template

We follow the [Michael Nygard ADR template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):

```
# Title

## Status
[Proposed | Accepted | Deprecated | Superseded by ADR-XXX]

## Context
What is the issue that we're seeing that is motivating this decision?

## Decision
What is the change that we're proposing and/or doing?

## Consequences
What becomes easier or more difficult because of this decision?
```

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-001](001-precomputed-cache.md) | Use Pre-computed Cache for Roman Numeral Conversion | Accepted | 2024-12-24 |
| [ADR-002](002-virtual-threads.md) | Use Java 21 Virtual Threads for Parallel Processing | Accepted | 2024-12-24 |
| [ADR-003](003-plain-text-errors.md) | Use Plain Text Error Responses | Accepted | 2024-12-24 |
| [ADR-004](004-rate-limiting.md) | Use Bucket4j for Rate Limiting | Accepted | 2024-12-24 |
| [ADR-005](005-observability-stack.md) | Use Prometheus, Grafana, and Loki for Observability | Accepted | 2024-12-24 |
| [ADR-006](006-no-database.md) | No Database Required | Accepted | 2024-12-24 |

## How to Add a New ADR

1. Copy the template from `_template.md`
2. Name it `XXX-short-title.md` (e.g., `007-new-feature.md`)
3. Fill in all sections
4. Update this index
5. Submit PR for review

