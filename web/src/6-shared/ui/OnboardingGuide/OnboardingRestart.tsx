import {IconButton, Tooltip} from '@mui/material';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';
import {onboardingService} from '@/6-shared/services/onboarding-service';

export const OnboardingRestart = observer(() => {
  const content = useContent();
  return (
    <Tooltip describeChild title={content.Onboarding.restart}>
      <IconButton size="small" aria-label={content.Onboarding.restart} onClick={onboardingService.restart}>
        <MenuBookOutlinedIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  );
});
