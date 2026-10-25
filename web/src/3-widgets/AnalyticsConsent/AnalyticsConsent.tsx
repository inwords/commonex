import {useState, useSyncExternalStore} from 'react';
import {Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Link} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {useContent} from '@/6-shared/i18n/useContent';
import {languageStore} from '@/6-shared/i18n/languageStore';

export const AnalyticsConsent = observer(() => {
  const content = useContent().Analytics;
  const consent = useSyncExternalStore(analyticsService.subscribeConsent, analyticsService.getConsent, () => 'unknown');
  const [editing, setEditing] = useState(false);
  if (!analyticsService.isConfigured()) {
    return null;
  }
  const choose = (value: 'granted' | 'denied') => {
    setEditing(false);
    analyticsService.changeConsent(value);
  };
  const actions = (
    <>
      <Button onClick={() => choose('denied')}>{content.decline}</Button>
      <Button onClick={() => choose('granted')}>{content.allow}</Button>
    </>
  );
  const explanation = (
    <>
      {content.description}{' '}
      <Link
        href={languageStore.language === 'ru' ? '/privacy-ru.html' : '/privacy.html'}
        target="_blank"
        rel="noopener noreferrer"
      >
        {content.privacy}
      </Link>
    </>
  );
  return (
    <>
      <Button onClick={() => setEditing(true)}>{content.settings}</Button>
      {consent === 'unknown' && !editing && (
        <Box
          component="section"
          aria-label={content.title}
          sx={{p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1, maxWidth: 640}}
        >
          <Box component="p" sx={{mt: 0}}>
            {explanation}
          </Box>
          {actions}
        </Box>
      )}
      <Dialog open={editing} onClose={() => setEditing(false)} aria-labelledby="analytics-title">
        <DialogTitle id="analytics-title">{content.title}</DialogTitle>
        <DialogContent>
          <DialogContentText component="div">{explanation}</DialogContentText>
        </DialogContent>
        <DialogActions>{actions}</DialogActions>
      </Dialog>
    </>
  );
});
