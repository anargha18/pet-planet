import { useEffect, useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material';

const empty = { email: '', password: '', firstName: '', lastName: '', role: 'USER' };

/** Create a user (POST /api/v1/users) with any role. */
function UserFormDialog({ open, onClose, onSave }) {
  const [form, setForm] = useState(empty);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(empty);
      setError('');
    }
  }, [open]);

  const handleChange = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

  const handleSave = async () => {
    setSaving(true);
    setError('');
    try {
      await onSave(form);
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>New user</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Stack direction="row" spacing={2}>
            <TextField label="First name" name="firstName" value={form.firstName} onChange={handleChange} fullWidth required />
            <TextField label="Last name" name="lastName" value={form.lastName} onChange={handleChange} fullWidth required />
          </Stack>
          <TextField label="Email" name="email" type="email" value={form.email} onChange={handleChange} required />
          <TextField label="Initial password" name="password" type="password" value={form.password} onChange={handleChange}
            helperText="At least 8 characters" required />
          <TextField select label="Role" name="role" value={form.role} onChange={handleChange}>
            <MenuItem value="USER">User</MenuItem>
            <MenuItem value="ADMIN">Admin</MenuItem>
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSave} disabled={saving}>{saving ? 'Creating…' : 'Create'}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default UserFormDialog;
