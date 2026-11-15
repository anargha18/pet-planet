import { useState } from 'react';
import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material';
import { changePassword, extractApiError } from '../../api';

const empty = { currentPassword: '', newPassword: '', confirm: '' };

/** PUT /api/v1/users/me/password (new password ≥ 8 characters; 401 if the current one is wrong). */
function ChangePasswordForm() {
  const [form, setForm] = useState(empty);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [saving, setSaving] = useState(false);

  const handleChange = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setDone(false);
    if (form.newPassword.length < 8) {
      setError('The new password must have at least 8 characters.');
      return;
    }
    if (form.newPassword !== form.confirm) {
      setError('The new passwords do not match.');
      return;
    }
    setSaving(true);
    try {
      await changePassword(form.currentPassword, form.newPassword);
      setForm(empty);
      setDone(true);
    } catch (saveError) {
      setError(extractApiError(saveError));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box component="form" onSubmit={handleSubmit} noValidate>
      <Stack spacing={2}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>Change password</Typography>
        {done && <Alert severity="success">Password changed.</Alert>}
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="Current password" name="currentPassword" type="password" value={form.currentPassword}
          onChange={handleChange} autoComplete="current-password" required />
        <TextField label="New password" name="newPassword" type="password" value={form.newPassword}
          onChange={handleChange} autoComplete="new-password" helperText="At least 8 characters" required />
        <TextField label="Repeat new password" name="confirm" type="password" value={form.confirm}
          onChange={handleChange} autoComplete="new-password" required />
        <Button type="submit" variant="contained" disabled={saving}>{saving ? 'Saving…' : 'Change password'}</Button>
      </Stack>
    </Box>
  );
}

export default ChangePasswordForm;
