import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Collapse,
  Container,
  Divider,
  Grid,
  IconButton,
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
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import RefreshIcon from '@mui/icons-material/Refresh';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import PaymentOutlinedIcon from '@mui/icons-material/PaymentOutlined';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  cancelOrder,
  extractApiError,
  getNotificationByOrderId,
  getOrders,
  getPaymentByOrderId,
  getShipmentByOrderId,
} from '../services/api';

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

const getStatusColor = (status) => {
  switch (status) {
    case 'PAID':
    case 'CONFIRMED':
    case 'DELIVERED':
      return 'success';
    case 'PAYMENT_PENDING':
    case 'IN_TRANSIT':
    case 'SHIPPED':
      return 'warning';
    case 'CREATED':
    case 'PACKAGED':
      return 'info';
    case 'FAILED':
      return 'error';
    case 'CANCELLED':
    default:
      return 'default';
  }
};

function OrderRow({ order, token, onOrderCancelled }) {
  const [open, setOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState('');
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingData, setTrackingData] = useState({
    payment: null,
    shipment: null,
    notification: null,
  });

  const loadTrackingDetails = async () => {
    setTrackingLoading(true);
    const results = { payment: null, shipment: null, notification: null };
    try {
      results.payment = await getPaymentByOrderId(order.id).catch(() => null);
    } catch {
      // not available yet
    }
    try {
      results.shipment = await getShipmentByOrderId(order.id).catch(() => null);
    } catch {
      // not available yet
    }
    try {
      results.notification = await getNotificationByOrderId(order.id).catch(() => null);
    } catch {
      // not available yet
    }
    setTrackingData(results);
    setTrackingLoading(false);
  };

  const handleToggleExpand = () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen) {
      loadTrackingDetails();
    }
  };

  const handleCancel = async () => {
    if (!window.confirm(`Are you sure you want to cancel Order #${order.id}?`)) {
      return;
    }
    setCancelling(true);
    setActionError('');
    try {
      await cancelOrder(order.id, token);
      if (onOrderCancelled) {
        onOrderCancelled(order.id);
      }
    } catch (err) {
      setActionError(extractApiError(err));
    } finally {
      setCancelling(false);
    }
  };

  const canCancel = order.status === 'CREATED' || order.status === 'PAYMENT_PENDING';

  return (
    <>
      <TableRow sx={{ '& > *': { borderBottom: 'unset' } }}>
        <TableCell>
          <IconButton size="small" onClick={handleToggleExpand}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell component="th" scope="row" sx={{ fontWeight: 700 }}>
          #{order.id}
        </TableCell>
        <TableCell>
          {order.createdAt ? new Date(order.createdAt).toLocaleString() : 'N/A'}
        </TableCell>
        <TableCell sx={{ fontWeight: 700 }}>
          {getCurrencyLabel(order.totalAmount)}
        </TableCell>
        <TableCell>
          <Chip label={order.status} color={getStatusColor(order.status)} size="small" />
        </TableCell>
        <TableCell align="right">
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button size="small" variant="outlined" onClick={handleToggleExpand}>
              {open ? 'Hide details' : 'Track & Details'}
            </Button>
            {canCancel && (
              <Button
                size="small"
                variant="outlined"
                color="error"
                disabled={cancelling}
                onClick={handleCancel}
              >
                {cancelling ? 'Cancelling...' : 'Cancel'}
              </Button>
            )}
          </Stack>
        </TableCell>
      </TableRow>

      <TableRow>
        <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={6}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ margin: 2 }}>
              {actionError && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {actionError}
                </Alert>
              )}

              <Grid container spacing={3}>
                <Grid item xs={12} md={7}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                    Ordered Items
                  </Typography>
                  <Paper variant="outlined" sx={{ mb: 2 }}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Pet Name</TableCell>
                          <TableCell>Species / Breed</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell align="right">Unit Price</TableCell>
                          <TableCell align="right">Subtotal</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {order.items?.map((item) => (
                          <TableRow key={item.id || item.petId}>
                            <TableCell sx={{ fontWeight: 600 }}>{item.petName}</TableCell>
                            <TableCell>{item.petSpecies} • {item.petBreed}</TableCell>
                            <TableCell align="right">{item.quantity}</TableCell>
                            <TableCell align="right">{getCurrencyLabel(item.unitPrice)}</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>
                              {getCurrencyLabel(item.subtotal)}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Paper>
                </Grid>

                <Grid item xs={12} md={5}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                      Live Saga & Tracking Status
                    </Typography>
                    <IconButton size="small" onClick={loadTrackingDetails} disabled={trackingLoading}>
                      <RefreshIcon fontSize="small" />
                    </IconButton>
                  </Stack>

                  {trackingLoading ? (
                    <Box display="flex" justifyContent="center" py={3}>
                      <CircularProgress size={24} />
                    </Box>
                  ) : (
                    <Stack spacing={1.5}>
                      {/* Payment Card */}
                      <Card variant="outlined">
                        <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                            <PaymentOutlinedIcon fontSize="small" color="primary" />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>Payment</Typography>
                            {trackingData.payment && (
                              <Chip
                                label={trackingData.payment.status}
                                color={getStatusColor(trackingData.payment.status)}
                                size="small"
                                sx={{ ml: 'auto' }}
                              />
                            )}
                          </Stack>
                          {trackingData.payment ? (
                            <Typography variant="caption" color="text.secondary">
                              Ref: {trackingData.payment.transactionReference || 'N/A'} • Amount:{' '}
                              {getCurrencyLabel(trackingData.payment.amount)}
                            </Typography>
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              Awaiting payment confirmation...
                            </Typography>
                          )}
                        </CardContent>
                      </Card>

                      {/* Shipment Card */}
                      <Card variant="outlined">
                        <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                            <LocalShippingOutlinedIcon fontSize="small" color="primary" />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>Shipment & Delivery</Typography>
                            {trackingData.shipment && (
                              <Chip
                                label={trackingData.shipment.status}
                                color={getStatusColor(trackingData.shipment.status)}
                                size="small"
                                sx={{ ml: 'auto' }}
                              />
                            )}
                          </Stack>
                          {trackingData.shipment ? (
                            <Typography variant="caption" color="text.secondary">
                              Tracking #{trackingData.shipment.trackingNumber || 'N/A'}
                            </Typography>
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              Pending shipment generation...
                            </Typography>
                          )}
                        </CardContent>
                      </Card>

                      {/* Notification Card */}
                      <Card variant="outlined">
                        <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                            <NotificationsOutlinedIcon fontSize="small" color="primary" />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>Customer Notification</Typography>
                            {trackingData.notification && (
                              <Chip
                                label={trackingData.notification.status}
                                color={getStatusColor(trackingData.notification.status)}
                                size="small"
                                sx={{ ml: 'auto' }}
                              />
                            )}
                          </Stack>
                          {trackingData.notification ? (
                            <Typography variant="caption" color="text.secondary">
                              {trackingData.notification.message || 'Notification queued'}
                            </Typography>
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              Awaiting notification trigger...
                            </Typography>
                          )}
                        </CardContent>
                      </Card>
                    </Stack>
                  )}
                </Grid>
              </Grid>
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

function OrdersPage() {
  const { token } = useAuth();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadOrders = async () => {
    if (!token) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const data = await getOrders(token);
      setOrders(data?.content || data || []);
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, [token]);

  const handleOrderCancelled = () => {
    loadOrders();
  };

  if (!token) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Card sx={{ textAlign: 'center', p: 4 }}>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 2 }}>
            Sign in to view your orders
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            Track order fulfillment, payment verification, and shipment progress in real time.
          </Typography>
          <Button component={Link} to="/login" variant="contained">
            Sign In
          </Button>
        </Card>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems="center" spacing={2}>
          <Box>
            <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.14em' }}>
              Order Management
            </Typography>
            <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>
              My Orders & Tracking
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadOrders}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>

        {error && <Alert severity="error">{error}</Alert>}

        {loading ? (
          <Box display="flex" justifyContent="center" py={8}>
            <CircularProgress />
          </Box>
        ) : orders.length === 0 ? (
          <Card sx={{ textAlign: 'center', py: 6 }}>
            <Typography variant="h6" sx={{ mb: 1, fontWeight: 700 }}>
              No orders found
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              You haven't placed any orders yet. Discover your new best companion in our catalog!
            </Typography>
            <Button component={Link} to="/pets" variant="contained">
              Browse Pets
            </Button>
          </Card>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table aria-label="orders table">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 48 }} />
                  <TableCell sx={{ fontWeight: 700 }}>Order ID</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Date Placed</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Total Amount</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map((order) => (
                  <OrderRow
                    key={order.id}
                    order={order}
                    token={token}
                    onOrderCancelled={handleOrderCancelled}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
    </Container>
  );
}

export default OrdersPage;

