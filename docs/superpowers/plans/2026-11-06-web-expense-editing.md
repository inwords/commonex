# Web Expense Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Edit existing expenses behind an off-by-default build-time flag using backend PR #344's revisions API.

**Architecture:** Extend existing expense API/service/store and existing form/details dialog. Preserve v2 read/create routes, use v4 for revisions. Keep the original snapshot as the optimistic concurrency baseline.

**Tech Stack:** Next.js 16, React, MUI, MobX, react-hook-form, Node test runner.

**Spec:** docs/superpowers/specs/2026-11-06-web-expense-editing-design.md

## Global Constraints

- NEXT_PUBLIC_EXPENSE_EDITING_ENABLED must equal the literal string `true`.
- Enabled builds create new events with version 2; legacy events remain unchanged.
- No new dependencies or backend changes.
- Preserve original createdAt and unchanged financial data; no expense-created analytics for revisions.

## Review Focus

- Equal saved shares may cover fewer users than current event membership: preserve explicit shares.
- Historical automatic exchange rates differ from today's rates: never infer custom intent by comparison with today's rate.
- Transient failure may occur after commit: retain idempotency key across retries.
- Another participant saves first: retain draft on conflict and require explicit reload.
- Flag disabled or absent versions: fail closed and preserve current creation payloads/routes.

### Task 1: Contract and revision orchestration

**Files:** shared/config/feature-flags.ts; expense lib/expense-editing.ts; expense types, API, service and store; event API/types; web/test/expense-editing.test.ts.

**Interfaces:** isExpenseEditingEnabled(): boolean; canEditExpense(event, expense): boolean; getExpenseEditForm(expense,eventCurrencyId): CreateExpenseForm; buildExpenseRevision(expense,data,eventCurrencyId,userIds): ReviseExpense; ExpenseService.updateExpense(expense,data,pinCode): Promise<void>; ExpenseService.reloadExpense(expense,pinCode): Promise<void>.

- [x] Write failing contract tests for flag eligibility, v2 event creation, revision transport, split/rate preservation, version conflict, retry idempotency and replacement in store.
- [x] Run `node --test test/expense-editing.test.ts`; verify missing-feature failure.
- [x] Implement minimal typed helpers and revision orchestration; keep create flows unchanged except explicitly flag-gated event version.
- [x] Run targeted tests, then full `npm test`.

### Task 2: Editing dialog and deployment switch

**Files:** ExpenseForm.tsx, ExchangeRateInput.tsx, ExpenseDetailsModal.tsx; localized strings/errors; web/Dockerfile; web/README.md; UI verification artifacts outside checkout.

**Interfaces:** ExpenseForm receives editing, busy, submitLabel, and optional onCancel; passes form values to existing onSuccess. ExpenseDetailsModal manages mode/conflict/reload while service owns data writes.

- [x] Extend form with edit-aware initialization and explicit custom-rate intent; derive manual total from splits and validate duplicate/invalid participants and amounts.
- [x] Add edit/save/cancel and conflict reload to existing dialog, with localized errors and busy protection; keep draft mounted on failed save.
- [x] Pass NEXT_PUBLIC_EXPENSE_EDITING_ENABLED through Docker ARG/ENV and document build-time behavior and backend PR dependency.
- [x] Verify flag-off and flag-on tests, lint, TypeScript and production builds; exercise UI with mocked backend contract if browser is available. Review complete diff for scope and regressions.

## Verification result

- 53 Node tests pass; lint and TypeScript checks pass.
- Chromium with the flag enabled: saved fields, cancel, successful revision, conflict draft retention, explicit reload, fractional shares, custom rate and all-zero rounded custom conversion verified against the backend PR request/response contract.
- Chromium with the flag disabled: Edit absent and no revision requests.
- Production build attempted with both flag values; both blocked by the existing Google Fonts network restriction (Roboto fetch), not declared successful. No live backend deployment integration was claimed.
- Independent review found one custom-rate rounding edge case; two regression tests reproduced it, then passed after fixing saved-amount preservation and the form's missing-rate handling.
