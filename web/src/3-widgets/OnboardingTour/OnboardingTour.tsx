import {
  Paper,
  Typography,
  Box,
  LinearProgress,
  IconButton,
  Button,
  Stack,
  Collapse,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import MinimizeIcon from '@mui/icons-material/Minimize';
import {useState} from 'react';
import {onboardingService, OnboardingStep} from '@/6-shared/services/onboarding-service';

interface Step {
  title: string;
  content: string;
  step: OnboardingStep;
}

interface Props {
  steps: Step[];
}

export const OnboardingTour = ({steps}: Props) => {
  const [isDismissed, setIsDismissed] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);

  const stepKeys = steps.map((s) => s.step);
  const completedSteps = onboardingService.getCompletedSteps();
  const allStepsCompleted = stepKeys.every((step) => completedSteps.includes(step));
  const isVisible = !isDismissed && !allStepsCompleted;

  const handleNext = () => {
    const currentStep = steps[currentStepIndex];
    onboardingService.completeStep(currentStep.step);

    if (currentStepIndex < steps.length - 1) {
      setCurrentStepIndex(currentStepIndex + 1);
    } else {
      handleComplete();
    }
  };

  const handleBack = () => {
    if (currentStepIndex > 0) {
      setCurrentStepIndex(currentStepIndex - 1);
    }
  };

  const handleComplete = () => {
    steps.forEach((step) => onboardingService.completeStep(step.step));
    setIsDismissed(true);
  };

  const handleClose = () => {
    steps.forEach((step) => onboardingService.completeStep(step.step));
    setIsDismissed(true);
  };

  const handleMinimize = () => {
    setIsMinimized(!isMinimized);
  };

  if (!isVisible) {
    return null;
  }

  const progress = ((currentStepIndex + 1) / steps.length) * 100;

  return (
    <Paper
      elevation={8}
      sx={{
        position: 'fixed',
        bottom: 16,
        right: 16,
        width: 360,
        maxWidth: 'calc(100vw - 32px)',
        zIndex: 1300,
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: 2,
          paddingBottom: 1,
          borderBottom: isMinimized ? 'none' : '1px solid',
          borderColor: 'divider',
        }}
      >
        <Typography variant="h6" fontSize="1rem">
          {steps[currentStepIndex].title}
        </Typography>

        <Stack direction="row" spacing={0.5}>
          <IconButton size="small" onClick={handleMinimize}>
            <MinimizeIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={handleClose}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </Stack>
      </Box>

      <Collapse in={!isMinimized}>
        <Box padding={2}>
          <Box marginBottom={2}>
            <LinearProgress variant="determinate" value={progress} />
            <Typography variant="caption" color="text.secondary" marginTop={0.5}>
              Шаг {currentStepIndex + 1} из {steps.length}
            </Typography>
          </Box>

          <Typography variant="body2" marginBottom={2}>
            {steps[currentStepIndex].content}
          </Typography>

          <Stack direction="row" spacing={1} justifyContent="flex-end">
            {currentStepIndex > 0 && (
              <Button size="small" onClick={handleBack}>
                Назад
              </Button>
            )}

            <Button size="small" variant="contained" onClick={handleNext}>
              {currentStepIndex < steps.length - 1 ? 'Далее' : 'Понятно'}
            </Button>
          </Stack>
        </Box>
      </Collapse>
    </Paper>
  );
};
