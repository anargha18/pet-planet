import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1f5f3b',
      light: '#3d7b5e',
      dark: '#163f2a',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#e77a46',
      light: '#f4a77a',
      dark: '#c85d2d',
      contrastText: '#ffffff',
    },
    background: {
      default: '#f7f4ee',
      paper: '#fffdf9',
    },
    text: {
      primary: '#1f1f1f',
      secondary: '#5c5c5c',
    },
    success: {
      main: '#2d8a58',
    },
    warning: {
      main: '#df9c3d',
    },
    error: {
      main: '#b54541',
    },
    info: {
      main: '#2d5a7a',
    },
  },
  shape: {
    borderRadius: 16,
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", sans-serif',
    h1: { fontWeight: 800, letterSpacing: '-0.04em' },
    h2: { fontWeight: 800, letterSpacing: '-0.03em' },
    h3: { fontWeight: 700 },
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 999,
          padding: '10px 20px',
          boxShadow: 'none',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 18,
          boxShadow: '0 10px 30px rgba(27, 48, 36, 0.08)',
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: '#fffdf9',
          color: '#163f2a',
          boxShadow: '0 2px 12px rgba(25, 42, 35, 0.08)',
        },
      },
    },
  },
});

export default theme;
