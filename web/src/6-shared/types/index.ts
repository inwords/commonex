import type {Locale, Language} from '../i18n/locales/ru';

export type {Locale, Language};

// legacy aliases, kept for backwards compat
export type AllContent = {ru: Locale; en: Locale};
export type ContentLanguages = Language;
