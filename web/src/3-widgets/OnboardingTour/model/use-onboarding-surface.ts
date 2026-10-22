import {useState, useSyncExternalStore} from 'react';
import type {OnboardingContext} from './onboarding-context';

type Surface = Pick<OnboardingContext, 'createEventOpen' | 'expenseOpen' | 'expenseHint'> & {otherDialogOpen: boolean};
const empty: Surface = {createEventOpen: false, expenseOpen: false, expenseHint: 'amount', otherDialogOpen: false};
const serverSnapshot = JSON.stringify(empty);

const createSurfaceSource = () => {
  let hint: Surface['expenseHint'] = 'amount';
  const getSnapshot = () => {
    const createEventOpen = !!document.querySelector('[data-onboarding="create-event-guide"]');
    const expenseOpen = !!document.querySelector('[data-onboarding="expense-guide"]');
    return JSON.stringify({
      createEventOpen,
      expenseOpen,
      expenseHint: expenseOpen ? hint : 'amount',
      otherDialogOpen: !!document.querySelector('[role="dialog"]') && !createEventOpen && !expenseOpen,
    });
  };
  const subscribe = (listener: () => void) => {
    const update = () => {
      if (!document.querySelector('[data-onboarding="expense-guide"]')) {
        hint = 'amount';
      }
      listener();
    };
    const onFocus = (event: FocusEvent) => {
      if (!(event.target instanceof Element)) {
        return;
      }
      const key = event.target.closest<HTMLElement>('[data-onboarding]')?.dataset.onboarding;
      switch (key) {
        case 'expense-amount':
          hint = 'amount';
          break;
        case 'expense-owner':
          hint = 'owner';
          break;
        case 'expense-split':
          hint = 'split';
          break;
        case 'expense-save':
          hint = 'save';
          break;
      }
      update();
    };
    const observer = new MutationObserver(update);
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['data-onboarding'],
    });
    document.addEventListener('focusin', onFocus);
    return () => {
      observer.disconnect();
      document.removeEventListener('focusin', onFocus);
    };
  };
  return {getSnapshot, subscribe};
};

export const useOnboardingSurface = () => {
  const [source] = useState(createSurfaceSource);
  const snapshot = useSyncExternalStore(source.subscribe, source.getSnapshot, () => serverSnapshot);
  return JSON.parse(snapshot) as Surface;
};
