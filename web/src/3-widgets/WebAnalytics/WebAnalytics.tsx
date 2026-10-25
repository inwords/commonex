import {useEffect, useSyncExternalStore} from 'react';
import {useLocation} from 'react-router';
import {analyticsService} from '@/6-shared/analytics/analytics-service';

export function WebAnalytics() {
  const location = useLocation();
  const consent = useSyncExternalStore(analyticsService.subscribeConsent, analyticsService.getConsent, () => 'unknown');
  useEffect(() => {
    analyticsService.navigate(location.key, window.location.href, document.referrer);
  }, [location.key, consent]);
  return null;
}
