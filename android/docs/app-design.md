# CommonEx Mobile App Design

Use these principles for new Android and iOS screens and mobile design refinements. CommonEx should feel warm, social, and dependable while making shared expenses easy to manage.

## Character with restraint

Give each screen a clear visual hierarchy and an intentional focal point. Express character through the app's typography, tonal surfaces, shapes, and purposeful icons, using the shared theme and Material 3 Expressive components.

Use theme-driven motion where it clarifies component state changes, such as expansion. The resulting state must remain clear with reduced motion.

Simplicity reduces competing messages and unnecessary decisions while retaining composition and personality. Compact forms still need grouping, rhythm, and emphasis. Keep expressive headings or illustrations proportionate to the task; inputs and financial information need calm, readable treatment. Use recognizable action icons and genuine brand assets for their respective purposes.

## Color and identity

Keep Android dynamic colors enabled where supported. Define a warm branded palette in light and dark variants for iOS and the Android fallback theme. CommonEx's visual identity should remain recognizable through typography, shapes, composition, and motion across palettes.

Use semantic color roles from the shared theme for backgrounds, tonal containers, and accents, with their corresponding text and icon colors. Build hierarchy through deliberate tonal contrast and selective emphasis; changing the palette should preserve the relationships between surfaces and actions. Implement color treatment through the shared theme and component roles rather than fixed colors scattered across screens.

Prototype and review new screen designs in the warm brand palette, a cool dynamic palette, and dark mode. Map prototype colors to the same semantic roles used by the app so the intended hierarchy and character carry into implementation. A design must work across these palettes without relying on the prototype's exact background hue.

## One clear task

Make the screen's purpose and primary action apparent together. Keep related fields, supporting information, and actions visually connected. Use spacing to distinguish groups and establish rhythm; size flexible gaps so the action still belongs to its content.

Align visible labels and icons with the surrounding content. Account for a component's internal padding, especially on text buttons, while preserving its touch target. Judge alignment by the rendered content as well as the component bounds.

Keep consent text and its legal links close enough to read as one group. Separate that group from reassurance copy and application actions. Compact visible spacing must retain accessible touch areas without overlap between interactive targets.

Draft a prototype before implementing each new or redesigned screen; align it with the current app's inputs, navigation, states, and capabilities. Place derived summaries beside the control they explain, and make each label or summary add distinct information.

Give secondary actions less emphasis. Use progress steps only for actual stages in the same task. Explanations earn their space by helping someone decide or complete an unfamiliar action. Prefer useful labels and examples over repeating the title or describing a later screen.

Use the shared 56dp minimum size for standalone screen and sheet action buttons, with matching typography, icon size, and padding. Express action emphasis through filled, tonal, outlined, or text styling rather than different sizes. Allow buttons to grow with wrapped or scaled text. Inline links, dialog actions, app-bar controls, and floating actions retain their component-specific sizing.

## Calm forms

Keep required inputs easy to find. Offer optional inputs through clearly labeled, discoverable actions when revealing them progressively reduces initial effort. Preserve a straightforward way to add or edit them.

Match controls to decisions: compact selectors for simple choices, text fields for entry, and native interaction conventions. Keyboard actions, focus order, and submission must form a coherent path. Use a full screen for a sustained task; use a sheet for a short contextual interaction.

## Visible, recoverable state

Show progress where the action occurs when the wait is perceptible; a brief local save does not by itself justify a loading phase or disabling the whole form. Prevent duplicate submissions. Preserve the draft during requests and provide a clear retry path for recoverable operation failures.

Attach field-validation messages to the field's supporting text and error state. Show request failures at form level when they concern the overall operation. Enabled actions must agree with visible validation. Empty states should explain the situation and offer the next useful action.

## Human language

Use friendly product language: “group” and “people” where natural, and specific roles where the distinction helps. Keep code terminology aligned with the [domain glossary](../../docs/domain.md).

Use “invite link” consistently in English UI copy. Write idiomatically in each locale rather than translating English phrasing literally.

Use short, concrete labels. Headings, labels, and short standalone phrases have no terminal full stop; prose uses normal punctuation. Use the same separator for equivalent inline lists. Group localized resources by screen and purpose, with corresponding locale ordering.

## Adaptive composition

Use normal phone sizes and default text settings as the primary composition and visual-review target. Retain narrow-window and large-text previews, and use targeted checks to verify usability in those configurations.

Adapt the composition to available space while preserving reading order and task hierarchy. Bound form widths on large windows; let content reflow and scroll on narrow or short ones. Keep required fields, optional controls, errors, and the primary action reachable with the keyboard open and at 200% text scaling.

Let card and row heights follow their content: supporting text such as a sync-status note can legitimately make an entry taller. Swipe actions match their own card's height, and revealing them preserves that height.

Choose text-dependent minimum widths and wrapping thresholds from representative content measured in the actual text style, including currency suffixes and component spacing. Scale the text budget through font metrics while keeping control padding unscaled. Use stable reference content so typing does not continually change the layout threshold; allow longer names to wrap, larger values to scroll, and fields to stack when the combined minimums do not fit.

Before accepting a design, inspect its hierarchy, character, action placement, and all relevant states on compact and expanded layouts. Review implemented screens on a device or emulator, including keyboard and large-text behavior. Use the [Compose UI checklist](compose-ui-rules.md) for Android engineering and accessibility requirements.
