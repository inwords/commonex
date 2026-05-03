import {Stack, Typography} from '@mui/material';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  pinCode: string;
  hidden: boolean;
  onToggle: VoidFunction;
}

export const PinCodeDisplay = ({pinCode, hidden, onToggle}: Props) => {
  const content = useContent();

  return (
    <Stack
      justifyContent={'center'}
      direction={'row'}
      spacing={1}
      style={{cursor: 'pointer'}}
      onClick={onToggle}
    >
      <Typography
        style={{
          userSelect: 'none',
        }}
        variant="subtitle1"
        marginBottom={'20px'}
      >
        {content.PinCode.label}
      </Typography>

      <Typography
        variant="subtitle1"
        style={{
          filter: hidden ? 'blur(10px)' : undefined,
          transition: 'all .4s ease',
          userSelect: 'none',
        }}
      >
        {pinCode}
      </Typography>
    </Stack>
  );
};
