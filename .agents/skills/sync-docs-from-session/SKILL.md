---
name: sync-docs-from-session
description: Propose and apply updates to repo docs, skill instructions, and agent guidance based on session-verified knowledge and user signals about agent behavior. Use when the user asks to sync/update instructions, AGENTS.md, README.md, docs/*, project docs, or skill docs with new knowledge or behavior feedback learned in the current session.
---

# Sync Docs From Session

## Purpose

Sync documentation and instructions to session-verified knowledge. Also capture user signals about agent behavior and turn them into candidate instruction updates.

Always show the proposal first. After the proposal:

- apply `Auto-store` items automatically
- ask the user whether to apply all suggested changes or only selected suggestion numbers
- store `Captured only` signals in the local backlog file

The scope is all session-relevant knowledge and behavior feedback supported by evidence gathered in the current session, even if the user did not ask about a specific aspect explicitly.

## Inputs (ask only for missing values)

Preferred input format (YAML list):

- kind: knowledge
  statement: <new or corrected knowledge>
  evidence: <user quote or file path/line observed in this session>
  scope: <optional; files or areas likely impacted>

- kind: agent_signal
  quote: <user statement about agent behavior>
  evidence: <user quote or session context>
  scope: <optional; repo-wide, project-specific, skill-specific>

If the user asks to run the skill without providing items, draft a proposal from session evidence with three sections:

- `Auto-store`
- `Suggested changes`
- `Captured only`

Use these sources:

- Explicit user statements in the current session.
- Explicit user statements about agent behavior in the current session.
- Files changed or created in this session (paths only, no assumptions).
- Doc discovery results (new README/guides and tooling folders).
- Session code diffs in non-doc files (behavior/policy changes and repeated reusable patterns).

In dirty worktrees, do not silently narrow the proposal to only the area the agent most recently edited. Instead:

- include any item that was touched, validated, or materially analyzed in the current session
- exclude unrelated pre-existing worktree changes that were not used as evidence in the current session
- if the repo is noisy, say that the proposal is filtered to session-touched or session-validated evidence

Present the proposal before making any file changes. Lead with reusable rule or doc deltas, not a full session recap. For `Suggested changes`, use a numbered list and end with:

`Apply all suggested changes, or only suggestions number 1,2,3...? Reply with: all / 1,3 / none / revise 2`

## Action buckets

Classify every proposed change or captured signal into one of these buckets.

**Auto-store**

- Apply automatically after the proposal.
- Use for explicit knowledge and for inferred knowledge the agent is genuinely sure about.
- Require clear wording, clear scope, obvious target file, and no conflict with existing instructions.
- Include exact evidence.

**Suggested changes**

- Show as numbered items for user selection.
- Use for plausible instruction or doc deltas where wording, scope, or source-of-truth fit is not fully obvious.
- Ask whether to apply all suggestions or only selected numbers.

**Captured only**

- Capture the signal but do not turn it into a doc or instruction edit yet.
- Use for ambiguous, weak, premature, or not-yet-reusable behavior signals.
- Persist these signals to the local backlog file so the user can decide later which ones deserve promotion.

## Behavioral change detection (required)

When running this skill, you must infer behavior-level changes from session edits, not only file/path changes.

1) Inspect changed non-doc files in this session and summarize runtime/contract behavior changes.
2) Cross-check with related tests/build outputs from this session to strengthen confidence.
3) Generate candidate knowledge items for behavior/policy changes even if the user did not explicitly provide them.
4) Include concrete evidence: file path and line/snippet reference, plus test/build command when available.

Behavior/policy items inferred from non-doc edits may be `Auto-store` when confidence is high. Otherwise classify them as `Suggested changes`.

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

## Agent signal capture (required)

When running this skill, you must treat user feedback about agent behavior as a separate evidence stream.

1) Capture every user signal about agent behavior, even if it is unclear, one-off, or not ready to store.
2) Normalize each signal into a compact interpretation with topic, likely meaning, and likely scope.
3) Synthesize reusable candidate rules when the signal is clear enough to propose a durable instruction delta.
3a) Treat signals where the agent accepted third-party feedback over explicit user direction or a recently agreed design as high-priority candidate rules; do not downgrade them to weak capture-only notes when the conflict and impact are clear.
4) Lead the proposal with candidate rules and instruction deltas, not with a dump of all session facts.
5) Merge multiple supporting signals into one candidate rule when they point to the same behavior.
6) Do not route agent-behavior updates to `.github/instructions/*`.

Capture both positive and negative signals. Preserve the original quote or a precise paraphrase as evidence.

Agent signal template:

- quote: <user statement>
  evidence: <exact quote or short session reference>
  interpretation: <what behavior change this might imply>
  scope: <repo-wide, project-specific, skill-specific, or unknown>
  bucket: <Auto-store | Suggested changes | Captured only>

Candidate rule template:

- rule: <instruction text to add or revise>
  why: <why the rule is proposed>
  evidence: <supporting user signals and session artifacts>
  trigger: <when it should apply>
  non-trigger: <when it should not apply>
  target: <file to update>

## Pattern discovery (required when applicable)

Propose **code patterns** or **architectural patterns** only when all are true:

- The pattern appears in files edited in this session.
- It appears at least twice (same file or multiple files).
- The pattern is specific and actionable (not generic advice).
- Evidence includes exact file paths and a short snippet reference.

Otherwise, do not propose the pattern.

When a pattern qualifies:

1) Add it to the proposal list even if the user did not explicitly ask for pattern docs.
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
5) Draft backlink proposals as suggested changes when they are not obvious enough to auto-store.

## What to update (repo-wide)

- AGENTS.md (root and project-level)
- Skill `SKILL.md` files
- `docs/agent-workflows.md`
- README.md
- docs/* and any project `docs/*` folders
- New docs under `docs/*` or project `docs/*` when no existing file is a good canonical fit
- Any other documentation files referenced by the user

Prefer links to a single source of truth over duplicated content.

## Local backlog file

Persist `Captured only` agent signals to:

- `.agents/local/sync-docs-from-session/captured-only-signals.jsonl`

Create parent directories on demand. Append one JSON object per line. Include:

- `captured_at`
- `quote`
- `evidence`
- `interpretation`
- `scope`
- `session_hint`

Treat this file as local scratch memory:

- do not commit it
- do not turn it into a user-facing recap unless asked
- do not promote entries from it automatically without current-session review

## Workflow (propose-first)

1) Gather knowledge items and agent signals and validate evidence.
2) Run behavioral change detection on session code diffs and tests (required).
3) Run agent signal capture and synthesize candidate rules (required).
4) Run pattern discovery on session code diffs and repeated refactors when applicable.
5) In dirty worktrees, explicitly separate session-derived evidence from unrelated existing changes before drafting knowledge items.
6) Use repo search to find impacted docs and instruction files, including the most likely canonical pattern/design doc.
7) Decide whether the best source of truth is:
    - an existing doc
    - an existing doc plus short backlinks
    - a new doc plus minimal backlinks
8) Draft a minimal edit plan (file list + change summary), distinguishing concise instruction updates, canonical-doc updates, and any proposed new doc creation.
9) Present proposed edits in three sections: `Auto-store`, `Suggested changes`, and `Captured only`.
10) Apply `Auto-store` items immediately after presenting the proposal. Preserve the repo's preferred LF line endings unless a file or tool explicitly requires a different EOL.
11) Persist `Captured only` items to the local backlog file.
12) If `Suggested changes` exist, ask whether to apply all suggestions or only selected numbers. Apply only the confirmed items.
13) Summarize updates and note any items not changed due to missing evidence or lack of confirmation.

## Editing rules

- Keep changes minimal and scoped to the evidence.
- Follow the most specific instruction file when multiple exist.
- Avoid duplicating content; link to the most specific doc instead.
- Always present the proposal before editing, even when all items are `Auto-store`.
- For inferred reusable patterns, prefer the area's canonical patterns/design doc as the source of truth; use `AGENTS.md` for short operational guidance only.
- Do not under-scope proposals to only the user's last-mentioned area if the session produced other verified or strongly supported inferred knowledge items.
- It is acceptable to create a new doc when existing docs would cause duplication, mixed-purpose sprawl, or an unclear source of truth.
- Link propagation: when a new README or guide is added in a subfolder, add a short pointer from the most appropriate level doc.
- For behavior/policy changes, document: trigger, default behavior, limits/budget, and validation command.
- For inferred pattern changes, document: when to apply the pattern, when not to apply it, and one short implementation shape example.
- For new-doc proposals, include: why existing docs are a poor fit, the proposed path/title, and which existing docs should link to it.
- For agent-behavior changes, prefer concise rule deltas with evidence over long narrative recaps.
- Capture all user behavior signals first; only filter at promotion time.
- Store `Captured only` signals in the local backlog file instead of forcing them into docs or instructions.
- Do not route agent-behavior updates to `.github/instructions/*`.
- Preserve existing structure and tone.
- Maintain the repo's preferred LF line endings unless a file or tool explicitly requires a different EOL.

## Stop conditions

- If suggested changes are present and the user does not confirm them, do not apply those suggested changes.
- If evidence is too weak for a rule proposal, downgrade it to `Captured only` instead of forcing a doc edit.
- If evidence is insufficient even for capture, ask for more detail.
