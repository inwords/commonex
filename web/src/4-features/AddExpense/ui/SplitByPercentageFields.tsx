import {TextFieldElement, useFormContext} from 'react-hook-form-mui';
import {Button, Stack, Typography} from '@mui/material';
import {SelectUser} from '@/5-entities/user/ui/SelectUser';
import {useContent} from '@/6-shared/i18n/useContent';
import React from 'react';

interface PercentageSplitDraft {
  userId?: string;
  amount?: number;
}

interface Props {
  fields: Array<{id: string}>;
  append: (value: PercentageSplitDraft) => void;
  remove: (index: number) => void;
  disabled?: boolean;
}

export const SplitByPercentageFields = ({fields, append, remove, disabled = false}: Props) => {
  const {watch} = useFormContext<{splitInformation: Array<PercentageSplitDraft>}>();
  const content = useContent();
  const c = content.AddExpense.splitByPercentage;
  const values = watch('splitInformation') ?? [];
  const total = values.reduce((sum, v) => sum + (Number(v?.amount) || 0), 0);
  const isValid = total === 100;

  return (
    <>
      {fields.map((field, index) => (
        <React.Fragment key={field.id}>
          <TextFieldElement
            name={`splitInformation.${index}.amount`}
            label={c.percentLabel}
            required
            disabled={disabled}
            type="number"
            slotProps={{htmlInput: {min: 1, max: 100, step: 1}}}
            rules={{
              validate: (v: string) => Number.isInteger(Number(v)) || c.onlyIntegers,
            }}
          />
          <SelectUser label={c.participantLabel} name={`splitInformation.${index}.userId`} disabled={disabled} required />
          {fields.length > 1 && (
            <Button onClick={() => remove(index)} variant="text" color="error" size="small" disabled={disabled}>
              {c.remove}
            </Button>
          )}
        </React.Fragment>
      ))}

      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="body2" color={isValid ? 'text.secondary' : 'error'}>
          {total}% / 100%
        </Typography>
        <Button onClick={() => append({})} variant="outlined" disabled={disabled}>
          {c.addParticipant}
        </Button>
      </Stack>
    </>
  );
};
