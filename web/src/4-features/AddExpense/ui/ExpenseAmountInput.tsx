import {TextFieldElement} from 'react-hook-form-mui';

interface Props {
  disabled?: boolean;
}

export const ExpenseAmountInput = ({disabled}: Props) => {
  return <TextFieldElement name={'amount'} label={'Сумма траты'} required disabled={disabled} type="number" />;
};
