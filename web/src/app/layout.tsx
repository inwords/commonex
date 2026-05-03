import type {Metadata, Viewport} from 'next';
import './globals.css';
import {Roboto} from 'next/font/google';
import {ClientProviders} from './ClientProviders';
import InitColorSchemeScript from '@mui/material/InitColorSchemeScript';

// If loading a variable font, you don't need to specify the font weight
const roboto = Roboto({
  weight: ['300', '400', '500', '700'],
  display: 'swap',
  subsets: ['cyrillic', 'latin'],
});

export const metadata: Metadata = {
  title: 'CommonEx - Учёт общих расходов в поездках и мероприятиях',
  description:
    'Удобный сервис для учёта общих расходов в поездках и мероприятиях. Создавайте события, добавляйте участников и отслеживайте, кто кому должен. Простое управление финансами в группе.',
  keywords: [
    'общие расходы',
    'учёт расходов',
    'совместные траты',
    'поездки',
    'мероприятия',
    'долги',
    'разделение расходов',
    'калькулятор долгов',
    'групповые расходы',
    'финансы в поездке',
  ],
  openGraph: {
    type: 'website',
    locale: 'ru_RU',
    url: 'https://commonex.ru',
    siteName: 'CommonEx',
    title: 'CommonEx - Учёт общих расходов',
    description:
      'Удобный сервис для учёта общих расходов в поездках и мероприятиях. Создавайте события, добавляйте участников и отслеживайте, кто кому должен.',
    images: [
      {
        url: 'https://commonex.ru/og-image.png',
        width: 1200,
        height: 630,
        alt: 'CommonEx - Учёт общих расходов',
      },
    ],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
};

export const viewport: Viewport = {
  initialScale: 1,
  width: 'device-width',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru" className={roboto.className} suppressHydrationWarning>
      <head>
        <InitColorSchemeScript defaultMode="system" />
      </head>
      <body>
        {children}
        <ClientProviders />
      </body>
    </html>
  );
}
