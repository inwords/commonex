---
name: update-dependency-batch
description: Use when planning or applying dependency or container version updates across CommonEx projects.
---

# Update Dependency Batch

Research every proposed bundle against the repository before recommending it. Give the user the relevant outcome, not copied changelogs.

## Workflow

1. Discover current and eligible target versions from official sources. Apply the stability policy.
2. Inventory every direct release in each `current exclusive → target inclusive` range. When a direct release only bumps a nested dependency, inventory every nested release needed to understand that bump. Every inventory item needs an official source; surface a missing source as a coverage gap. Never call a range fully reviewed while a gap exists.
3. Review the complete sourced inventory, then inspect actual repository usage: manifests/catalogs, build or runtime configuration, and affected code paths.
4. For each bundle, evaluate relevant new features against that usage and prepare the proposal below.
5. **Stop.** Do not edit version files until the user names bundle numbers in a follow-up message.
6. Apply exactly the selected bundles and only their authorized changes, validate, then complete the release-note coverage audit.

## Proposal output

Use one concise section per bundle; do not split a menu from the notes.

```markdown
## N — <bundle name>

**Current → target:** …
**Keys / files:** …
**Why together:** … (omit if standalone)
**Official release inventory:** <direct and needed nested ranges; official URLs>
**Coverage:** Complete / Gaps: …
**Fully reviewed:** Yes / No
**all-safe eligible:** Yes / No — …

**Project verdict:** <one sentence naming the concrete affected code/configuration, or no impact>
**Relevant official notes:**
- …

| Feature / change | Repo evidence | Decision | Exact local action (files / symbols) | Validation |
|---|---|---|---|---|
| … | … | Required migration / Adopt now / Consider later / Not applicable | … | … |
```

- The inventory shows ranges, official URLs, and coverage gaps—not raw notes. A **Complete** inventory with every item reviewed is the only basis for **Fully reviewed: Yes**.
- **Project verdict** is one concrete outcome only; do not use semantic-version filler (for example, “stable compiler patch releases”). Say directly when there is no repository-relevant impact.
- **Relevant official notes** are concise source-backed bullets: each supplies a distinct release fact that matters here. Preserve migration/compatibility requirements; omit unrelated metadata.
- The table contains only material required migrations or features that merit a project-fit decision; if none exist, use one “no relevant new feature” row. Do not add rows for routine docs/internal/CI-only or otherwise filtered-out notes: the inventory proves they were reviewed. Each **Required migration** or **Adopt now** row must state a precise local action, affected files/symbols, and a validation command or check; otherwise it is not authorized.
- Choose one decision per row:
  - **Required migration** — necessary compatibility work; it is not optional adoption.
  - **Adopt now** — low-risk, in-scope, behavior-preserving improvement to an existing path, verifiable during this update.
  - **Consider later** — useful but broader, behavior-changing, insufficiently scoped, or not verifiable now.
  - **Not applicable** — does not match the listed repository evidence.

After all bundles, add brief lists when needed:

- **Already on latest** — dependencies with nothing to propose.
- **Excluded** — candidates omitted with a concrete reason.

End by asking which bundle numbers to apply (for example, `2,3,4`, `all`, or `all-safe`). Do not edit manifests until the user replies.

`all-safe` means a same-major stable target with complete coverage, no unresolved migration/coverage gap or known breaking migration, and only version/lockfile work plus explicitly described localized, behavior-preserving Adopt-now work covered by normal validation. `all-safe` selects only bundles marked **Yes**; all others must be **No** with the reason shown.

## Stability policy

Tiers: stable → rc → beta → alpha → preview / eap / snapshot / dev.

- Never move to a lower tier.
- Do not move stable to pre-release unless the user asks.
- Pre-release may move sideways or up within the same line.
- If omitting a candidate, state why.

## Apply rules (after selection)

- Update exactly the selected bundle versions and files. Selection authorizes only the declared version/lockfile edits plus **Required migration** and **Adopt now** rows that explicitly name the local action, affected files/symbols, and validation. Vague text authorizes no code/configuration change.
- An Adopt-now change must remain low-risk, in scope, and behavior-preserving. Do not implement Consider-later or Not-applicable items, or other broader/behavior-changing work, without separate approval.
- Treat warnings, errors, and unresolved migration questions as findings: do not guess or suppress them; ask the user when a decision is required.
- Run the routed project validation after edits.
- Before handoff, audit release-note coverage: implementation/PR notes must capture coverage gaps, required/breaking migrations, completed Adopt-now work, and relevant deferred opportunities (for example, OpenTelemetry semantic-convention changes).

## Project routing

- `update-mobile-dependencies` → `android/`
- `update-backend-dependencies` → `backend/`
- `update-web-dependencies` → `web/`
- `update-infra-dependencies` → `infra/`
