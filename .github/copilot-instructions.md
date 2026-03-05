# GitHub Copilot – Repo guidance

Use the repo’s **source-of-truth** agent instructions; this file is a thin adapter.

## Where to read first

- **Repo-wide:** [`AGENTS.md`](../AGENTS.md) – scope, precedence, cross-cutting standards, workflow rules, freshness policy.
- **Per project:** `android/AGENTS.md`, `backend/AGENTS.md`, `web/AGENTS.md`, `infra/AGENTS.md` (and `.github/instructions/*` for path-specific guidance).

Follow the most specific, relevant instructions for the code you’re editing.

## Workflow and validation

- Prefer a short **plan** for non-trivial work; **evaluate** with the smallest sufficient validation (e.g. build + tests + lint) and escalate only when the change justifies it. See [`docs/agent-workflows.md`](../docs/agent-workflows.md) for the Architect →
  Plan → Code → Evaluate → Review → Refine → Report lifecycle.
- **Stop and ask** when the task is ambiguous, when repo and upstream docs conflict, or when validation fails repeatedly. Fix causes, not symptoms; keep changes minimal.

## Freshness and discipline

- **Search official current docs** when you need up-to-date info (library versions, migrations, tooling, version-specific errors). **Do not** re-research conventions already documented in this repo. If upstream docs contradict repo guidance, **flag the
  conflict** instead of silently overriding.
- **Do not duplicate tooling versions** in docs; reference build/CI as source of truth (see [`android/docs/local-agent-prerequisites.md`](../android/docs/local-agent-prerequisites.md) for Android).
