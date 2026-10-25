import {useId, useState} from 'react';
import {Box, IconButton, NoSsr, Popover, Stack, Tooltip, Typography} from '@mui/material';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import CloseIcon from '@mui/icons-material/Close';
import {observer} from 'mobx-react-lite';
import {LanguageSwitcher} from '@/6-shared/ui/LanguageSwitcher/LanguageSwitcher';
import {ThemeSwitcher} from '@/6-shared/ui/ThemeSwitcher/ThemeSwitcher';
import {OnboardingRestart} from '@/6-shared/ui/OnboardingGuide/OnboardingRestart';
import {useContent} from '@/6-shared/i18n/useContent';
import {AnalyticsConsent} from '@/3-widgets/AnalyticsConsent/AnalyticsConsent';

export const AppSettings = observer(() => {
  const content = useContent().Settings;
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const id = useId();
  const titleId = `${id}-title`;
  const open = Boolean(anchor);

  return (
    <NoSsr>
      <Box sx={{position: 'fixed', top: 12, right: 12, zIndex: 1400}}>
        <Stack direction="row" alignItems="center" gap={1}>
          <OnboardingRestart />
          <Tooltip describeChild title={content.title}>
            <IconButton
              size="small"
              aria-label={content.title}
              aria-haspopup="dialog"
              aria-expanded={open}
              aria-controls={open ? id : undefined}
              onClick={(event) => setAnchor(open ? null : event.currentTarget)}
            >
              <SettingsOutlinedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      </Box>
      <Popover
        open={open}
        anchorEl={anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}
        transformOrigin={{vertical: 'top', horizontal: 'right'}}
        slotProps={{
          paper: {
            id,
            role: 'dialog',
            'aria-labelledby': titleId,
            sx: {width: 340, maxWidth: 'calc(100vw - 32px)', p: 2.5, mt: 1, borderRadius: 3},
          },
        }}
      >
        <Stack spacing={2}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Typography id={titleId} component="h2" variant="h6">
              {content.title}
            </Typography>
            <IconButton size="small" aria-label={content.close} onClick={() => setAnchor(null)}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>
          <ThemeSwitcher />
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
            <Typography variant="body2">{content.language}</Typography>
            <LanguageSwitcher />
          </Stack>
          <AnalyticsConsent />
        </Stack>
      </Popover>
    </NoSsr>
  );
});
