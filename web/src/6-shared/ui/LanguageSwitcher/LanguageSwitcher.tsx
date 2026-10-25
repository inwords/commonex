import {ToggleButton, ToggleButtonGroup} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {languageStore} from '@/6-shared/i18n/languageStore';
import type {Language} from '@/6-shared/i18n/languageStore';
import {useContent} from '@/6-shared/i18n/useContent';

export const LanguageSwitcher = observer(() => {
  const content = useContent().Settings;
  const handleChange = (_: React.MouseEvent, value: Language | null) => {
    if (value) {
      languageStore.setLanguage(value);
    }
  };

  return (
    <ToggleButtonGroup
      aria-label={content.language}
      value={languageStore.language}
      exclusive
      onChange={handleChange}
      size="small"
    >
      <ToggleButton value="ru" sx={{px: 1.5, py: 0.5, fontSize: '0.75rem'}}>
        RU
      </ToggleButton>
      <ToggleButton value="en" sx={{px: 1.5, py: 0.5, fontSize: '0.75rem'}}>
        EN
      </ToggleButton>
    </ToggleButtonGroup>
  );
});
