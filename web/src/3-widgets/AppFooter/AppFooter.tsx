import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {Box, Container, Link, Stack, Typography} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {languageStore} from '@/6-shared/i18n/languageStore';
import {useContent} from '@/6-shared/i18n/useContent';

export const AppFooter = observer(() => {
  const content = useContent().AppFooter;

  return (
    <Box
      component="footer"
      sx={{borderTop: 1, borderColor: 'divider', bgcolor: 'background.paper', pt: 3, pb: {xs: 10, sm: 4}, mt: 5}}
    >
      <Container maxWidth="md">
        <Stack alignItems="center" spacing={2}>
          <Typography variant="body2" color="text.secondary" align="center">
            {content.title}
          </Typography>
          <Stack direction="row" useFlexGap flexWrap="wrap" justifyContent="center" gap={2}>
            <Link
              onClick={() => analyticsService.track({name: 'store_link_clicked', params: {store: 'app_store'}})}
              href="https://apps.apple.com/app/id6761690923"
              target="_blank"
              rel="noopener noreferrer"
              sx={{
                display: 'inline-flex',
                borderRadius: 1,
                '&:focus-visible': {outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 4},
              }}
            >
              <Box
                component="img"
                src={`/store-badges/app-store-${languageStore.language}.svg`}
                alt={content.appStore}
                width={120}
                height={40}
                sx={{display: 'block', height: 48, width: 'auto'}}
              />
            </Link>
            <Link
              onClick={() => analyticsService.track({name: 'store_link_clicked', params: {store: 'rustore'}})}
              href="https://www.rustore.ru/catalog/app/ru.commonex"
              target="_blank"
              rel="noopener noreferrer"
              sx={{
                display: 'inline-flex',
                borderRadius: 1,
                '&:focus-visible': {outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 4},
              }}
            >
              <Box
                component="img"
                src={`/store-badges/rustore-${languageStore.language}.svg`}
                alt={content.ruStore}
                width={111}
                height={40}
                sx={{display: 'block', height: 48, width: 'auto'}}
              />
            </Link>
          </Stack>
        </Stack>
      </Container>
    </Box>
  );
});
