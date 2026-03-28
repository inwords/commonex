# Agent Workflow Lifecycle

This document describes the default local workflow for agents (and developers using agent-assisted workflows) in CommonEx. It keeps the existing lightweight phase model while adding clearer planning, discovery, debugging, and parallel-work rules for
multi-project tasks.

## Phases

1. **Architect** – Understand scope and constraints. Identify the target project (`android/`, `backend/`, `web/`, `infra/`), read the relevant `AGENTS.md` and project instructions, and confirm what “done” looks like. If the task is ambiguous or conflicts with
   repo guidance, **stop and ask** before proceeding.

2. **Plan** – For non-trivial work, choose a planning track, then propose a concrete plan with scope, affected areas, dependencies, and validation. If the plan would touch contracts or multiple services, call out server-client or cross-project impact and
   **stop and ask** if unclear. Do not start implementation until the plan reaches `Implementation Readiness: PASS`.

3. **Code** – Implement changes. Prefer minimal, focused edits; preserve existing architecture and conventions. Fix causes, not symptoms. Prefer precise types and guards that match validated local behavior; avoid speculative defensive unions or branches when the current stack behavior is already known. Follow repo standards (prefer LF line endings, no edits to generated/build output, comments only when non-obvious). If
   scope changes materially after coding starts, return to Plan and issue a `Plan Delta` before continuing.

4. **Evaluate** – Run the chosen validation profile. Start with the smallest sufficient set (e.g. build + host tests + lint); escalate to broader or instrumented validation only when justified by the change. If a command fails repeatedly with no progress,
   **stop and ask**; do not retry blindly.

5. **Review** – Check that changes match the plan and meet repo rules (e.g. patterns, accessibility, tests). Review findings should stay tied to concrete risks, regressions, or missing validation, not speculative cleanup.

6. **Refine** – Apply fixes from review: address failures, improve clarity, update docs or instructions if behavior or contracts changed, and re-run the relevant validation after refinements. If review reveals a design or scope problem, return to Plan and
   update the plan rather than patching around the mismatch.

7. **Report** – Summarize what was done, what was verified (and at what depth), what was skipped or deferred and why, any blockers, and suggested next steps. Keep the report concise; avoid dumping full logs unless relevant to a failure.

## Planning Tracks

Use a planning track that matches the size and ambiguity of the task:

- `Quick Change` – localized, low-ambiguity work with clear file ownership and a small validation surface.
- `Feature Track` – medium-sized work with a few moving parts, explicit dependencies, and a broader validation plan.
- `System Track` – cross-project, architectural, integration, or contract-sensitive work that may require phased delivery and clearer dependency handling.

For `Feature Track` and `System Track`, plans should name:

- objective and scope
- expected affected areas
- dependencies or cross-project touchpoints
- validation depth
- gaps, defaults, or assumptions

## Implementation Readiness

Execution should not start until the plan is explicit about readiness:

- `Implementation Readiness: PASS` when scope is stable enough, affected areas are known, dependencies are identified, and verification is concrete.
- `Implementation Readiness: BLOCKED` when scope is fuzzy, affected files are unclear, dependencies are unresolved, or verification is still hand-wavy.

If readiness is `BLOCKED`, stop and clarify instead of forcing momentum.

## Discovery Protocol

Run discovery only when it materially improves planning or reduces change risk.

- Skip discovery when the target files, owner area, and local pattern are already clear.
- Run one focused discovery pass when you need repo context for a single surface.
- Use parallel discovery only for largely independent surfaces, such as `backend/` plus `web/`, or `android/` plus shared KMM code.

Preferred discovery order in CommonEx:

1. `docs/README.md` and any relevant canonical doc in `docs/`
2. target project `AGENTS.md` and project-local docs
3. targeted repo search for the affected code path, tests, and existing patterns
4. current official upstream docs only when freshness is required by the task

## Plan Delta

If scope changes after a plan already exists, emit a `Plan Delta` instead of restarting from scratch. A useful delta states:

- what changed
- what remains valid
- which steps are removed
- which new steps are added
- whether readiness, routing, or validation depth changed

Use a full re-plan only when the previous plan is no longer trustworthy.

## When To Advance Vs Stop And Ask

- **Advance** when the current phase has a clear outcome and the next step is well-defined (e.g. plan ready, tests pass, review complete).
- **Stop and ask** when: readiness is blocked; the task or scope is ambiguous; repo and upstream documentation conflict; the same validation fails repeatedly; the environment is missing (e.g. no SDK, no device for instrumented tests); or the user requested
  confirmation before a high-impact or destructive action. Do not guess when below the confidence threshold (e.g. 80-85% per repo rules).

## Validation Depth

- Choose validation depth from the task and touched areas (see project-specific skills and `AGENTS.md`). Prefer the **fast local gate** first; add broader or instrumented runs only when the change justifies it (e.g. shared/KMM code, DB, navigation, or explicit
  UI E2E/deeplink work).
- Do not run release flows, baseline-profile generation, or heavy benchmark automation as part of the default workflow; route those to dedicated skills or docs.

## Debug Workflow

Use a debugging workflow only for concrete bug signals, such as:

- a failing test
- a runtime error or stack trace
- a reproducible user scenario
- a validated regression found during review or evaluation

The default debug sequence is:

1. reproduce the issue and record expected vs actual behavior
2. identify the minimal root cause in real code
3. apply the smallest fix that addresses that cause
4. re-run the original reproduction and relevant regression checks

If the issue cannot be reproduced with the current information, stop and report that instead of making speculative fixes.

## Parallel Work And Worktrees

- Parallelize only when file ownership is clear and there are no blocking data dependencies.
- Keep work sequential when tasks overlap in the same files or when one change needs another to land first.
- Use `git worktree` only when normal parallelization is unsafe, such as overlapping high-risk files, larger refactors, or long-running debug flows that need filesystem isolation.
- Prefer ordinary in-place work for small or localized tasks; worktrees are an exception, not the default.

## How Review Feeds Refinement

- Review findings (e.g. lint, test failures, or manual checklist gaps) become refinement tasks. Re-run the same validation after refinement to confirm fixes.
- If review reveals a design or scope problem, return to Plan and update the active plan or issue a `Plan Delta` before further Code.

## References

- Root [`AGENTS.md`](../AGENTS.md) – repo-wide rules, workflow agent rules, freshness policy
- Project `AGENTS.md` files – build commands, validation steps, patterns
- [`docs/README.md`](README.md) – cross-project doc discovery
- [`android/docs/local-agent-prerequisites.md`](../android/docs/local-agent-prerequisites.md) – local Android env and version sources of truth
- [`android/.agents/skills/run-android-local-long-task/SKILL.md`](../android/.agents/skills/run-android-local-long-task/SKILL.md) – Android validation profiles and reporting
