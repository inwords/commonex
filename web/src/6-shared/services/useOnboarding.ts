import {useSyncExternalStore} from 'react';
import {onboardingService} from './onboarding-service';

export const useOnboarding = () =>
  useSyncExternalStore(onboardingService.subscribe, onboardingService.getSnapshot, onboardingService.getServerSnapshot);
