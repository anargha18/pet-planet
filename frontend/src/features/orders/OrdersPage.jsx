import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Container,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Link } from 'react-router-dom';
import { extractApiError, getMyTracking, getOrders } from '../../api';
import { formatCurrency, formatDateTime } from '../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../components/PageState';
import StatusChip from '../../components/StatusChip';

/**
 * The customer's orders with their tracking state. Two calls in total: the orders, and every tracking summary from
 * the fulfillment projection (R-FE-03). Details and the timeline are on the order page.
 */
function OrdersPage() {
  const [orders, setOrders] = useState([]);
  const [trackingByOrder, setTrackingByOrder] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [trackingError, setTrackingError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    setTrackingError('');
    try {
      const page = await getOrders({ size: 100, sort: 'createdAt,desc' });
      setOrders(page?.content || []);
    } catch (loadError) {
      setError(extractApiError(loadError));
    }
    try {
      const tracking = await getMyTracking();
      setTrackingByOrder(Object.fromEntries((tracking?.content || []).map((t) => [t.orderId, t])));
    } catch (loadError) {
      setTrackingError(`Tracking is unavailable right now: ${extractApiError(loadError)}`);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems="center" spacing={2}>
          <Box>
            <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.14em' }}>
              Orders
            </Typography>
            <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>My orders &amp; tracking</Typography>
          </Box>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={load} disabled={loading}>Refresh</Button>
        </Stack>

        <ErrorState message={error} onRetry={load} />
        {!error && trackingError && <ErrorState message={trackingError} onRetry={load} />}

        {loading ? (
          <LoadingState label="Loading your orders…" />
        ) : !error && orders.length === 0 ? (
          <EmptyState
            title="No orders yet"
            action={<Button component={Link} to="/pets" variant="contained">Browse pets</Button>}
          >
            Discover your new best companion in our catalog.
          </EmptyState>
        ) : orders.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table aria-label="orders">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Order</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Placed</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Total</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Payment</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Delivery</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map((order) => {
                  const tracking = trackingByOrder[order.id];
                  return (
                    <TableRow key={order.id} hover>
                      <TableCell sx={{ fontWeight: 700 }}>#{order.id}</TableCell>
                      <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                      <TableCell>{formatCurrency(order.totalAmount)}</TableCell>
                      <TableCell><StatusChip status={order.status} /></TableCell>
                      <TableCell><StatusChip status={tracking?.paymentStatus} emptyLabel="—" /></TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1}>
                          <StatusChip status={tracking?.deliveryStatus || tracking?.shipmentStatus} emptyLabel="—" />
                          {tracking?.escalated && <Chip label="Needs attention" color="error" size="small" />}
                        </Stack>
                      </TableCell>
                      <TableCell align="right">
                        <Button component={Link} to={`/orders/${order.id}`} size="small" variant="outlined">
                          Details
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
    </Container>
  );
}

export default OrdersPage;
