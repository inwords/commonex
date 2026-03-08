# CommonEx Docs

Use these docs as the primary starting points for repo-wide knowledge:

- [domain.md](domain.md) - Product model, core entities, domain rules, and client/domain alignment notes.
- [network-contracts.md](network-contracts.md) - Shared HTTP and transport behavior across backend, web, and mobile clients.
- [agent-workflows.md](agent-workflows.md) - The default Architect -> Plan -> Code -> Evaluate -> Review -> Refine -> Report workflow for non-trivial tasks.

## Documentation Topology

- `docs/` holds cross-project canonical references.
- Project `AGENTS.md` files hold operational instructions for working in that project.
- Project-local `docs/README.md` files are the preferred entry point when a project has multiple focused reference docs.
- Prefer one canonical doc plus short backlinks instead of repeating the same guidance in multiple READMEs or AGENTS files.

Project-specific operational docs:

- Android/KMM: [../android/AGENTS.md](../android/AGENTS.md) and [../android/docs/README.md](../android/docs/README.md)
- Backend: [../backend/AGENTS.md](../backend/AGENTS.md)
- Web: [../web/AGENTS.md](../web/AGENTS.md)

Prefer linking back to these canonical docs instead of duplicating the same guidance in multiple AGENTS or README files.
