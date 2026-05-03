import {languageStore} from './languageStore';
import type {Locale} from './locales/ru';

export const useContent = (): Locale => {
  return languageStore.content;
};
