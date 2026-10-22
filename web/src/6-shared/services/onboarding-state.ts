export interface OnboardingState {
  status: 'active' | 'skipped' | 'completed';
  expenseEventId: string | null;
}
