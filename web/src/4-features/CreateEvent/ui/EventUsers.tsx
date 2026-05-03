import {TextFieldElement, useFieldArray, useFormContext} from 'react-hook-form-mui';
import React, {useEffect} from 'react';
import {Button, Stack} from '@mui/material';
import {UserDraft} from '@/5-entities/user/types/types';
import {useContent} from '@/6-shared/i18n/useContent';

type EventUsersFormValues = {
  users: Array<UserDraft>;
};

export const EventUsers = () => {
  const {control, setValue} = useFormContext<EventUsersFormValues>();
  const content = useContent();

  const {fields, append} = useFieldArray({
    control,
    name: 'users',
  });

  useEffect(() => {
    setValue('users', [{name: ''}]);
  }, [setValue]);

  return (
    <Stack direction={'column'} spacing={2}>
      {fields.map((field, index) => {
        return (
          <React.Fragment key={field.id}>
            <TextFieldElement name={`users.${index}.name`} label={content.CreateEvent.form.userNameInput} required />
          </React.Fragment>
        );
      })}

      <Button onClick={() => append({name: ''})} variant={'outlined'}>
        {content.CreateEvent.form.addUser}
      </Button>
    </Stack>
  );
};
