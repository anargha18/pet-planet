import { useState } from 'react';
import {
  Alert,
  Button,
  Container,
  MenuItem,
  Pagination,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { archivePet, createPet, extractApiError, getPets, updatePet } from '../../../api';
import { formatCurrency, humanize } from '../../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../../components/PageState';
import StatusChip from '../../../components/StatusChip';
import AdminPageHeader from '../AdminPageHeader';
import { usePagedQuery } from '../usePagedQuery';
import PetFormDialog from './PetFormDialog';

const PAGE_SIZE = 20;

/** Catalog administration: create, edit, archive (R-FE-04). Archived pets are listed with the RETIRED filter. */
function AdminPetsPage() {
  const [pageIndex, setPageIndex] = useState(0);
  const [availability, setAvailability] = useState('');
  const [dialog, setDialog] = useState({ open: false, pet: null });
  const [message, setMessage] = useState(null);
  const params = { page: pageIndex, size: PAGE_SIZE, sort: 'createdAt,desc', ...(availability && { availabilityStatus: availability }) };
  const { page, rows, error, loading, reload } = usePagedQuery(getPets, params);

  const save = async (pet) => {
    try {
      if (dialog.pet) {
        await updatePet(dialog.pet.id, pet);
      } else {
        await createPet(pet);
      }
    } catch (saveError) {
      throw new Error(extractApiError(saveError));
    }
    setMessage({ severity: 'success', text: dialog.pet ? `Saved ${pet.name}.` : `Created ${pet.name}. Inventory makes it available within a few seconds.` });
    setDialog({ open: false, pet: null });
    reload();
  };

  const archive = async (pet) => {
    if (!window.confirm(`Archive ${pet.name}? It disappears from the shop and can no longer be ordered.`)) return;
    try {
      await archivePet(pet.id);
      setMessage({ severity: 'success', text: `Archived ${pet.name}.` });
      reload();
    } catch (archiveError) {
      setMessage({ severity: 'error', text: extractApiError(archiveError) });
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <AdminPageHeader title="Pets" subtitle="Availability is read-only here; it follows inventory.">
          <TextField select size="small" label="Availability" value={availability} sx={{ minWidth: 170 }}
            onChange={(event) => { setAvailability(event.target.value); setPageIndex(0); }}>
            <MenuItem value="">Active (not archived)</MenuItem>
            {['AVAILABLE', 'RESERVED', 'SOLD', 'RETIRED'].map((s) => <MenuItem key={s} value={s}>{humanize(s)}</MenuItem>)}
          </TextField>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, pet: null })}>New pet</Button>
        </AdminPageHeader>

        {message && <Alert severity={message.severity} onClose={() => setMessage(null)}>{message.text}</Alert>}
        <ErrorState message={error} onRetry={reload} />

        {loading ? <LoadingState /> : !error && rows.length === 0 ? (
          <EmptyState title="No pets match this filter" />
        ) : rows.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Species / breed</TableCell>
                  <TableCell>Age</TableCell>
                  <TableCell align="right">Price</TableCell>
                  <TableCell>Availability</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((pet) => (
                  <TableRow key={pet.id} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{pet.name}</TableCell>
                    <TableCell>{humanize(pet.species)} · {pet.breed}</TableCell>
                    <TableCell>{pet.age}</TableCell>
                    <TableCell align="right">{formatCurrency(pet.price)}</TableCell>
                    <TableCell><StatusChip status={pet.availabilityStatus} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => setDialog({ open: true, pet })}>Edit</Button>
                        {pet.availabilityStatus === 'AVAILABLE' && (
                          <Button size="small" color="error" onClick={() => archive(pet)}>Archive</Button>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {page?.totalPages > 1 && (
          <Pagination count={page.totalPages} page={pageIndex + 1} onChange={(_, value) => setPageIndex(value - 1)} />
        )}
      </Stack>
      <PetFormDialog open={dialog.open} pet={dialog.pet} onClose={() => setDialog({ open: false, pet: null })} onSave={save} />
    </Container>
  );
}

export default AdminPetsPage;
