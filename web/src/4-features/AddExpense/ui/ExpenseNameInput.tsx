import {TextFieldElement} from 'react-hook-form-mui';

interface Props {
  disabled?: boolean;
}

export const ExpenseDescriptionInput = ({disabled}: Props) => {
  return <TextFieldElement name={'description'} label={'Описание траты'} required disabled={disabled} />;
};
