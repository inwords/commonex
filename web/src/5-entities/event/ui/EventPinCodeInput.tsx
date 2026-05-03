import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

export const EventPinCodeInput = () => {
  const content = useContent();
  return <TextFieldElement name={'pinCode'} label={content.CreateEvent.form.pinCodeInput} required />;
};
