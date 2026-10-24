import {makeAutoObservable} from 'mobx';
import type {Locale} from './locales/ru';
import ru from './locales/ru';
import en from './locales/en';

export type Language = 'ru' | 'en';

const STORAGE_KEY = 'lang';
const locales: Record<Language, Locale> = {ru, en};

class LanguageStore {
  language: Language = 'ru';
  content: Locale = ru;

  constructor() {
    makeAutoObservable(this);

    if (typeof window === 'undefined') {
      return;
    }

    try {
      const saved = localStorage.getItem(STORAGE_KEY) as Language | null;
      if (saved && saved in locales) {
        this.language = saved;
        this.content = locales[saved];
      }
    } catch {
      // Browser storage is optional; keep the default language.
    }
  }

  setLanguage(lang: Language) {
    this.language = lang;
    this.content = locales[lang];

    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // Keep the selected language for this page when persistence is blocked.
      }
    }
  }
}

export const languageStore = new LanguageStore();
