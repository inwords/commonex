# CommonEx Backend

CommonEx Backend is the server-side component of the CommonEx expense sharing platform.

It exists to keep business rules, data consistency, and integrations centralized in one authoritative service so Android/iOS and web clients behave consistently.

The service exposes REST and gRPC APIs, runs core expense-sharing workflows, persists data in PostgreSQL, and emits telemetry for operations/monitoring.

For engineering workflow, commands, runtime details, and troubleshooting, see `backend/AGENTS.md`.
