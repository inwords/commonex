# Database Research Log Entry Template

Use this template for each entry in `android/docs/database-research-log.md`.
Keep field order unchanged for consistency.

```markdown
## YYYY-MM-DD: <Research title>

- Scope: <what is being compared/measured>
- Hypothesis: <expected outcome before running>
- Environment: <device/emulator + relevant runtime profile>
- Method: <test style, data setup, benchmark approach>
- Variants: <A vs B variants>
- Runs: <number of runs>
- Results (run-level):
- Run 1: <numbers>
- Run 2: <numbers>
- Run 3: <numbers>
- Results (aggregate): <mean/median summary and deltas>
- Decision: <accept/reject/partial + why>
- Recommendation: <what to do in runtime/code + when to re-test>
- Artifacts: <logs/files/paths or "none retained">
- Cleanup status: <what benchmark code/docs were removed or retained>
```
