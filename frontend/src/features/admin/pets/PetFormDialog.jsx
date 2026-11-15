import { useEffect, useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material';

const empty = { name: '', species: 'DOG', breed: '', age: 1, gender: 'MALE', price: '', description: '' };

/** Create or edit a pet. Availability is not editable: inventory owns it (R-SAGA-05). */
function PetFormDialog({ open, pet, onClose, onSave }) {
  const [form, setForm] = useState(empty);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(pet ? { ...empty, ...pet } : empty);
      setError('');
    }
  }, [open, pet]);

  const handleChange = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

  const handleSave = async () => {
    setSaving(true);
    setError('');
    try {
      await onSave({
        name: form.name,
        species: form.species,
        breed: form.breed,
        age: Number(form.age),
        gender: form.gender,
        price: Number(form.price),
        description: form.description,
      });
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{pet ? `Edit ${pet.name}` : 'New pet'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Name" name="name" value={form.name} onChange={handleChange} required />
          <Stack direction="row" spacing={2}>
            <TextField select label="Species" name="species" value={form.species} onChange={handleChange} fullWidth>
              <MenuItem value="DOG">Dog</MenuItem>
              <MenuItem value="CAT">Cat</MenuItem>
            </TextField>
            <TextField select label="Gender" name="gender" value={form.gender} onChange={handleChange} fullWidth>
              <MenuItem value="MALE">Male</MenuItem>
              <MenuItem value="FEMALE">Female</MenuItem>
            </TextField>
          </Stack>
          <TextField label="Breed" name="breed" value={form.breed} onChange={handleChange} required />
          <Stack direction="row" spacing={2}>
            <TextField label="Age (years)" name="age" type="number" inputProps={{ min: 0 }} value={form.age} onChange={handleChange} fullWidth />
            <TextField label="Price (USD)" name="price" type="number" inputProps={{ min: 0.01, step: 0.01 }} value={form.price} onChange={handleChange} fullWidth required />
          </Stack>
          <TextField label="Description" name="description" value={form.description} onChange={handleChange} multiline minRows={3} required />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSave} disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default PetFormDialog;
