import {Box, Button, Paper, Popper, Stack, Typography} from '@mui/material';
import {useEffect, useId, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {onboardingService} from '@/6-shared/services/onboarding-service';
import {useOnboarding} from '@/6-shared/services/useOnboarding';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  title: string;
  content: string;
  stage: number;
  target?: string;
  onShown: () => void;
}

export const OnboardingGuide = observer(({title, content: text, stage, target, onShown}: Props) => {
  const state = useOnboarding();
  const content = useContent().Onboarding;
  const titleId = useId();
  const [anchor, setAnchor] = useState<{element: HTMLElement; rect: DOMRect} | null>(null);
  const active = state.status === 'active';
  const visible = active && (!target || anchor?.element.dataset.onboarding === target);

  useEffect(() => {
    if (visible) {
      onShown();
    }
  }, [visible, onShown]);

  useEffect(() => {
    if (!target || !active) {
      return;
    }
    const element = document.querySelector<HTMLElement>(`[data-onboarding="${target}"]`);
    if (!element) {
      return;
    }
    let frame = 0;
    const measure = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => setAnchor({element, rect: element.getBoundingClientRect()}));
    };
    element.scrollIntoView({block: 'nearest', inline: 'nearest'});
    measure();
    const resize = new ResizeObserver(measure);
    resize.observe(element);
    const viewport = window.visualViewport;
    viewport?.addEventListener('resize', measure);
    viewport?.addEventListener('scroll', measure);
    window.addEventListener('resize', measure);
    document.addEventListener('scroll', measure, true);
    return () => {
      cancelAnimationFrame(frame);
      resize.disconnect();
      viewport?.removeEventListener('resize', measure);
      viewport?.removeEventListener('scroll', measure);
      window.removeEventListener('resize', measure);
      document.removeEventListener('scroll', measure, true);
    };
  }, [target, active]);

  if (!active) {
    return null;
  }

  const card = (
    <Paper
      role="region"
      aria-labelledby={titleId}
      elevation={target ? 8 : 0}
      sx={{
        p: 2,
        borderRadius: 3,
        border: 1,
        borderColor: 'primary.main',
        bgcolor: 'background.paper',
        width: target ? 320 : 'auto',
        maxWidth: 'calc(100vw - 32px)',
        mb: target ? 0 : 2,
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
        <Typography variant="caption" color="text.secondary">
          {content.step} {stage} {content.of} 5
        </Typography>
        <Button size="small" onClick={onboardingService.skip}>
          {content.skip}
        </Button>
      </Stack>
      <Box aria-live="polite">
        <Typography id={titleId} variant="subtitle1" fontWeight={600}>
          {title}
        </Typography>
        <Typography variant="body2" sx={{mt: 0.5}}>
          {text}
        </Typography>
      </Box>
    </Paper>
  );

  if (!target) {
    return card;
  }
  if (!anchor || anchor.element.dataset.onboarding !== target) {
    return null;
  }
  const {rect} = anchor;
  return (
    <>
      <Box
        aria-hidden
        sx={{
          position: 'fixed',
          pointerEvents: 'none',
          zIndex: 1201,
          top: rect.top - 6,
          left: rect.left - 6,
          width: rect.width + 12,
          height: rect.height + 12,
          borderRadius: 2,
          border: '2px solid',
          borderColor: 'primary.main',
          boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.55)',
          '@keyframes onboardingPulse': {
            '0%, 100%': {outlineColor: 'transparent'},
            '50%': {outlineColor: 'currentColor'},
          },
          outline: '4px solid transparent',
          outlineOffset: 3,
          color: 'primary.main',
          animation: 'onboardingPulse 1.4s ease-in-out 2',
          '@media (prefers-reduced-motion: reduce)': {animation: 'none'},
        }}
      />
      <Popper
        open
        anchorEl={anchor.element}
        placement="top"
        popperOptions={{strategy: 'fixed'}}
        modifiers={[
          {name: 'offset', options: {offset: [0, 16]}},
          {name: 'preventOverflow', options: {padding: 16}},
        ]}
        sx={{zIndex: 1202}}
      >
        {card}
      </Popper>
    </>
  );
});
