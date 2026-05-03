import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

export const EventInput = () => {
  const content = useContent();
  return <TextFieldElement name={'eventId'} label={content.FindEvent.eventIdInput} required />;
};
