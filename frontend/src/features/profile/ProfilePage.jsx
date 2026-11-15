import { useCallback, useEffect, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Chip, Container, Grid, Stack, TextField, Typography } from '@mui/material';
import { extractApiError, getCurrentUser, updateCurrentUser } from '../../api';
import { formatDateTime } from '../../components/format';
import { ErrorState, LoadingState } from '../../components/PageState';
import { useAuth } from '../auth';
import ChangePasswordForm from './ChangePasswordForm';

/** View and edit the signed-in user's profile (GET/PATCH /api/v1/users/me) and change the password (R-FE-03). */
function ProfilePage() {
  const { updateEmail } = useAuth();
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoadError('');
    try {
      const me = await getCurrentUser();
      setProfile(me);
      setForm({ firstName: me.firstName || '', lastName: me.lastName || '', email: me.email || '' });
    } catch (error) {
      setLoadError(extractApiError(error));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleChange = (event) => {
    setSaved(false);
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaveError('');
    setSaving(true);
    try {
      const updated = await updateCurrentUser(form);
      setProfile(updated);
      updateEmail(updated.email);
      setSaved(true);
    } catch (error) {
      setSaveError(extractApiError(error));
    } finally {
      setSaving(false);
    }
  };

  if (!profile) {
    return loadError ? (
      <Container maxWidth="md" sx={{ py: 6 }}><ErrorState message={loadError} onRetry={load} /></Container>
    ) : (
      <LoadingState label="Loading your profile…" />
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={1} sx={{ mb: 3 }}>
        <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>My profile</Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={profile.role} size="small" color={profile.role === 'ADMIN' ? 'secondary' : 'default'} />
          <Typography variant="body2" color="text.secondary">Member since {formatDateTime(profile.createdAt)}</Typography>
        </Stack>
      </Stack>
      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Card>
            <CardContent>
              <Box component="form" onSubmit={handleSubmit} noValidate>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>Details</Typography>
                  {saved && <Alert severity="success">Profile saved.</Alert>}
                  {saveError && <Alert severity="error">{saveError}</Alert>}
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                    <TextField label="First name" name="firstName" value={form.firstName} onChange={handleChange} fullWidth />
                    <TextField label="Last name" name="lastName" value={form.lastName} onChange={handleChange} fullWidth />
                  </Stack>
                  <TextField label="Email" name="email" type="email" value={form.email} onChange={handleChange} fullWidth />
                  <Button type="submit" variant="contained" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</Button>
                </Stack>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={5}>
          <Card>
            <CardContent>
              <ChangePasswordForm />
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

export default ProfilePage;
