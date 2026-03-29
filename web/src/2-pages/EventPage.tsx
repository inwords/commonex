import {SelectUserList} from '@/3-widgets/SelectUserList/SelectUserList';
import {EventTabs} from '@/3-widgets/EventTabs/EventTabs';
import {EventHeader} from '@/3-widgets/EventHeader';
import {OnboardingTour} from '@/3-widgets/OnboardingTour';
import {useEffect} from 'react';
import {Navigate, useLocation, useParams} from 'react-router';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {userStore} from '@/5-entities/user/stores/user-store';
import {observer} from 'mobx-react-lite';
import {ROUTES} from '@/6-shared/routing/constants';
import {useSearchParams} from 'react-router-dom';
import {eventService} from '@/5-entities/event/services/event-service';
import {currencyService} from '@/5-entities/currency/services/currency-service';
import {EVENT_PAGE_ONBOARDING_STEPS} from '@/6-shared/constants/onboarding-steps';
import {eventStore} from '@/5-entities/event/stores/event-store';

export const EventPage = observer(() => {
  const {id} = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigatedFromMainForm = location.state === 'navigateFromMainForm';
  const canOpenPage = id && (token || navigatedFromMainForm);

  useEffect(() => {
    if (!canOpenPage || !id) {
      return;
    }

    if (!navigatedFromMainForm && token) {
      void currencyService.fetchCurrencies();

      const params: {pinCode?: string; token?: string} = {};

      if (token) {
        params.token = token;
      }

      const fn = async () => {
        await eventService.getEventInfo(id, params);

        if (eventStore.currentEvent?.pinCode) {
          void expenseService.fetchExpenses(id, eventStore.currentEvent.pinCode);
        }
      }

      void fn();
    } else {
      if (eventStore.currentEvent?.pinCode) {
        void expenseService.fetchExpenses(id, eventStore.currentEvent.pinCode);
      }
    }
  }, [canOpenPage, id, navigatedFromMainForm, token]);

  if (!canOpenPage) {
    return <Navigate to={ROUTES.Main} />;
  }

  return (
    <>
      <EventHeader />

      <SelectUserList />

      {userStore.currentUser && (
        <>
          <EventTabs />
          <OnboardingTour steps={EVENT_PAGE_ONBOARDING_STEPS} />
        </>
      )}
    </>
  );
});
