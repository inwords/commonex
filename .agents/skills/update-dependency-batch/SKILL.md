---
name: update-dependency-batch
description: Use when updating dependency or container versions across CommonEx projects and the work should start with internet verification, a per-bundle summary plus full release-note output, a user selection gate, and then implementation.
---

# Update Dependency Batch

## Workflow

1. Discover current and target versions (official sources; record one source URL per bundle).
2. Write the proposal (short summary per bundle, then full release notes for that bundle).
3. **Stop.** Do not edit version files until the user names bundle numbers in a follow-up message.
4. Apply only the selected bundles, then run project validation commands.

## Proposal output format

Use **one section per bundle**. Do not split into separate “menu” and “release notes” parts.

For each bundle `N`:

```markdown
## N — <bundle name>

**Current → target:** …
**Keys / files:** …
**Why together:** … (omit if standalone)
**Source:** one URL

**Summary:** …

1. <full changelog line or paragraph>
2. …
```

**Summary** (required, before the numbered list):

- A concise digest of what matters for this repo: breaking changes, deprecations, migrations, new runtime/toolchain requirements, and notable fixes or features.
- Paraphrase and roll up here; this is the only place summarization is allowed.

**Numbered changelog** (required, after the summary):

- Include **all** official changelog content for `current → target`.
- **Do not summarize or paraphrase** inside the numbered list — copy upstream wording (minus allowed metadata stripping).
- Restart numbering at `1` for each bundle.
- When stripping metadata from copied text, you **may remove**:
  - URLs and “full changelog” / diff link lines
  - commit hashes and compare links
  - contributor / thanks / reaction sections
  - download counts, asset tables, publish timestamps
  - PR numbers, issue/ticket IDs, and review references (e.g. `(#1234)`, `b/123`, `[KTOR-8421]`, `(I70ad8, b/462092640)`)
- **Keep** section headings, breaking-change / deprecation / migration wording, and version or compatibility requirements.

If a changelog only points at another dependency, mention the bump in **Summary**, then follow it recursively and continue the numbered list under a short `Nested: <name> <version>` subheading in the same bundle. Include full nested changelog text in the numbered list; do not replace nested notes with summary-only text. Stop on cycles, repetition, or missing public notes (say so in Summary and in the numbered list).

After all bundles, add short lists if needed:

- **Already on latest** — dependencies with nothing to propose.
- **Excluded** — candidates omitted with a concrete reason (never silent skips).

End with: ask the user which bundle numbers to apply (e.g. `2,3,4`, `all`, `all-safe`). Do not edit manifests until they reply.

## Stability policy

Tiers: stable → rc → beta → alpha → preview / eap / snapshot / dev.

- Never move to a lower tier.
- Do not move stable to pre-release unless the user asks.
- Pre-release may move sideways or up within the same line.
- If omitting a candidate, state why.

## Apply rules (after user selection)

- Update only chosen bundles.
- Refactor deprecations in touched code when reasonable.
- Ask instead of guessing or suppressing warnings.
- Run project-skill validation after edits.

## Project routing

- `update-mobile-dependencies` → `android/`
- `update-backend-dependencies` → `backend/`
- `update-web-dependencies` → `web/`
- `update-infra-dependencies` → `infra/`
