---
name: update-dependency-batch
description: Use when updating dependency or container versions across CommonEx projects and the work should start with internet verification, release-note gathering, a user selection gate, and then implementation.
---

# Update Dependency Batch

## Overview

Use this skill to run the CommonEx dependency-update workflow without skipping discovery. The job is split into four mandatory stages: discover versions, gather release notes, stop and ask the user which bundles to update, then apply only the selected updates.

## Required Workflow

1. **Check current available versions on the internet**
   - Use official or primary sources first: vendor release pages, changelogs, package registries, Docker image tags, and official compatibility matrices.
   - Record the exact source URL for every proposed update bundle.
   - If the current repo version is pre-release, another pre-release is acceptable only when it is not a lower stability tier.

2. **Gather release notes**
   - Collect notes for every candidate version jump.
   - Prefer official release notes or changelogs over third-party summaries.
   - Extract the changes that matter for this repo: deprecations, breaking changes, new runtime requirements, config changes, build-tool changes, and useful features.

3. **Stop and prompt the user**
   - Present a numbered list of candidate updates or the smallest valid update bundles.
   - One number per independently selectable bundle.
   - For each numbered item, include:
     - current version and proposed version
     - why it is bundled
     - concise release-note summary
     - source links
   - Do not edit version files before the user selects bundles.

4. **Apply selected updates**
   - Update only the bundles chosen by the user.
   - Use new APIs and useful changes when they clearly improve the touched code.
   - Refactor deprecated usages introduced or exposed by the upgrade.
   - If a warning cannot be resolved confidently, stop and ask instead of suppressing it.
   - Keep the worktree no dirtier than it was before the update work started.

## Stability Policy

Treat stability tiers as:
- stable
- rc
- beta
- alpha
- preview, eap, snapshot, dev

Rules:
- never suggest moving from a higher tier to a lower tier
- stable must not be moved to pre-release unless the user explicitly asks
- pre-release may move sideways or upward in stability, for example beta to newer beta, rc, or stable
- if an update is omitted, provide a concrete reason; do not silently filter it out

## Recursive Release Notes

Some upstream notes are only a pointer, for example:
- "updated sentry-java from X to Y"
- "bumped OpenTelemetry SDK"
- "updated embedded nginx dependency"

When that happens:
- follow the referenced dependency release notes recursively
- continue until you reach meaningful notes, a clear compatibility matrix, or a cycle/noise boundary
- keep a short chain record such as `sentry-android -> sentry-java`
- surface the rolled-up impact in the numbered proposal, not just the shallow note

Stop recursion when:
- you hit a cycle
- the remaining notes are clearly repetitive
- the dependency is purely internal and has no separate public notes
- the chain no longer changes upgrade risk for this repo

## Output Rules

- Be concise and precise.
- Do not duplicate the same release-note detail in multiple sections.
- Do not guess when the upstream source is unclear; mark it and ask.
- If multiple projects are involved, keep one combined numbered menu grouped by project.

## Project Routing

- Use `update-android-dependencies` for `android/`
- Use `update-backend-dependencies` for `backend/`
- Use `update-web-dependencies` for `web/`
- Use `update-infra-dependencies` for `infra/`

The shared skill owns the workflow. Project skills own the manifests, bundle rules, and validation commands.
