import {FormContainer} from 'react-hook-form-mui';
import {Button, Stack} from '@mui/material';

import React from 'react';
import {EventNameInput} from '@/4-features/CreateEvent/ui/EventNameInput';
import {EventUsers} from '@/4-features/CreateEvent/ui/EventUsers';
import {EventPinCodeInput} from '@/5-entities/event/ui/EventPinCodeInput';
import {useNavigate} from 'react-router';
import {ROUTES} from '@/6-shared/routing/constants';
import {SelectCurrency} from '@/5-entities/currency/ui/SelectCurrency';
import {eventService} from '@/5-entities/event/services/event-service';
import {CreateEvent} from '@/5-entities/event/types/types';

export const CreateEventForm = () => {
  const navigate = useNavigate();

  return (
    <FormContainer<CreateEvent>
      onSuccess={async (data) => {
        const eventId = await eventService.createEvent(data);

        navigate(ROUTES.Event(eventId), {state: 'navigateFromMainForm'});
      }}
    >
      <Stack spacing={2} maxWidth={600}>
        <EventNameInput />

        <EventPinCodeInput />

        <EventUsers />

        <SelectCurrency />
      </Stack>

      <Stack justifyContent="end" marginTop={'16px'}>
        <Button type={'submit'} variant="contained">
          Создать поездку
        </Button>
      </Stack>
    </FormContainer>
  );
};
