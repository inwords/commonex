import {RadioButtonGroup} from 'react-hook-form-mui';
import {expenseService} from '@/5-entities/expense/services/expense-service';

interface Props {
  disabled?: boolean;
}

export const SplitOptions = ({disabled}: Props) => {
  return (
    <RadioButtonGroup
      label="Варинт деления траты"
      name="splitOption"
      row
      onChange={(v) => {
        expenseService.setSplitOption(v as '1' | '2');
      }}
      options={[
        {
          id: '1',
          label: 'Поровну',
        },
        {
          id: '2',
          label: 'Внести вручную',
        },
      ]}
      disabled={disabled}
    />
  );
};
