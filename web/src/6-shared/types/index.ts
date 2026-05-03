import type {Locale} from '../i18n/locales/ru';
import type {Language} from '../i18n/languageStore';

export type {Locale, Language};

// legacy aliases, kept for backwards compat
export type AllContent = {ru: Locale; en: Locale};
export type ContentLanguages = Language;
