import { useState } from 'react';
import {
  Alert,
  Button,
  Chip,
  Container,
  FormControlLabel,
  Pagination,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { Link } from 'react-router-dom';
import { advanceDelivery, extractApiError, nextDeliveryStatuses, searchTracking } from '../../../api';
import { formatDateTime, humanize } from '../../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../../components/PageState';
import StatusChip from '../../../components/StatusChip';
import AdminPageHeader from '../AdminPageHeader';
import { usePagedQuery } from '../usePagedQuery';

const ACTION_COLOR = { OUT_FOR_DELIVERY: 'primary', DELIVERED: 'success', DELIVERY_FAILED: 'error' };

/**
 * Deliveries (R-FE-04): orders from the tracking projection, with the single "advance delivery" admin action
 * (PUT /api/v1/deliveries/order/{id}/status). Shipping, notification and tracking follow through events.
 */
function AdminDeliveriesPage() {
  const [pageIndex, setPageIndex] = useState(0);
  const [onlyEscalated, setOnlyEscalated] = useState(false);
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(null);
  const { page, rows, error, loading, reload } = usePagedQuery(searchTracking, {
    page: pageIndex, size: 50, ...(onlyEscalated && { escalated: true }),
  });
  const shipped = rows.filter((row) => row.shipmentStatus || row.deliveryStatus);

  const advance = async (row, status) => {
    const note = status === 'DELIVERY_FAILED'
      ? window.prompt('Why did the delivery fail? (optional, shown to the customer)', '')
      : '';
    if (note === null) return; // prompt cancelled
    setBusy(row.orderId);
    try {
      await advanceDelivery(row.orderId, status, note);
      setMessage({ severity: 'success', text: `Order #${row.orderId}: ${humanize(status)}. Tracking updates in a moment.` });
      setTimeout(reload, 1500); // the projection updates asynchronously from the delivery event
    } catch (advanceError) {
      setMessage({ severity: 'error', text: `Order #${row.orderId}: ${extractApiError(advanceError)}` });
    } finally {
      setBusy(null);
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <AdminPageHeader title="Deliveries" subtitle="Orders that reached shipping, most recently updated first.">
          <FormControlLabel
            control={<Switch checked={onlyEscalated} onChange={(event) => { setOnlyEscalated(event.target.checked); setPageIndex(0); }} />}
            label="Escalated only"
          />
          <Button variant="outlined" onClick={reload}>Refresh</Button>
        </AdminPageHeader>
        {message && <Alert severity={message.severity} onClose={() => setMessage(null)}>{message.text}</Alert>}
        <ErrorState message={error} onRetry={reload} />
        {loading ? <LoadingState /> : !error && shipped.length === 0 ? (
          <EmptyState title={onlyEscalated ? 'No escalated shipments' : 'No shipments on this page'}>
            Shipments appear here once an order is confirmed.
          </EmptyState>
        ) : shipped.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Tracking number</TableCell>
                  <TableCell>Shipment</TableCell>
                  <TableCell>Delivery</TableCell>
                  <TableCell>Updated</TableCell>
                  <TableCell align="right">Advance delivery</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {shipped.map((row) => (
                  <TableRow key={row.orderId} hover>
                    <TableCell sx={{ fontWeight: 700 }}><Link to={`/orders/${row.orderId}`}>#{row.orderId}</Link></TableCell>
                    <TableCell>{row.customerId ?? '—'}</TableCell>
                    <TableCell>{row.trackingNumber || '—'}</TableCell>
                    <TableCell><StatusChip status={row.shipmentStatus} /></TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1}>
                        <StatusChip status={row.deliveryStatus} />
                        {row.escalated && <Chip size="small" color="error" label="Escalated" title={humanize(row.escalationReason)} />}
                      </Stack>
                    </TableCell>
                    <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {nextDeliveryStatuses(row.deliveryStatus).map((status) => (
                          <Button key={status} size="small" variant="outlined" color={ACTION_COLOR[status]}
                            disabled={busy === row.orderId} onClick={() => advance(row, status)}>
                            {humanize(status)}
                          </Button>
                        ))}
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
    </Container>
  );
}

export default AdminDeliveriesPage;
