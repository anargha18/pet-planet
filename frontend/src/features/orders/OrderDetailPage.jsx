import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Divider,
  Grid,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Link, useLocation, useParams } from 'react-router-dom';
import { cancelOrder, extractApiError } from '../../api';
import { formatCurrency, formatDateTime, humanize } from '../../components/format';
import { ErrorState, LoadingState } from '../../components/PageState';
import SimulatedPaymentNote from '../../components/SimulatedPaymentNote';
import StatusChip from '../../components/StatusChip';
import TrackingTimeline from './TrackingTimeline';
import { useOrderTracking } from './useOrderTracking';

function StatusLine({ label, status }) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center">
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <StatusChip status={status} />
    </Stack>
  );
}

/** One order: items, live status (polled right after checkout), tracking timeline and notifications (R-FE-03). */
function OrderDetailPage() {
  const { orderId } = useParams();
  const location = useLocation();
  const justPlaced = Boolean(location.state?.justPlaced);
  const { order, tracking, notifications, error, loading, polling, reload } = useOrderTracking(orderId, { poll: true });
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState('');

  const handleCancel = async () => {
    if (!window.confirm(`Cancel order #${orderId}? Any payment taken will be refunded.`)) return;
    setCancelling(true);
    setActionError('');
    try {
      await cancelOrder(orderId);
      await reload();
    } catch (cancelError) {
      setActionError(extractApiError(cancelError));
    } finally {
      setCancelling(false);
    }
  };

  if (loading && !order) {
    return <LoadingState label="Loading your order…" />;
  }
  if (!order) {
    return (
      <Container maxWidth="md" sx={{ py: 6 }}>
        <ErrorState message={error || 'Order not found.'} onRetry={reload} />
      </Container>
    );
  }

  const summary = tracking?.summary;
  const canCancel = order.status === 'CREATED' || order.status === 'PAYMENT_PENDING';

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Button component={Link} to="/orders" size="small" sx={{ mb: 1 }}>← All orders</Button>
            <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.04em' }}>
              Order #{order.id}
            </Typography>
            <Typography variant="body2" color="text.secondary">Placed {formatDateTime(order.createdAt)}</Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <StatusChip status={order.status} size="medium" />
            {summary?.escalated && <Chip label="Needs attention" color="error" size="medium" />}
            <Button startIcon={<RefreshIcon />} variant="outlined" onClick={reload}>Refresh</Button>
            {canCancel && (
              <Button color="error" variant="outlined" disabled={cancelling} onClick={handleCancel}>
                {cancelling ? 'Cancelling…' : 'Cancel order'}
              </Button>
            )}
          </Stack>
        </Stack>

        {justPlaced && <Alert severity="success">Thanks! Your order was placed. Its progress updates live below.</Alert>}
        {polling && (
          <Box>
            <Typography variant="caption" color="text.secondary">Updating every 2 seconds…</Typography>
            <LinearProgress sx={{ mt: 0.5 }} />
          </Box>
        )}
        {!polling && (order.status === 'CREATED' || order.status === 'PAYMENT_PENDING') && (
          <Alert severity="warning">This order is taking longer than usual. Use Refresh to check again.</Alert>
        )}
        <ErrorState message={error} onRetry={reload} />
        <ErrorState message={actionError} />
        {(order.status === 'CREATED' || order.status === 'PAYMENT_PENDING') && <SimulatedPaymentNote />}

        <Grid container spacing={3}>
          <Grid item xs={12} md={7}>
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Items</Typography>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Pet</TableCell>
                      <TableCell>Species / breed</TableCell>
                      <TableCell align="right">Price</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {order.items?.map((item) => (
                      <TableRow key={item.id || item.petId}>
                        <TableCell sx={{ fontWeight: 600 }}>
                          <Link to={`/pets/${item.petId}`}>{item.petName}</Link>
                        </TableCell>
                        <TableCell>{humanize(item.petSpecies)} · {item.petBreed}</TableCell>
                        <TableCell align="right">{formatCurrency(item.subtotal ?? item.unitPrice)}</TableCell>
                      </TableRow>
                    ))}
                    <TableRow>
                      <TableCell colSpan={2} sx={{ fontWeight: 700 }}>Total</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>{formatCurrency(order.totalAmount)}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </CardContent>
            </Card>

            <Card sx={{ mt: 3 }}>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Timeline</Typography>
                {tracking ? (
                  <TrackingTimeline timeline={tracking.timeline} />
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Tracking is not available yet — it appears a moment after the order is placed.
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={5}>
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Status</Typography>
                <Stack spacing={1.5}>
                  <StatusLine label="Order" status={order.status} />
                  <StatusLine label="Payment (simulated)" status={summary?.paymentStatus} />
                  <StatusLine label="Shipment" status={summary?.shipmentStatus} />
                  <StatusLine label="Delivery" status={summary?.deliveryStatus} />
                  {summary?.trackingNumber && (
                    <Typography variant="body2">Tracking number: <strong>{summary.trackingNumber}</strong></Typography>
                  )}
                  {summary?.escalated && (
                    <Alert severity="error">Our team has been alerted ({humanize(summary.escalationReason)}).</Alert>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ mt: 3 }}>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Notifications</Typography>
                {notifications.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">No notifications yet.</Typography>
                ) : (
                  <Stack spacing={1.5} divider={<Divider flexItem />}>
                    {notifications.map((notification) => (
                      <Box key={notification.id}>
                        <Typography variant="caption" color="text.secondary">
                          {humanize(notification.type)} · {formatDateTime(notification.sentAt || notification.createdAt)}
                        </Typography>
                        <Typography variant="body2">{notification.message}</Typography>
                      </Box>
                    ))}
                  </Stack>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Stack>
    </Container>
  );
}

export default OrderDetailPage;
