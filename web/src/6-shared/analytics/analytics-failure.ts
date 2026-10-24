export function analyticsFailureReason(
  error: unknown,
): 'network' | 'access_denied' | 'not_found' | 'validation' | 'server' | 'unknown' {
  if (error instanceof TypeError) return 'network';
  if (!error || typeof error !== 'object') return 'unknown';
  const {statusCode, code} = error as {statusCode?: number; code?: string};
  if (['B4003', 'B4008', 'B4009'].includes(code ?? '') || statusCode === 401 || statusCode === 403)
    return 'access_denied';
  if (code === 'B4001' || code === 'B4002' || statusCode === 404) return 'not_found';
  if (statusCode === 400 || statusCode === 422) return 'validation';
  if (statusCode && statusCode >= 500) return 'server';
  return 'unknown';
}
