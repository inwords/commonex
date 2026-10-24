import {useEffect, useSyncExternalStore} from 'react';
import {useLocation} from 'react-router';
import {getAnalyticsConsent, initializeAnalytics, pageview, subscribeAnalyticsConsent} from '@/6-shared/analytics';
import {AnalyticsNavigation} from '@/6-shared/analytics/analytics-navigation';

let navigation: AnalyticsNavigation | undefined;

export function WebAnalytics() {
  const location = useLocation();
  const consent = useSyncExternalStore(subscribeAnalyticsConsent, getAnalyticsConsent, () => 'unknown');
  useEffect(() => {
    navigation ??= new AnalyticsNavigation(window.location.href, document.referrer);
    if (consent === 'granted') initializeAnalytics();
    navigation.update(location.key, window.location.href, consent === 'granted', pageview);
  }, [location.key, consent]);
  return null;
}
