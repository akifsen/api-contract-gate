# ADR 001: Reuse a compatibility engine; fail closed around it

Accepted. Use OpenAPI Diff rather than implementing schema compatibility from scratch. Validate bounded local JSON and internal refs before calling the parser with external resolution disabled. Treat parser messages as errors. Report engine incompatibilities with consumer-oriented categories and exact source-pair fingerprints.

Waivers bind an exact category/location and both complete canonical documents. This is deliberately more conservative than matching only an endpoint name: a later edit invalidates the approval. A fixed UTC expiry prevents permanent undocumented exceptions. Recursive schemas, YAML and remote refs are deferred to keep v1 input authority and resource scope explicit. OpenAPI does not encode every behavioral contract, so passing this gate never replaces consumer/runtime tests.
