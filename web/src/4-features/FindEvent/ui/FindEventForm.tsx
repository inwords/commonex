import {FormContainer} from 'react-hook-form-mui';
import {EventInput} from '@/4-features/FindEvent/ui/EventInput';
import {FindEventButton} from '@/4-features/FindEvent/ui/FindEventButton';
import {Box, Stack} from '@mui/material';
import {EventPinCodeInput} from '@/5-entities/event/ui/EventPinCodeInput';
import {eventService} from '@/5-entities/event/services/event-service';
import {useNavigate} from 'react-router';
import {ROUTES} from '@/6-shared/routing/constants';

type FindEventFormValues = {
  eventId: string;
  pinCode: string;
};

export const FindEventForm = () => {
  const navigate = useNavigate();

  return (
    <Box display="flex" justifyContent="center" marginTop={'20px'}>
      <FormContainer<FindEventFormValues>
        onSuccess={async (d) => {
          await eventService.getEventInfo(d.eventId, {pinCode: d.pinCode});

          navigate(ROUTES.Event(d.eventId), {state: 'navigateFromMainForm'});
        }}
      >
        <Stack direction="column" spacing={2} minWidth={300}>
          <EventInput />

          <EventPinCodeInput />

          <FindEventButton />
        </Stack>
      </FormContainer>
    </Box>
  );
};
