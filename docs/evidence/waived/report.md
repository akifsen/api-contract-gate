# API compatibility report

Status: **WAIVED**; exit 0.

Old SHA-256: 799ed70ea1fd8c2b8df047e25b476368ba44f2ec6beac806c7ebf5451edf1133

New SHA-256: 66d6baffbaf6d00c18db470c48501cd862ec1b57b3906ac0875d30ab20c1b2cf

## REQUEST_PARAMETERS — GET /orders

Previously valid request parameters may be rejected.

Fingerprint: a8682d448247ae94f8314ee1c536623498ddfa19a2110d816ede796c99766975

Decision: WAIVED

<pre>Before: {"get":{"responses":{"200":{"description":"Orders"}}}} After: {"get":{"parameters":[{"in":"query","name":"search","required":true,"schema":{"type":"string"}}],"responses":{"200":{"description":"Orders"}}}}</pre>

Owner: synthetic-api-team; expires: 2026-10-01; reason: Synthetic coordinated consumer migration example

- OpenAPI Diff 2.1.7 default compatibility rules; runtime/business semantics are not proven.
- Fingerprints bind category/location and the exact canonical contract pair; unrelated edits conservatively invalidate waivers.
- JSON OpenAPI 3.0.0–3.0.3 only; external refs and recursive schemas are rejected. Compatible does not mean unchanged.
