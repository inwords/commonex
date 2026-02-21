import {TextFieldElement} from 'react-hook-form-mui';

export const ExpenseRefundAmountInput = () => {
  return <TextFieldElement name={'amount'} label={'Сумма возврата'} required type="number" />;
};
