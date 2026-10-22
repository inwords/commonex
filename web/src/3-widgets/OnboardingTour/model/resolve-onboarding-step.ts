import type {OnboardingContext} from './onboarding-context';
import type {OnboardingStep} from './onboarding-step';

export const resolveOnboardingStep = (context: OnboardingContext): OnboardingStep | null => {
  if (context.page === 'main') {
    return context.createEventOpen
      ? {stage: 1, slot: 'create-event-guide', copy: 'createEvent'}
      : {stage: 1, target: 'entry-actions', copy: 'entry'};
  }
  if (context.page !== 'event') {
    return null;
  }
  if (!context.hasUser) {
    return context.canSelectUser ? {stage: 2, target: 'select-user', copy: 'selectUser'} : null;
  }
  if (context.expenseOpen) {
    return {stage: 4, slot: 'expense-guide', copy: context.expenseHint};
  }
  if (context.expenseSaved) {
    return context.tab === 2 ? null : {stage: 5, target: 'view-debts', copy: 'debts'};
  }
  return context.tab === 0 || context.tab === 1
    ? {stage: 3, target: 'add-expense', copy: 'addExpense'}
    : {stage: 3, target: 'expenses-tab', copy: 'openExpenses'};
};
