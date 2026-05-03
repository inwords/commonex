'use client';

import {Navigate, Route, Routes} from 'react-router';
import {BrowserRouter} from 'react-router-dom';
import {MainPage} from '@/2-pages/MainPage';
import {CssBaseline, NoSsr} from '@mui/material';
import {ThemeProvider} from '@mui/material/styles';
import {ROUTES} from '@/6-shared/routing/constants';
import {EventPage} from '@/2-pages/EventPage';
import {SupportPage} from '@/2-pages/SupportPage';
import {LanguageSwitcher} from '@/6-shared/ui/LanguageSwitcher/LanguageSwitcher';
import {theme} from '@/6-shared/theme/theme';

export default function Home() {
  return (
    <ThemeProvider theme={theme} defaultMode="system">
      <CssBaseline />
      <LanguageSwitcher />
      <NoSsr>
        <BrowserRouter>
          <Routes>
            <Route path={ROUTES.Main} element={<MainPage />} />

            <Route path={ROUTES.Event(':id')} element={<EventPage />} />

            <Route path={ROUTES.Support} element={<SupportPage />} />

            <Route path="*" element={<Navigate to={ROUTES.Main} />} />
          </Routes>
        </BrowserRouter>
      </NoSsr>
    </ThemeProvider>
  );
}
