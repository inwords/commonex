import assert from 'node:assert/strict';
import test from 'node:test';
import {resolveOnboardingStep} from '../src/3-widgets/OnboardingTour/model/resolve-onboarding-step.ts';

const base = {
  page: 'event' as 'main' | 'event' | 'other',
  hasUser: true,
  canSelectUser: true,
  tab: 0,
  expenseSaved: false,
  createEventOpen: false,
  expenseOpen: false,
  expenseHint: 'amount' as 'amount' | 'owner' | 'split' | 'save',
};

test('starts at entry actions and follows the create-event form when opened', () => {
  assert.equal(resolveOnboardingStep({...base, page: 'main'})?.target, 'entry-actions');
  assert.equal(resolveOnboardingStep({...base, page: 'main', createEventOpen: true})?.slot, 'create-event-guide');
  assert.equal(resolveOnboardingStep({...base, page: 'other'}), null);
});

test('waits for loaded participants before guiding user selection', () => {
  assert.equal(resolveOnboardingStep({...base, hasUser: false, canSelectUser: false}), null);
  assert.equal(resolveOnboardingStep({...base, hasUser: false})?.target, 'select-user');
});

test('guides to expenses from other tabs and follows focus inside the expense form', () => {
  assert.equal(resolveOnboardingStep(base)?.target, 'add-expense');
  assert.equal(resolveOnboardingStep({...base, tab: 3})?.target, 'expenses-tab');
  const step = resolveOnboardingStep({...base, expenseOpen: true, expenseHint: 'owner'});
  assert.equal(step?.slot, 'expense-guide');
  assert.equal(step?.copy, 'owner');
});

test('only a saved expense unlocks debts and an open form takes precedence', () => {
  assert.equal(resolveOnboardingStep({...base, expenseSaved: true})?.target, 'view-debts');
  assert.equal(resolveOnboardingStep({...base, expenseSaved: true, expenseOpen: true})?.slot, 'expense-guide');
  assert.equal(resolveOnboardingStep({...base, expenseSaved: true, tab: 2}), null);
});
