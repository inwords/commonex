import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

export const EventNameInput = () => {
  const content = useContent();
  return <TextFieldElement name={'name'} label={content.CreateEvent.form.eventNameInput} required />;
};
