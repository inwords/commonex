export interface OnboardingStep {
  stage: number;
  target?: string;
  slot?: string;
  copy:
    | 'entry'
    | 'createEvent'
    | 'selectUser'
    | 'addExpense'
    | 'openExpenses'
    | 'debts'
    | 'amount'
    | 'owner'
    | 'split'
    | 'save';
}
