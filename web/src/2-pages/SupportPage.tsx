import {
  Box,
  Container,
  Link,
  Stack,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {useContent} from '@/6-shared/i18n/useContent';
import {observer} from 'mobx-react-lite';

export const SupportPage = observer(() => {
  const content = useContent();
  const s = content.Support;

  return (
    <Container maxWidth="sm">
      <Box paddingTop={6} paddingBottom={6}>
        <Typography variant="h4" component="h1" gutterBottom>
          {s.title}
        </Typography>

        <Typography variant="body1" color="text.secondary" marginBottom={4}>
          {s.subtitle}
        </Typography>

        <Stack spacing={4}>
          <Box>
            <Typography variant="h6" gutterBottom>
              {s.contacts}
            </Typography>
            <Link href={`mailto:${s.email}`} underline="hover">
              {s.email}
            </Link>
          </Box>

          <Box>
            <Typography variant="h6" gutterBottom>
              {s.documents}
            </Typography>
            <Link href={s.privacyPolicyUrl} underline="hover" target="_blank" rel="noopener">
              {s.privacyPolicy}
            </Link>
          </Box>

          <Box>
            <Typography variant="h6" gutterBottom>
              {s.faq.title}
            </Typography>
            <Stack spacing={1}>
              {s.faq.items.map(({q, a}) => (
                <Accordion key={q} disableGutters elevation={0} variant="outlined">
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body1">{q}</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Typography variant="body2" color="text.secondary">
                      {a}
                    </Typography>
                  </AccordionDetails>
                </Accordion>
              ))}
            </Stack>
          </Box>
        </Stack>
      </Box>
    </Container>
  );
});
