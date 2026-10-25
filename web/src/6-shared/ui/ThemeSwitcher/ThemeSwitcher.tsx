'use client';

import {useEffect} from 'react';
import {useColorScheme} from '@mui/material/styles';
import {FormControlLabel, Switch} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';

export const ThemeSwitcher = observer(() => {
  const {mode, setMode, systemMode} = useColorScheme();
  const content = useContent().Settings;

  useEffect(() => {
    if (mode === 'system' && systemMode) {
      setMode(systemMode);
    }
  }, [mode, systemMode, setMode]);

  const resolvedMode = mode === 'system' ? systemMode : mode;

  return (
    <FormControlLabel
      label={content.darkTheme}
      labelPlacement="start"
      sx={{m: 0, justifyContent: 'space-between', '& .MuiFormControlLabel-label': {fontSize: '0.875rem'}}}
      control={
        <Switch
          checked={resolvedMode === 'dark'}
          disabled={!mode}
          onChange={(_, checked) => setMode(checked ? 'dark' : 'light')}
        />
      }
    />
  );
});
