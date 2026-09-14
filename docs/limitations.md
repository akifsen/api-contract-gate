# Limitations

Only local JSON OpenAPI 3.0.0–3.0.3, nonrecursive internal references. YAML, 3.1 and external refs are rejected. All parser messages fail closed, potentially rejecting otherwise usable contracts. Default engine rules cannot represent every runtime, serialization or business semantic change.

Findings group engine categories per operation; fingerprints bind the complete canonical input pair, so unrelated edits invalidate approval. Excerpts are bounded and may omit referenced component detail. Schema fallback locations are engine list indices within that exact pair. Fixed fixture waivers intentionally expire.

Input/output/structure caps do not impose a hard synchronous diff CPU deadline. Use trusted CI contracts, heap cap and job timeout. Reports can contain source excerpts and must share input access controls. No live API is exercised and a compatible result is not a consumer-test replacement.
