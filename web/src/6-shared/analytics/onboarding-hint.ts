import type {eventParameters} from './analytics-event.ts';

export type OnboardingHint = (typeof eventParameters)['onboarding_step_viewed']['step'][number];
