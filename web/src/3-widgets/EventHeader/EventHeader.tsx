import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {Button, Stack, Typography, Box} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {useState} from 'react';
import {userStore} from '@/5-entities/user/stores/user-store';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {eventService} from '@/5-entities/event/services/event-service';
import {notificationStore} from '@/6-shared/stores/notification-store';
import {CurrentUserBadge} from '@/6-shared/ui/CurrentUserBadge';
import {PinCodeDisplay} from '@/6-shared/ui/PinCodeDisplay';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {useContent} from '@/6-shared/i18n/useContent';
import copy from 'copy-to-clipboard';

export const EventHeader = observer(() => {
  const [shouldHidePinCode, setShouldHidePinCode] = useState(true);
  const content = useContent();

  if (!userStore.currentUser || !eventStore.currentEvent) {
    return null;
  }

  const handleChangeUser = () => {
    userStore.setCurrentUser(undefined);
  };

  const handleShareLink = async () => {
    analyticsService.track({name: 'invitation_requested'});
    try {
      if (!eventStore.currentEvent) {
        return;
      }

      const {token} = await eventService.createEventShareToken(
        eventStore.currentEvent.id,
        eventStore.currentEvent.pinCode,
      );

      const location = window.location;
      const shareUrl = `${location.origin}/event/${eventStore.currentEvent.id}?token=${encodeURIComponent(token)}&utm_source=commonex&utm_medium=referral&utm_campaign=trip_invite`;

      if (!copy(shareUrl)) {
        analyticsService.track({name: 'invitation_failed', params: {reason: 'unknown'}});
        return;
      }
      analyticsService.track({name: 'invitation_copied'});

      notificationStore.setNotification(content.EventHeader.linkCopied, 'success');
    } catch (error) {
      analyticsService.track({name: 'invitation_failed', params: {reason: analyticsService.failureReason(error)}});
    }
  };

  return (
    <>
      <CurrentUserBadge letter={userStore.currentUser.name[0]} onClick={handleChangeUser} />

      <Typography variant="h3" align="center" marginBottom={'16px'}>
        {eventStore.currentEvent.name}

        <PinCodeDisplay
          pinCode={eventStore.currentEvent.pinCode}
          hidden={shouldHidePinCode}
          onToggle={() => setShouldHidePinCode(!shouldHidePinCode)}
        />
      </Typography>

      <Box marginBottom={'16px'}>
        <Typography variant="h6" align="center" color="text.secondary">
          {content.EventHeader.totalSpent} {expenseStore.totalExpensesAmount.toFixed(2)}{' '}
          {currencyStore.getCurrencyCode(eventStore.currentEvent.currencyId)}
        </Typography>
        <Typography variant="body1" align="center" color="text.secondary">
          {content.EventHeader.youSpent} {expenseStore.currentUserSpentAmount.toFixed(2)}{' '}
          {currencyStore.getCurrencyCode(eventStore.currentEvent.currencyId)}
        </Typography>
      </Box>

      <Stack direction={'row'} justifyContent={'center'} marginBottom={'16px'}>
        <Button variant="outlined" onClick={handleShareLink}>
          {content.EventHeader.copyLink}
        </Button>
      </Stack>
    </>
  );
});
