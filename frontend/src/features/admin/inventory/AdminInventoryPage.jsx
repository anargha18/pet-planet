import { useCallback, useEffect, useState } from 'react';
import {
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
import { Link } from 'react-router-dom';
import { getPets, listInventory } from '../../../api';
import { formatDateTime, humanize } from '../../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../../components/PageState';
import StatusChip from '../../../components/StatusChip';
import AdminPageHeader from '../AdminPageHeader';
import { usePagedQuery } from '../usePagedQuery';

/** Pet names for the inventory rows (inventory itself only knows pet IDs). Best effort: IDs are shown if it fails. */
function usePetNames() {
  const [names, setNames] = useState({});
  const load = useCallback(async () => {
    try {
      const [active, retired] = await Promise.all([
        getPets({ size: 500 }),
        getPets({ size: 500, availabilityStatus: 'RETIRED' }),
      ]);
      setNames(Object.fromEntries([...active.content, ...retired.content].map((pet) => [pet.id, pet.name])));
    } catch {
      setNames({}); // names are cosmetic; the table still shows pet IDs
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  return names;
}

/** Read-only inventory (R-FE-04, R-SAGA-07): status, reservation and sale details per pet. */
function AdminInventoryPage() {
  const [pageIndex, setPageIndex] = useState(0);
  const [status, setStatus] = useState('');
  const names = usePetNames();
  const { page, rows, error, loading, reload } = usePagedQuery(listInventory, {
    page: pageIndex, size: 20, ...(status && { status }),
  });

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <AdminPageHeader title="Inventory" subtitle="Read-only: reservations, releases and sales only happen through the checkout saga.">
          <TextField select size="small" label="Status" value={status} sx={{ minWidth: 160 }}
            onChange={(event) => { setStatus(event.target.value); setPageIndex(0); }}>
            <MenuItem value="">All</MenuItem>
            {['AVAILABLE', 'RESERVED', 'SOLD', 'RETIRED'].map((s) => <MenuItem key={s} value={s}>{humanize(s)}</MenuItem>)}
          </TextField>
        </AdminPageHeader>
        <ErrorState message={error} onRetry={reload} />
        {loading ? <LoadingState /> : !error && rows.length === 0 ? (
          <EmptyState title="No inventory records match this filter" />
        ) : rows.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Pet</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Order</TableCell>
                  <TableCell>Reserved</TableCell>
                  <TableCell>Reservation expires</TableCell>
                  <TableCell>Sold</TableCell>
                  <TableCell>Updated</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((item) => (
                  <TableRow key={item.petId} hover>
                    <TableCell>
                      <Link to={`/pets/${item.petId}`}>{names[item.petId] || item.petId}</Link>
                    </TableCell>
                    <TableCell><StatusChip status={item.status} /></TableCell>
                    <TableCell>{item.orderId ? <Link to={`/orders/${item.orderId}`}>#{item.orderId}</Link> : '—'}</TableCell>
                    <TableCell>{formatDateTime(item.reservedAt)}</TableCell>
                    <TableCell>{formatDateTime(item.reservationExpiresAt)}</TableCell>
                    <TableCell>{formatDateTime(item.soldAt)}</TableCell>
                    <TableCell>{formatDateTime(item.updatedAt)}</TableCell>
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
    </Container>
  );
}

export default AdminInventoryPage;
