'use client';

import {Navigate, Route, Routes} from 'react-router';
import {BrowserRouter} from 'react-router-dom';
import {MainPage} from '@/2-pages/MainPage';
import {NoSsr} from '@mui/material';
import {ROUTES} from '@/6-shared/routing/constants';
import {EventPage} from '@/2-pages/EventPage';
import {SupportPage} from '@/2-pages/SupportPage';
import {LanguageSwitcher} from '@/6-shared/ui/LanguageSwitcher/LanguageSwitcher';

export default function Home() {
  return (
    <NoSsr>
      <LanguageSwitcher />
      <BrowserRouter>
        <Routes>
          <Route path={ROUTES.Main} element={<MainPage />} />

          <Route path={ROUTES.Event(':id')} element={<EventPage />} />

          <Route path={ROUTES.Support} element={<SupportPage />} />

          <Route path="*" element={<Navigate to={ROUTES.Main} />} />
        </Routes>
      </BrowserRouter>
    </NoSsr>
  );
}
