import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {SelectUserList} from '@/3-widgets/SelectUserList/SelectUserList';
import {EventTabs} from '@/3-widgets/EventTabs/EventTabs';
import {EventHeader} from '@/3-widgets/EventHeader';
import {Box} from '@mui/material';
import {useEffect, useRef} from 'react';
import {Navigate, useLocation, useParams} from 'react-router';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {userStore} from '@/5-entities/user/stores/user-store';
import {observer} from 'mobx-react-lite';
import {ROUTES} from '@/6-shared/routing/constants';
import {useSearchParams} from 'react-router-dom';
import {eventService} from '@/5-entities/event/services/event-service';
import {currencyService} from '@/5-entities/currency/services/currency-service';
import {eventStore} from '@/5-entities/event/stores/event-store';

export const EventPage = observer(() => {
  const {id} = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigatedFromMainForm = location.state === 'navigateFromMainForm';
  const canOpenPage = id && (token || navigatedFromMainForm);

  const lastEntry = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!canOpenPage || !id || lastEntry.current === location.key) {
      return;
    }

    lastEntry.current = location.key;
    if (!navigatedFromMainForm && token) {
      analyticsService.track({name: 'invitation_opened'});
      void currencyService.fetchCurrencies();

      const params: {pinCode?: string; token?: string} = {};

      if (token) {
        params.token = token;
      }

      const fn = async () => {
        await eventService.getEventInfo(id, params, 'token');

        if (eventStore.currentEvent?.pinCode) {
          void expenseService.fetchExpenses(id, eventStore.currentEvent.pinCode);
        }
      };

      void fn().catch(() => {});
    } else {
      if (eventStore.currentEvent?.pinCode) {
        void expenseService.fetchExpenses(id, eventStore.currentEvent.pinCode);
      }
    }
  }, [canOpenPage, id, navigatedFromMainForm, token, location.key]);

  if (!canOpenPage) {
    return <Navigate to={ROUTES.Main} />;
  }

  return (
    <Box sx={{pt: 7}}>
      <EventHeader />

      <SelectUserList />
      {userStore.currentUser && <EventTabs />}
    </Box>
  );
});
