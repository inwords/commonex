import './helpers/register-source.ts';
import assert from 'node:assert/strict';
import test from 'node:test';

test('website language preferences do not prevent using analytics choices when storage is denied', async () => {
  Object.defineProperty(globalThis, 'window', {value: {}, configurable: true});
  Object.defineProperty(globalThis, 'localStorage', {get() {throw new Error('Storage blocked');}, configurable: true});
  try {
    let languageStore: typeof import('../src/6-shared/i18n/languageStore.ts').languageStore;
    await assert.doesNotReject(async () => { ({languageStore} = await import('../src/6-shared/i18n/languageStore.ts')); });
    assert.equal(languageStore!.language, 'ru');
    assert.doesNotThrow(() => languageStore!.setLanguage('en'));
    assert.equal(languageStore!.language, 'en');
  } finally {
    Reflect.deleteProperty(globalThis, 'window');
    Reflect.deleteProperty(globalThis, 'localStorage');
  }
});
