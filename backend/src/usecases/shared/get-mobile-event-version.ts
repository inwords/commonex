import {EventVersion} from '#domain/entities/event-version.enum';

export const getMobileEventVersion = (userAgent: string | undefined): EventVersion | undefined => {
  const mobile = /(?:^|\s)CommonEx\/(\d+) \((?:Android|iOS)\/[rd]\)(?:\s|$)/.exec(userAgent ?? '');
  if (!mobile) {
    return undefined;
  }
  const release = Number(mobile[1]);
  return Number.isSafeInteger(release) && release >= 11 ? EventVersion.ExpenseEditing : EventVersion.Legacy;
};
