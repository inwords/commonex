import {EventUsers} from '@/4-features/CreateEvent/ui/EventUsers';
import {FormContainer} from 'react-hook-form-mui';
import React from 'react';
import {Button, Stack} from '@mui/material';
import {UserDraft} from '@/5-entities/user/types/types';

type AddUsersToEventFormValues = {
  users: Array<UserDraft>;
};

interface Props {
  onSuccess: (data: Array<UserDraft>) => void;
}

export const AddUsersToEventForm = ({onSuccess}: Props) => {
  return (
    <FormContainer<AddUsersToEventFormValues>
      onSuccess={(data) => {
        onSuccess(data.users);
      }}
    >
      <Stack direction={'column'} spacing={2}>
        <EventUsers />

        <Button type={'submit'} variant="contained">
          Отправить
        </Button>
      </Stack>
    </FormContainer>
  );
};
