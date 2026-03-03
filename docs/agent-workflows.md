# Agent Workflow Lifecycle

This document describes a lightweight local workflow for agents (and developers using agent-assisted workflows). It adapts a simple phase model without requiring external orchestration.

## Phases

1. **Architect** – Understand scope and constraints. Identify the target project (`android/`, `backend/`, `web/`, `infra/`), read the relevant `AGENTS.md` and project instructions, and confirm what “done” looks like. If the task is ambiguous or conflicts with
   repo guidance, **stop and ask** before proceeding.

2. **Plan** – Propose a short plan for non-trivial work. Break the work into concrete steps; choose the right validation depth (e.g. fast local gate vs broader tests vs instrumented runs) based on what is being changed. For project-specific validation
   profiles, see project docs (e.g. `android/.agents/skills/run-android-local-long-task`). If the plan would touch contracts or multiple services, call out server–client or cross-project impact and **stop and ask** if unclear.

3. **Code** – Implement changes. Prefer minimal, focused edits; preserve existing architecture and conventions. Fix causes, not symptoms. Follow repo standards (CRLF, no edits to generated/build output, comments only when non-obvious).

4. **Evaluate** – Run the chosen validation profile. Start with the smallest sufficient set (e.g. build + host tests + lint); escalate to broader or instrumented validation only when justified by the change. If a command fails repeatedly with no progress, **stop and ask**; do not retry blindly.

5. **Review** – Check that changes match the plan and meet repo rules (e.g. patterns, accessibility, tests). Consider whether review findings require code or doc updates.

6. **Refine** – Apply fixes from review: address failures, improve clarity, update docs or instructions if behavior or contracts changed. Re-run the relevant validation after refinements.

7. **Report** – Summarize what was done, what was verified (and at what depth), what was skipped or deferred and why, any blockers, and suggested next steps. Keep the report concise; avoid dumping full logs unless relevant to a failure.

## When to advance vs stop and ask

- **Advance** when the current phase has a clear outcome and the next step is well-defined (e.g. plan agreed, tests pass, review complete).
- **Stop and ask** when: the task or scope is ambiguous; repo and upstream documentation conflict; the same validation fails repeatedly; the environment is missing (e.g. no SDK, no device for instrumented tests); or the user requested confirmation before a
  high-impact or destructive action. Do not guess when below the confidence threshold (e.g. 80–85% per repo rules).

## Validation depth

- Choose validation depth from the task and touched areas (see project-specific skills and `AGENTS.md`). Prefer the **fast local gate** first; add broader or instrumented runs only when the change justifies it (e.g. shared/KMM code, DB, navigation, or explicit
  UI E2E/deeplink work).
- Do not run release flows, baseline-profile generation, or heavy benchmark automation as part of the default workflow; route those to dedicated skills or docs.

## How review feeds refinement

- Review findings (e.g. lint, test failures, or manual checklist gaps) become refinement tasks. Re-run the same validation after refinement to confirm fixes. If review reveals a design or scope problem, return to Plan (or Architect) and adjust before further
  Code.

## References

- Root [`AGENTS.md`](../AGENTS.md) – repo-wide rules, workflow agent rules, freshness policy
- Project `AGENTS.md` files – build commands, validation steps, patterns
- [`android/docs/local-agent-prerequisites.md`](../android/docs/local-agent-prerequisites.md) – local Android env and version sources of truth
- [`android/.agents/skills/run-android-local-long-task/SKILL.md`](../android/.agents/skills/run-android-local-long-task/SKILL.md) – Android validation profiles and reporting
