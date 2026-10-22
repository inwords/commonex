import {useEffect, useRef} from 'react';
import {Portal} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {useLocation} from 'react-router';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {userStore} from '@/5-entities/user/stores/user-store';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {useOnboarding} from '@/6-shared/services/useOnboarding';
import {onboardingService} from '@/6-shared/services/onboarding-service';
import {OnboardingGuide} from '@/6-shared/ui/OnboardingGuide/OnboardingGuide';
import {useContent} from '@/6-shared/i18n/useContent';
import {ROUTES} from '@/6-shared/routing/constants';
import {resolveOnboardingStep} from './model/resolve-onboarding-step';
import {useOnboardingSurface} from './model/use-onboarding-surface';

export const OnboardingTour = observer(() => {
  const {pathname} = useLocation();
  const content = useContent().Onboarding;
  const state = useOnboarding();
  const surface = useOnboardingSurface();
  const eventId = eventStore.currentEvent?.id;
  const path = pathname.replace(/\/$/, '') || '/';
  const page = path === ROUTES.Main ? 'main' : eventId && path === ROUTES.Event(eventId) ? 'event' : 'other';
  const tab = expenseStore.currentTab;
  const hasUser = !!userStore.currentUser;

  const lastCreatedExpense = expenseStore.lastCreatedExpense;
  const previousExpense = useRef(lastCreatedExpense);

  useEffect(() => {
    if (lastCreatedExpense && lastCreatedExpense !== previousExpense.current) {
      previousExpense.current = lastCreatedExpense;
      onboardingService.expenseSaved(lastCreatedExpense.eventId);
    }
  }, [lastCreatedExpense]);

  useEffect(() => {
    if (page === 'event' && eventId && hasUser && tab === 2) {
      onboardingService.complete(eventId);
    }
  }, [page, eventId, hasUser, tab, state.expenseEventId, state.status]);

  if (state.status !== 'active' || surface.otherDialogOpen) {
    return null;
  }
  const step = resolveOnboardingStep({
    page,
    hasUser,
    canSelectUser: userStore.users.length > 0,
    tab,
    expenseSaved: state.expenseEventId === eventId,
    ...surface,
  });
  if (!step) {
    return null;
  }
  const copy =
    step.copy === 'amount' || step.copy === 'owner' || step.copy === 'split' || step.copy === 'save'
      ? content.expense[step.copy]
      : content[step.copy];
  const guide = <OnboardingGuide key={step.slot || step.target} stage={step.stage} target={step.target} {...copy} />;
  if (!step.slot) {
    return guide;
  }
  const slot = document.querySelector<HTMLElement>(`[data-onboarding="${step.slot}"]`);
  return slot ? <Portal container={slot}>{guide}</Portal> : null;
});
