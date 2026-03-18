---
name: sync-docs-from-session
description: Propose and apply updates to repo docs and agent instructions based on session-verified knowledge. Use when the user asks to sync/update instructions, AGENTS.md, README.md, docs/*, project docs, or other documentation with new, non-obvious information learned in the current session.
---

# Sync Docs From Session

## Purpose

Sync documentation and instructions to session-verified knowledge. Always show the proposal first. After the proposal:

- apply Tier A items automatically
- wait for confirmation before applying Tier B items

The scope is all knowledge that is session-relevant and supported by evidence gathered in the current session, even if the user did not ask about a specific aspect explicitly.

## Inputs (ask only for missing values)

Preferred input format (YAML list):

- statement: <new or corrected knowledge>
  evidence: <user quote or file path/line observed in this session>
  scope: <optional; files or areas likely impacted>

If the user asks to run the skill without providing knowledge items, draft a suggested list with two sections:

- Tier A (explicit, auto-accepted)
- Tier B (derived, needs confirmation)

Use these sources:

- Explicit user statements in the current session.
- Files changed or created in this session (paths only, no assumptions).
- Doc discovery results (new README/guides and tooling folders).
- Session code diffs in non-doc files (behavior/policy changes and repeated reusable patterns).

In dirty worktrees, do not silently narrow the proposal to only the area the agent most recently edited. Instead:

- include any item that was touched, validated, or materially analyzed in the current session
- exclude unrelated pre-existing worktree changes that were not used as evidence in the current session
- if the repo is noisy, say that the proposal is filtered to session-touched or session-validated evidence

Present the draft list as a numbered list before making any file changes. Clearly label which items are Tier A and will be applied automatically, and which items are Tier B and require confirmation.

## Evidence tiers

**Tier A: Explicit (auto-accepted after proposal)**

- Direct user statements in this session
- Doc or instruction files created, edited, or removed in this session when the change is intended to remain
- Session-validated behavior confirmed by local build/test results in this session (include exact command/output evidence)

Do not treat raw non-doc code diffs as Tier A by themselves. They become Tier A only when the behavior/policy change is validated in this session or the user explicitly states it.

**Tier B: Derived (confirm before changes)**

- Inferences directly observable from files (no speculation)
- Examples:
    - New tool folder or README implies the tool is part of the workflow
    - New README implies a parent doc should link to it
    - Moved files imply backlinks should be updated

Do not apply Tier B changes unless confirmed.

## Behavioral change detection (required)

When running this skill, you must infer behavior-level changes from session edits, not only file/path changes.

1) Inspect changed non-doc files in this session and summarize runtime/contract behavior changes.
2) Cross-check with related tests/build outputs from this session to mark changes as session-verified.
3) Generate candidate knowledge items for behavior/policy changes even if the user did not explicitly provide them.
4) Include concrete evidence: file path and line/snippet reference, plus test/build command when available.

Behavior/policy items inferred from non-doc edits default to Tier B unless the user explicitly stated them or session validation elevates them to Tier A.

Example categories:

- Network resilience policy (HTTP status handling, retry budgets, backoff, method filters, retry conditions).
- Error/result mapping semantics (e.g., status code to retry/failure mapping).
- Task wiring and test executability changes (new/renamed runnable tasks, CI inclusion changes).
- Default behavior changes vs optional behavior (enabled/disabled by default, opt-in/out).

Behavior item template:

- statement: <what changed in behavior>
  evidence: <path/line and optional command output from this session>
  impact: <who/what is affected; default scope>
  scope: <docs/files to update>

## Pattern discovery (required when applicable)

Propose **code patterns** or **architectural patterns** only when all are true:

- The pattern appears in files edited in this session.
- It appears at least twice (same file or multiple files).
- The pattern is specific and actionable (not generic advice).
- Evidence includes exact file paths and a short snippet reference.

Otherwise, do not propose the pattern.

When a pattern qualifies:

1) Add it to the Tier B proposal list even if the user did not explicitly ask for pattern docs.
2) Prefer updating the canonical patterns/design doc for that area (`docs/*`, `android/docs/*`, README section, etc.) instead of documenting the full rule only in `AGENTS.md`.
3) Keep `AGENTS.md` concise: add a short pointer or rule there only if it helps agent execution, and link to the canonical doc when possible.
4) If no obvious canonical doc exists, first evaluate whether an existing doc can hold the new knowledge cleanly without becoming a mixed-purpose sink.
5) If the knowledge is durable, cross-cutting, and would be a better source of truth as its own topic (for example a repo-wide contract, protocol guide, or subsystem reference), propose a new doc instead of forcing it into an ill-fitting existing file.
6) When proposing a new doc, also propose the minimal backlink(s) needed from existing docs so it is discoverable.

## Doc discovery (before proposing edits)

1) List new or updated docs in the session (README.md, docs/*, project docs such as android/docs/* or backend/docs/*, and AGENTS.md).
2) Identify new tooling folders (e.g., android/marathon, android/gradle/profiler).
3) Choose the most appropriate level doc to link from:
    - Project AGENTS.md for project-specific docs
    - Root AGENTS.md only for cross-project docs
4) If no existing doc is a clean source-of-truth fit, identify the ideal new doc location and title.
5) Draft backlink proposals as Tier B items and ask for confirmation.

## What to update (repo-wide)

- AGENTS.md (root and project-level)
- .github/instructions/*
- README.md
- docs/* and any project `docs/*` folders
- New docs under `docs/*` or project `docs/*` when no existing file is a good canonical fit
- Any other documentation files referenced by the user

Prefer links to a single source of truth over duplicated content.

## Workflow (propose-first)

1) Gather knowledge items and validate evidence.
2) Run behavioral change detection on session code diffs and tests (required).
3) Run pattern discovery on session code diffs and repeated refactors; generate Tier B candidates for reusable patterns when applicable.
4) In dirty worktrees, explicitly separate session-derived evidence from unrelated existing changes before drafting knowledge items.
5) Use rg to find impacted docs and instruction files, including the most likely canonical pattern/design doc.
6) Decide whether the best source of truth is:
    - an existing doc
    - an existing doc plus short backlinks
    - a new doc plus minimal backlinks
7) Draft a minimal edit plan (file list + change summary), distinguishing concise instruction updates, canonical-doc updates, and any proposed new doc creation.
8) Present proposed edits, explicitly split into Tier A and Tier B.
9) Apply Tier A immediately after presenting the proposal. Preserve the repo's preferred LF line endings unless a file or tool explicitly requires a different EOL.
10) If Tier B exists, ask for confirmation and apply only the confirmed Tier B items.
11) Summarize updates and note any files not changed due to missing evidence.

## Editing rules

- Keep changes minimal and scoped to the evidence.
- Follow the most specific instruction file when multiple exist.
- Avoid duplicating content; link to the most specific doc instead.
- Always present the proposal before editing, even when all items are Tier A.
- For inferred reusable patterns, prefer the area's canonical patterns/design doc as the source of truth; use `AGENTS.md` for short operational guidance only.
- Do not under-scope proposals to only the user's last-mentioned area if the session produced other verified or strongly supported inferred knowledge items.
- It is acceptable to create a new doc when existing docs would cause duplication, mixed-purpose sprawl, or an unclear source of truth.
- Link propagation: when a new README or guide is added in a subfolder, add a short pointer from the most appropriate level doc.
- For behavior/policy changes, document: trigger, default behavior, limits/budget, and validation command.
- For inferred pattern changes, document: when to apply the pattern, when not to apply it, and one short implementation shape example.
- For new-doc proposals, include: why existing docs are a poor fit, the proposed path/title, and which existing docs should link to it.
- Preserve existing structure and tone.
- Maintain the repo's preferred LF line endings unless a file or tool explicitly requires a different EOL.

## Stop conditions

- If Tier B items are present and the user does not confirm them, do not apply those Tier B changes.
- If evidence is insufficient, ask for more detail.
