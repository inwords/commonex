export const getMobileVersion = (userAgent: string | undefined): number | undefined => {
  const mobile = /(?:^|\s)CommonEx\/(\d+) \((?:Android|iOS)\/[rd]\)(?:\s|$)/.exec(userAgent ?? '');
  return mobile ? Number(mobile[1]) : undefined;
};
