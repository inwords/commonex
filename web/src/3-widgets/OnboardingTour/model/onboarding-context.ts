export interface OnboardingContext {
  page: 'main' | 'event' | 'other';
  hasUser: boolean;
  canSelectUser: boolean;
  tab: number;
  expenseSaved: boolean;
  createEventOpen: boolean;
  expenseOpen: boolean;
  expenseHint: 'amount' | 'owner' | 'split' | 'save';
}
