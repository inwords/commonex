import {useSyncExternalStore} from 'react';
import {Button, Divider, Link, Stack, Typography} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {useContent} from '@/6-shared/i18n/useContent';
import {languageStore} from '@/6-shared/i18n/languageStore';

export const AnalyticsConsent = observer(() => {
  const content = useContent().Analytics;
  const consent = useSyncExternalStore(analyticsService.subscribeConsent, analyticsService.getConsent, () => 'unknown');

  return (
    <Stack component="section" aria-label={content.title} spacing={1.5}>
      <Divider />
      <Typography component="h3" variant="subtitle2">
        {content.title}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {content.description}{' '}
        <Link
          href={languageStore.language === 'ru' ? '/privacy-ru.html' : '/privacy.html'}
          target="_blank"
          rel="noopener noreferrer"
        >
          {content.privacy}
        </Link>
      </Typography>
      <Stack direction="row" spacing={1}>
        <Button
          fullWidth
          variant={consent === 'denied' ? 'contained' : 'outlined'}
          aria-pressed={consent === 'denied'}
          onClick={() => analyticsService.changeConsent('denied')}
        >
          {content.decline}
        </Button>
        <Button
          fullWidth
          variant={consent === 'granted' ? 'contained' : 'outlined'}
          aria-pressed={consent === 'granted'}
          onClick={() => analyticsService.changeConsent('granted')}
        >
          {content.allow}
        </Button>
      </Stack>
    </Stack>
  );
});
