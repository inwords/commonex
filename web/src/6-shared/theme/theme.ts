import {createTheme} from '@mui/material/styles';

export const theme = createTheme({
  components: {
    MuiDialogContent: {
      styleOverrides: {
        root: ({theme}) => ({
          '.MuiDialogTitle-root + &': {
            paddingTop: theme.spacing(2),
          },
        }),
      },
    },
  },
  cssVariables: {
    colorSchemeSelector: '[data-mui-color-scheme="%s"]',
  },
  colorSchemes: {
    light: {
      palette: {
        primary: {
          main: '#1976d2',
        },
        background: {
          default: '#f6f7f8',
          paper: '#ffffff',
        },
      },
    },
    dark: {
      palette: {
        primary: {
          main: '#90caf9',
        },
        background: {
          default: '#121212',
          paper: '#1e1e1e',
        },
      },
    },
  },
});
