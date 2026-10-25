const onboardingSteps = [
  'entry',
  'createEvent',
  'selectUser',
  'addExpense',
  'openExpenses',
  'amount',
  'owner',
  'split',
  'save',
  'debts',
] as const;

export const eventParameters = {
  onboarding_started: {source: ['automatic', 'manual']},
  onboarding_step_viewed: {step: onboardingSteps},
  onboarding_skipped: {step: onboardingSteps},
  onboarding_completed: {},
  trip_create_opened: {},
  trip_created: {},
  trip_create_failed: {reason: ['network', 'access_denied', 'not_found', 'validation', 'server', 'unknown']},
  invitation_requested: {},
  invitation_copied: {},
  invitation_failed: {reason: ['network', 'access_denied', 'not_found', 'validation', 'server', 'unknown']},
  invitation_opened: {},
  join_started: {entry_method: ['token', 'manual']},
  join_succeeded: {entry_method: ['token', 'manual']},
  join_failed: {
    entry_method: ['token', 'manual'],
    reason: ['network', 'access_denied', 'not_found', 'validation', 'server', 'unknown'],
  },
  participant_claimed: {entry_method: ['created', 'token', 'manual']},
  expense_opened: {},
  expense_submitted: {},
  expense_failed: {reason: ['network', 'access_denied', 'not_found', 'validation', 'server', 'unknown']},
  settlement_viewed: {},
  settlement_recorded: {},
  settlement_failed: {reason: ['network', 'access_denied', 'not_found', 'validation', 'server', 'unknown']},
  store_link_clicked: {store: ['app_store', 'rustore']},
} as const;

type Schema = typeof eventParameters;
export type AnalyticsEvent = {
  [Name in keyof Schema]: keyof Schema[Name] extends never
    ? {name: Name; params?: never}
    : {
        name: Name;
        params: {
          [Key in keyof Schema[Name]]: Schema[Name][Key] extends readonly string[] ? Schema[Name][Key][number] : never;
        };
      };
}[keyof Schema];

export function safeEventParams(event: AnalyticsEvent): Record<string, string | number> | null {
  if (!Object.hasOwn(eventParameters, event.name)) {
    return null;
  }
  const result: Record<string, string | number> = {schema_version: 1, platform: 'web'};
  for (const [key, allowed] of Object.entries(eventParameters[event.name])) {
    const value = (event.params as Record<string, unknown> | undefined)?.[key];
    if (typeof value !== 'string' || !(allowed as readonly string[]).includes(value)) {
      return null;
    }
    result[key] = value;
  }
  return result;
}
