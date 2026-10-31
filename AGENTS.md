# Agent Instructions

## Approval Before Implementation

- Before implementation, propose a brief solution and wait for the user's explicit approval.
- Do not change code, configuration, or database schemas before approval.
- If the proposed solution changes substantially during the work, get the user's approval again.

## Tests

- Add tests at the end of the work, after the user approves the implementation.
- Test business logic only: domain rules, invariants, and meaningful behavior.
- Do not add tests for input validation, configuration validation, data classes, DTOs, or straightforward data mapping.
- Do not remove existing tests without the user's approval.

## Scripts, Containers, and External Files

- Before running any script or starting, stopping, or changing containers, ask the user for approval for that specific action.
- You may read files in any directory explicitly listed as part of the current project's authorized workspace roots. Ask the user before reading files outside those directories.

## Language

- Keep all comments, documentation, and other text in the project in English.

## Dependencies

- Prefer the latest stable dependency versions. Use an appropriate LTS version when one is available.
- If the latest stable version conflicts with the current stack or other dependencies, ask the user before choosing a different version or keeping an older one.
