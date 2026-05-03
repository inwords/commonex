import {RadioButtonGroup} from 'react-hook-form-mui';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
}

export const SplitOptions = ({disabled}: Props) => {
  const content = useContent();
  const c = content.AddExpense.splitOption;

  return (
    <RadioButtonGroup
      label={c.label}
      name="splitOption"
      row
      onChange={(v) => {
        expenseService.setSplitOption(v as '1' | '2' | '3');
      }}
      options={[
        {id: '1', label: c.equal},
        {id: '2', label: c.manual},
        {id: '3', label: c.percentage},
      ]}
      disabled={disabled}
    />
  );
};
