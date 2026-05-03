import {CreateEventButton} from '@/4-features/CreateEvent/ui/CreateEventButton';
import {Box, Stack, Typography, Container} from '@mui/material';
import {useEffect, useState} from 'react';
import {CreateEventModal} from '@/3-widgets/CreateEventModal/CreateEventModal';
import {FindEventForm} from '@/4-features/FindEvent/ui/FindEventForm';
import {currencyService} from '@/5-entities/currency/services/currency-service';
import {OnboardingTour} from '@/3-widgets/OnboardingTour';
import {useContent} from '@/6-shared/i18n/useContent';
import {MAIN_PAGE_ONBOARDING_STEP_KEYS} from '@/6-shared/constants/onboarding-steps';
import {observer} from 'mobx-react-lite';

export const MainPage = observer(() => {
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const content = useContent();

  const onboardingSteps = content.Onboarding.main.map((item, i) => ({
    ...item,
    step: MAIN_PAGE_ONBOARDING_STEP_KEYS[i],
  }));

  useEffect(() => {
    void currencyService.fetchCurrencies();
  }, []);

  return (
    <Container maxWidth="md">
      <Box display="flex" flexDirection="column" alignItems="center" paddingTop={4}>
        <Typography variant="h2" component="h1" gutterBottom align="center">
          CommonEx
        </Typography>

        <Typography variant="subtitle1" color="text.secondary" align="center" marginBottom={4}>
          {content.MainPage.subtitle}
        </Typography>

        <FindEventForm />

        <Box display="flex" justifyContent="center" marginTop={'16px'}>
          <Stack minWidth={300}>
            <CreateEventButton onClick={() => setIsDialogOpen(true)} />
          </Stack>
        </Box>

        <CreateEventModal isOpen={isDialogOpen} setIsOpen={setIsDialogOpen} />
      </Box>

      <OnboardingTour steps={onboardingSteps} />
    </Container>
  );
});
