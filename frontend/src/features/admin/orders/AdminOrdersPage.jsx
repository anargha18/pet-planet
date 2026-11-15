import { useState } from 'react';
import {
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
import { Link } from 'react-router-dom';
import { getOrders } from '../../../api';
import { formatCurrency, formatDateTime, humanize } from '../../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../../components/PageState';
import StatusChip from '../../../components/StatusChip';
import AdminPageHeader from '../AdminPageHeader';
import { usePagedQuery } from '../usePagedQuery';

const STATUSES = ['CREATED', 'PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED'];

/** All customers' orders, filterable by status and customer (R-FE-04). Details open the shared order page. */
function AdminOrdersPage() {
  const [pageIndex, setPageIndex] = useState(0);
  const [status, setStatus] = useState('');
  const [customerId, setCustomerId] = useState('');
  const { page, rows, error, loading, reload } = usePagedQuery(getOrders, {
    page: pageIndex,
    size: 20,
    sort: 'createdAt,desc',
    ...(status && { status }),
    ...(customerId && { customerId }),
  });

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <AdminPageHeader title="Orders" subtitle="Every customer’s orders.">
          <TextField size="small" label="Customer ID" value={customerId} sx={{ width: 130 }}
            onChange={(event) => { setCustomerId(event.target.value.replace(/\D/g, '')); setPageIndex(0); }} />
          <TextField select size="small" label="Status" value={status} sx={{ minWidth: 170 }}
            onChange={(event) => { setStatus(event.target.value); setPageIndex(0); }}>
            <MenuItem value="">All</MenuItem>
            {STATUSES.map((s) => <MenuItem key={s} value={s}>{humanize(s)}</MenuItem>)}
          </TextField>
        </AdminPageHeader>
        <ErrorState message={error} onRetry={reload} />
        {loading ? <LoadingState /> : !error && rows.length === 0 ? (
          <EmptyState title="No orders match these filters" />
        ) : rows.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Placed</TableCell>
                  <TableCell>Items</TableCell>
                  <TableCell align="right">Total</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((order) => (
                  <TableRow key={order.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>#{order.id}</TableCell>
                    <TableCell>{order.customerId}</TableCell>
                    <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                    <TableCell>{order.items?.map((item) => item.petName).join(', ')}</TableCell>
                    <TableCell align="right">{formatCurrency(order.totalAmount)}</TableCell>
                    <TableCell><StatusChip status={order.status} /></TableCell>
                    <TableCell align="right">
                      <Button size="small" component={Link} to={`/orders/${order.id}`}>Details</Button>
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
    </Container>
  );
}

export default AdminOrdersPage;
