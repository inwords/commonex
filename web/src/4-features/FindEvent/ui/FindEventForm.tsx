import {FormContainer} from 'react-hook-form-mui';
import {EventInput} from '@/4-features/FindEvent/ui/EventInput';
import {FindEventButton} from '@/4-features/FindEvent/ui/FindEventButton';
import {Box, Stack} from '@mui/material';
import {EventPinCodeInput} from '@/5-entities/event/ui/EventPinCodeInput';
import {eventService} from '@/5-entities/event/services/event-service';
import {useNavigate} from 'react-router';
import {useContent} from '@/6-shared/i18n/useContent';
import {observer} from 'mobx-react-lite';
import {ROUTES} from '@/6-shared/routing/constants';

type FindEventFormValues = {
  eventId: string;
  pinCode: string;
};

export const FindEventForm = observer(() => {
  const content = useContent();
  const navigate = useNavigate();

  return (
    <Box display="flex" justifyContent="center" marginTop={'20px'}>
      <FormContainer<FindEventFormValues>
        onSuccess={async (d) => {
          try {
            await eventService.getEventInfo(d.eventId, {pinCode: d.pinCode}, 'manual');
          } catch {
            return;
          }

          navigate(ROUTES.Event(d.eventId), {state: 'navigateFromMainForm'});
        }}
      >
        <Stack direction="column" spacing={2} minWidth={300}>
          <EventInput />

          <EventPinCodeInput />
          <Box component="p" sx={{fontSize: 12, color: 'text.secondary', maxWidth: 300}}>
            {content.Onboarding.joinHint}
          </Box>

          <FindEventButton />
        </Stack>
      </FormContainer>
    </Box>
  );
});
