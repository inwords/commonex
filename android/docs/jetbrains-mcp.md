# JetBrains MCP for Android (KMM)

This document explains how JetBrains MCP is expected to work for the CommonEx Android (KMM) project and how to verify that it is connected.

## Scope

JetBrains MCP is used against the Android IDE project at `<repository_root>/android`.

It is not expected to expose the whole repository root as a single IDE project. For this repo, MCP access is scoped to the Android project.

## What “working” looks like

JetBrains MCP is working when direct JetBrains IDE tools can query the Android project successfully.

Known-good checks from this repo:

- `list_directory_tree` can read the Android project structure
- `get_run_configurations` returns Android run/debug or test configurations

The MCP resource registry may still be empty. An empty `list_mcp_resources` result does not by itself mean JetBrains MCP is broken if direct JetBrains tools work.

## Prerequisites

- Open the Android project in a JetBrains IDE, typically Android Studio or IntelliJ IDEA
- Open the project root as `android/`, not the repository root
- Keep the IDE running while the MCP client is trying to use JetBrains tools
- Let the IDE finish indexing before relying on symbol-aware or inspection-heavy actions

If your agent session runs under WSL, the workspace path may appear as `/mnt/c/<path_to_repository_root>`, but the JetBrains MCP project path should still match the Windows IDE project path.

## Quick verification flow

Use a small progression instead of starting with builds or refactors.

1. Call `list_directory_tree` on the project root
2. Call `get_run_configurations`
3. If needed, call `search_in_files_by_text` or `get_file_text_by_path` on a known file

If these succeed, JetBrains MCP is usable for normal Android project work.

## Typical tools that should work

- Project queries: `get_project_modules`, `get_project_dependencies`, `list_directory_tree`
- Navigation and search: `search_in_files_by_text`, `search_in_files_by_regex`, `open_file_in_editor`
- File diagnostics: `get_file_problems` (very important), `get_symbol_info`
- Refactoring and edits: `rename_refactoring`, `replace_text_in_file`, `reformat_file`
- Validation and execution: `execute_terminal_command`, `get_run_configurations`, `execute_run_configuration`, `build_project`

## Validation guidance for this repo

The Android project follows an MCP-first validation policy in [android/AGENTS.md](../AGENTS.md).

When MCP is available:

- Prefer JetBrains MCP tools for project-aware queries and inspections
- Prefer `execute_terminal_command` for Gradle validation run from the IDE context

When MCP is unavailable or limited:

- Fall back to normal CLI or Gradle commands
- Record that MCP was unavailable and note the fallback in the task report

## Common failure modes

### No project found

Cause:

- The IDE does not have the Android project open
- The wrong project path is being passed

Fix:

- Open `<repository_root>/android` in the IDE
- Retry the MCP call with that exact project path

### Empty MCP resources

Cause:

- The resource registry is not populated

Fix:

- Try direct JetBrains tools anyway
- Treat direct tool success as the real health check

### Search or inspections are incomplete

Cause:

- IDE indexing is still in progress

Fix:

- Wait for indexing to complete
- Retry symbol, inspection, or search actions
