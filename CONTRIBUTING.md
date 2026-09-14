# Contributing

Use Java 21 and `./mvnw spotless:apply verify -Pintegration`. Tests run the actual diff engine and packaged CLI, without external infrastructure. Keep invalid input fail-closed, preserve direction-sensitive compatibility fixtures and never broaden waivers into wildcard bypasses. Do not commit private API contracts.
