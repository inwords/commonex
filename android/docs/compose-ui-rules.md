# Compose Material 3 UI/UX Rules (Strict)

For any Android Compose UI change (new or edited pane/screen/dialog/bottom sheet), all items below are required and blocking:

1. **Screen Anatomy and Structure:** Full-screen surfaces must use `Scaffold` and place top bar/actions/snackbar through scaffold slots.
2. **Insets and Edge-to-Edge Safety:** Apply scaffold/system/IME insets once at the content root. No clipped content under bars, cutouts, gesture areas, or keyboard.
3. **Guardrails and Grid:** Use Material mobile guardrails and consistent spacing rhythm (default side margins around `16dp`, layout spacing on an `8dp` grid, smaller adjustments on `4dp`).
4. **Alignment and Grouping:** Keep related content/actions visually grouped via spacing, cards, and dividers; maintain consistent horizontal alignment.
5. **Content Hierarchy:** Keep section structure clear (title/supporting text/content/actions) and avoid dense, ungrouped controls.
6. **Primary Action Hierarchy:** Keep one dominant primary action per surface. Destructive actions must be secondary unless in a confirmation dialog.
7. **Navigation and Action Placement:** Put global/screen actions in app bars, primary task actions in FAB/primary buttons, and infrequent actions in overflow or secondary surfaces.
8. **Adaptive Layouts:** Use window size classes/canonical adaptive patterns for layout decisions (single-pane on compact; supporting pane/list-detail where appropriate on larger sizes).
9. **Lazy vs Scroll Containers:** Use `LazyColumn` (or other lazy containers) for dynamic/unbounded collections. Use `verticalScroll` only for bounded short forms/details.
10. **Overlay Semantics:** Use dialog for confirmation/critical interruption, and bottom sheet for contextual details/actions.
11. **State Coverage:** Define and handle `Loading`/`Success`/`Error`/`Empty` states where applicable. No silent blank states.
12. **Accessibility Semantics:** Interactive icons and controls must have meaningful semantics (including `contentDescription` where needed).
13. **Interaction Feedback:** Long-running actions must show progress and prevent duplicate taps while in progress.
14. **Preview and Testability:** Complex panes must have previews and stable test tags/selectors for critical actions.
15. **Typography Roles:** Use `MaterialTheme.typography` role styles (`display`, `headline`, `title`, `body`, `label`) instead of arbitrary text styles.
16. **Type Scale Discipline:** Avoid hard-coded `sp` sizes and ad-hoc line heights/letter spacing unless required for a documented one-off design reason.
17. **Readable Font Choices:** Prefer app theme font families optimized for UI readability; avoid decorative fonts in core task flows.
18. **Color Role Usage:** Use `MaterialTheme.colorScheme` semantic roles (`primary`, `onPrimary`, `surface`, `onSurface`, etc.) and avoid raw hex colors in feature UI.
19. **Contrast and Meaning:** Preserve readable contrast and never rely on color alone to communicate critical state/action meaning.
20. **Surface Hierarchy:** Use surface/container roles (`surface`, `surfaceContainer*`) to express elevation and grouping, not ad-hoc background tints.
21. **Shape Tokens:** Use `MaterialTheme.shapes` (or M3 shape scale tokens) for corner radii; avoid arbitrary radius values unless design-reviewed.
22. **Component Shape Mapping:** Respect M3 default shape intent (e.g., chips/buttons/cards/text fields) and document intentional overrides.
23. **Border Semantics:** Use borders mainly for outlined/medium-emphasis components (for example, outlined buttons/cards/text fields), not as decorative noise.
24. **Elevation Strategy:** Prefer tonal elevation and component defaults; add shadow elevation only when depth separation is necessary.
25. **Shadow Restraint:** Keep shadows subtle and consistent; avoid stacking multiple custom shadows that reduce clarity or legibility.
26. **Minimum Touch Targets:** Interactive controls must remain at least `48dp x 48dp` touch size.
27. **Motion Purpose and Control:** Use animation to support comprehension (state/visibility/layout transitions), not decoration. UX must remain clear when system animation scale is reduced or disabled.
28. **Accessibility Traversal Order:** Keep logical reading order by default; when needed, explicitly control order with semantics (`isTraversalGroup`, `traversalIndex`) so screen-reader traversal matches visual intent.
29. **Font Scaling Robustness:** UI must remain usable at high font scales (including Android nonlinear scaling up to 200%): no clipped/overlapped critical text and no blocked primary actions.
30. **Localization in Compose:** Do not hardcode user-facing strings in UI code; use resource APIs (`stringResource`, `pluralStringResource`) and keep default resources complete.
31. **RTL and Pseudolocale Verification:** Validate key screens with pseudolocales (including RTL pseudolocale) to catch truncation, mirroring, and direction issues before merge.
32. **Text Input Keyboard Semantics:** For each text input, intentionally set keyboard behavior (`keyboardType`, `imeAction`, `capitalization`, `autoCorrect`) according to task semantics.
33. **Input Constraints and Formatting:** Prefer text input transformations for constraints/formatting (length, allowed characters, output formatting) over ad-hoc post-processing.
34. **Adaptive Layout Decisions:** Use window size classes for layout decisions (not device type checks); support canonical adaptive patterns (for example list-detail / supporting pane) on larger widths.
35. **Lazy List Performance Contracts:** For dynamic lists, provide stable item keys and `contentType` where relevant to maximize reuse and reduce recomposition cost.
36. **Compose Stability and Recomposition Hygiene:** Keep expensive work out of composition (`remember`, `derivedStateOf`, ViewModel precomputation), and favor stable/immutable UI models to reduce unnecessary recompositions.
37. **Composable API Semantics:** For reusable composables, prefer semantic API parameters (`isEmphasized`, `variant`, `enabled`) over visual implementation parameters (`TextStyle`, `Color`, raw typography tokens), unless a visual parameter is explicitly
    required by a shared design-system contract.
38. **Modifier-First Layout Extensibility:** For reusable composables, expose a `modifier: Modifier = Modifier` and use it for spacing/placement overrides at call sites; avoid bespoke spacing params such as `topPadding` on component APIs.
39. **Spacing Token Discipline:** Use standard 4dp-grid spacing tokens only (`4/8/12/16/24/32`, plus `48` for touch-target-related sizing). Avoid ad-hoc spacing values (for example `10dp`, `14dp`, `18dp`) unless explicitly justified in a nearby code comment.
40. **UX Scope Control:** During UI polish/refactor tasks, do not add new controls/actions (for example extra close buttons, additional menus, or secondary actions) unless explicitly requested or required to satisfy an existing acceptance criterion.

Allowed exceptions are only explicit domain constraints where the rule does not apply (for example, no empty state by domain model). Document the reason in code comments when using an exception.
