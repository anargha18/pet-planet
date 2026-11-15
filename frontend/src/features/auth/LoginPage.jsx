import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Container,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { extractApiError, loginUser } from '../../api';

const initialState = { email: '', password: '' };

function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login } = useAuth();
  const expired = searchParams.get('expired') === '1';
  // Only same-app paths are followed after login (never an absolute URL from the query string).
  const requestedNext = searchParams.get('next') || '/';
  const next = requestedNext.startsWith('/') && !requestedNext.startsWith('//') ? requestedNext : '/';
  const [form, setForm] = useState(initialState);
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const authResponse = await loginUser(form);
      login(authResponse);
      navigate(next, { replace: true });
    } catch (apiError) {
      setError(extractApiError(apiError));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Card>
        <CardContent sx={{ p: { xs: 3, sm: 5 } }}>
          <Stack spacing={3}>
            <Box>
              <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.16em' }}>
                Welcome back
              </Typography>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>
                Login to Pet Planet
              </Typography>
            </Box>

            {expired && !error && (
              <Alert severity="warning">Your session has expired. Please sign in again.</Alert>
            )}
            {!expired && requestedNext !== '/' && !error && (
              <Alert severity="info">Please sign in to continue.</Alert>
            )}
            {error && <Alert severity="error">{error}</Alert>}

            <Box component="form" onSubmit={handleSubmit} noValidate>
              <Stack spacing={2}>
                <TextField
                  label="Email"
                  name="email"
                  type="email"
                  fullWidth
                  value={form.email}
                  onChange={handleChange}
                  required
                />
                <TextField
                  label="Password"
                  name="password"
                  type="password"
                  fullWidth
                  value={form.password}
                  onChange={handleChange}
                  required
                />
                <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
                  {isSubmitting ? 'Signing in...' : 'Login'}
                </Button>
              </Stack>
            </Box>

            <Typography variant="body2" color="text.secondary">
              New here?{' '}
              <Link component={RouterLink} to="/register">
                Create an account
              </Link>
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </Container>
  );
}

export default LoginPage;
