import {EventUsers} from '@/4-features/CreateEvent/ui/EventUsers';
import {FormContainer} from 'react-hook-form-mui';
import React from 'react';
import {Button, Stack} from '@mui/material';
import {UserDraft} from '@/5-entities/user/types/types';
import {observer} from 'mobx-react-lite';
import {userStore} from '@/5-entities/user/stores/user-store';
import {useContent} from '@/6-shared/i18n/useContent';

type AddUsersToEventFormValues = {
  users: Array<UserDraft>;
};

interface Props {
  onSuccess: (data: Array<UserDraft>) => void;
}

export const AddUsersToEventForm = observer(({onSuccess}: Props) => {
  const content = useContent();

  return (
    <FormContainer<AddUsersToEventFormValues>
      onSuccess={(data) => {
        onSuccess(data.users);
      }}
    >
      <Stack direction={'column'} spacing={2}>
        <EventUsers />

        <Button type={'submit'} variant="contained" loading={userStore.isAddingUsers}>
          {content.AddUsers.submit}
        </Button>
      </Stack>
    </FormContainer>
  );
});
