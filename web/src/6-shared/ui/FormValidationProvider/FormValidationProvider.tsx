import type {PropsWithChildren} from 'react';
import {FormErrorProvider} from 'react-hook-form-mui';
import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';

export const FormValidationProvider = observer(({children}: PropsWithChildren) => {
  const content = useContent().Validation;

  return (
    <FormErrorProvider onError={(error) => (error.type === 'required' ? content.required : error.message)}>
      {children}
    </FormErrorProvider>
  );
});
