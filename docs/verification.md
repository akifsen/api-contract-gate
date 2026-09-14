# Local verification

| Claim | Evidence | Command | Local result |
|---|---|---|---|
| Real compatibility engine with direction-sensitive semantics | GateTest request/response fixtures | `./mvnw test` | Passed |
| Exact expiring waivers cannot cover later changes | GateTest fixed-clock policy | same | Passed |
| Invalid/missing/external/recursive refs never pass | GateTest parser and input bounds | same | Passed |
| Packaged CLI reports and exit codes | GateIT and saved CLI fixtures | `./mvnw verify -Pintegration`, README examples | 0/1/0/2/2 as documented |

2026-09-14, Windows / Temurin 21.0.12+8 / Maven 3.9.16. `mvnw.cmd -B -ntp spotless:apply verify -Pintegration` passed five real-engine unit tests and one packaged CLI integration test, zero skips. An independent source-only copy passed the same six tests without sibling source or Docker.

Packaged CLI examples were actually executed and their Markdown/JSON reports saved under `evidence`: compatible exit 0, breaking exit 1, invalid exit 2, exact active waiver exit 0, expired waiver exit 2. `evidence/exits.json` records the results. The breaking fixture adds a required request query parameter; reports include the concrete before/after excerpt. These are synthetic fixtures, not real PRs.

Tests also verify reversed response-required semantics, duplicate JSON keys, oversize input, internal refs, missing/remote refs and recursive-ref denial. GitHub CI and Linux execution remain unverified. No remote contract or live API was accessed by the gate.
