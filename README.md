# API Contract Gate

Repository: https://github.com/akifsen/api-contract-gate. Local verification results are documented below; hosted CI status must be checked in GitHub Actions.

A Java CLI wrapping the real OpenAPI Diff 2.1.7 engine with team policy, exact expiring waivers and consumer-facing Markdown/JSON reports. Adding a required request parameter blocks the build; a reviewed waiver can accept that exact change temporarily. Invalid contracts never become a compatibility pass.

## Run

Java 21 and Maven Wrapper 3.9.16; no Docker. The Spring Boot 4.1.1 BOM manages compatible transitive dependencies without a Boot runtime. Production uses OpenAPI Diff and its Swagger/Jackson parser dependencies; tests use JUnit 6.1.3.

```powershell
.\mvnw.cmd -B verify -Pintegration
java -Xmx192m -jar target/api-contract-gate-0.1.0-SNAPSHOT.jar fixtures/base.json fixtures/compatible.json reports/compatible
java -Xmx192m -jar target/api-contract-gate-0.1.0-SNAPSHOT.jar fixtures/base.json fixtures/breaking.json reports/breaking
# Review the findings before creating a waiver:
java -Xmx192m -jar target/api-contract-gate-0.1.0-SNAPSHOT.jar fixtures/base.json fixtures/breaking.json reports/waived fixtures/waiver.json
```

Arguments: old local JSON file, new local JSON file, output directory, optional local waiver JSON file. The command writes `report.md` and `report.json`, replacing those files in the chosen directory. Use a dedicated report directory. CMD/PowerShell exit status matters; do not infer success from the presence of a report.

| Exit | Meaning |
|---|---|
| 0 | COMPATIBLE or every breaking finding explicitly WAIVED |
| 1 | BREAKING, at least one unwaived finding |
| 2 | ERROR: invalid input, parser/ref failure, expired/stale/invalid waiver, budget or output failure |

Errors overwrite prior reports with ERROR where writing is possible; output failure also exits 2. CLI diagnostics go to stderr. GitHub CI must use the exit code, not just upload artifacts.

## Supported contracts and policy

Version one supports **OpenAPI 3.0.0–3.0.3 in JSON only**. YAML, 3.1, external references and recursive schemas are explicitly rejected. `$ref` must be a resolvable internal JSON Pointer. Input is parsed from bounded bytes, never from a URL; Swagger resolve/resolveFully are disabled. Internal references are validated before the diff engine runs. Parser messages fail closed, including warnings; this can conservatively reject documents another tool accepts.

Default engine compatibility policy is fixed; arbitrary config switches cannot disable checks. Findings separate removed endpoint, request parameter/body, response, security, other operation and component/extension incompatibilities. Tightening accepted input can break existing requests; weakening required output can break consumers relying on that guarantee. The actual response-direction fixtures verify that removing required output is breaking while adding the guarantee is compatible. Compatibility does not mean unchanged or prove runtime/business semantics.

Reports include category, endpoint/location, consumer impact, canonical source hashes, exact fingerprint and bounded before/after source excerpts. Excerpts may include an entire path item or be truncated at 4,096 characters; inspect the input for referenced components and full detail. Engine categories can group several changes within one operation; a waiver covers that exact category and contract pair, never future changes.

## Waivers

```json
[
  {
    "fingerprint": "copy-the-exact-64-character-fingerprint-from-a-reviewed-report",
    "owner": "api-team",
    "reason": "Consumers migrated under a reviewed rollout plan",
    "expires": "2026-10-01"
  }
]
```

The placeholder above is intentionally invalid until replaced with an actual report fingerprint. SHA-256 binds gate policy version, category, location and **both canonical complete contracts**. Unrelated edits conservatively invalidate a waiver too. No wildcard, broad disable switch or duplicate fingerprint. Owner/reason are required and bounded. Expiry is exclusive at the start of its UTC date. Expired or unused/stale entries fail with exit 2; they are not silently ignored. Object key reordering does not change the canonical hashes; array order does. A waiver is approval data, not proof that consumers actually migrated.

`fixtures/waiver.json` and saved reports are synthetic examples with a fixed expiry. Once expired, their live CLI example correctly fails; the tests use a fixed clock for policy tests and relative future expiry for packaged CLI verification. No fake PR or production migration is represented.

## Bounds and evidence

Per input 256 KiB, JSON nesting 40, strings 65,536 chars, at most 100 paths, reference traversal 20,000 visits/depth 40, at most 128 waivers, report text cap one MiB. Reject duplicate JSON keys and trailing documents. Trusted CI contracts are assumed: the dependency parser/diff engine runs synchronously, so these bounds are not a hard CPU deadline or a hostile-parser sandbox. Run the documented heap cap and an external CI timeout. No live API, URL fetch or shell command is needed for comparison.

Five unit tests exercise the actual engine and policy; one packaged CLI test exercises compatible/breaking/waived/expired/invalid exits and both report formats. [Verification](docs/verification.md) records execution. [ADR](docs/adr/001-policy.md), [references](docs/references.md), [contributing](CONTRIBUTING.md). MIT application source; upstream OpenAPI Diff is Apache-2.0 and other shaded dependencies keep their own notices. No published release, GitHub CI pass or complete semantic compatibility guarantee is claimed.

## Review corrections — 2026-09-20

CLI rejects report/input collisions (including existing hard links) before any success or error report is written. Waiver fields must be JSON strings and expiry is always evaluated in UTC.
