'use client';

import {useEffect} from 'react';
import {useColorScheme} from '@mui/material/styles';
import {IconButton} from '@mui/material';
import {DarkMode, LightMode} from '@mui/icons-material';

export function ThemeSwitcher() {
  const {mode, setMode, systemMode} = useColorScheme();

  useEffect(() => {
    if (mode === 'system' && systemMode) {
      setMode(systemMode);
    }
  }, [mode, systemMode, setMode]);

  const resolvedMode = mode === 'system' ? systemMode : mode;

  const handleToggle = () => {
    setMode(resolvedMode === 'dark' ? 'light' : 'dark');
  };

  return (
    <IconButton onClick={handleToggle} size="small">
      {resolvedMode === 'dark' ? <LightMode fontSize="small" /> : <DarkMode fontSize="small" />}
    </IconButton>
  );
}
