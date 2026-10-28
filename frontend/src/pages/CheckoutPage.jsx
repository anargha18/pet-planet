import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Container,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { createOrder, extractApiError, getCurrentUser } from '../services/api';

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

function CheckoutPage() {
  const navigate = useNavigate();
  const { token } = useAuth();
  const { cartItems, subtotal, clearCart } = useCart();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [orderResult, setOrderResult] = useState(null);

  useEffect(() => {
    if (!token) {
      return;
    }

    const loadCurrentUser = async () => {
      try {
        await getCurrentUser(token);
      } catch (apiError) {
        setError(extractApiError(apiError));
      }
    };

    loadCurrentUser();
  }, [token]);

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!cartItems.length) {
      setError('Your cart is empty. Add a pet before checking out.');
      return;
    }

    setIsSubmitting(true);
    setError('');

    try {
      const payload = {
        items: cartItems.map((item) => ({
          petId: item.id,
          quantity: 1,
        })),
      };

      const response = await createOrder(payload, token);
      setOrderResult(response);
      clearCart();
    } catch (apiError) {
      setError(extractApiError(apiError));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!cartItems.length && !orderResult) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 6 }}>
            <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
              Your cart is empty.
            </Typography>
            <Button variant="contained" onClick={() => navigate('/pets')}>
              Browse pets
            </Button>
          </CardContent>
        </Card>
      </Container>
    );
  }

  if (orderResult) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Card>
          <CardContent sx={{ p: 4 }}>
            <Typography variant="overline" color="success.main" sx={{ fontWeight: 700, letterSpacing: '0.16em' }}>
              Order created
            </Typography>
            <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.05em', mb: 2 }}>
              Thanks for your purchase
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Your order was successfully created in the backend order service.
            </Typography>
            <Stack spacing={1}>
              <Typography><strong>Order ID:</strong> {orderResult.id}</Typography>
              <Typography><strong>Status:</strong> {orderResult.status}</Typography>
              <Typography><strong>Total:</strong> {getCurrencyLabel(orderResult.totalAmount)}</Typography>
            </Stack>
            <Button variant="contained" sx={{ mt: 3 }} onClick={() => navigate('/pets')}>
              Continue shopping
            </Button>
          </CardContent>
        </Card>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 8 }}>
      <Grid container spacing={4}>
        <Grid item xs={12} md={7}>
          <Card>
            <CardContent sx={{ p: 4 }}>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.05em', mb: 2 }}>
                Checkout
              </Typography>

              <Box component="form" onSubmit={handleSubmit} noValidate>
                <Stack spacing={3}>
                  {error && <Alert severity="error">{error}</Alert>}

                  <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
                    {isSubmitting ? (
                      <>
                        <CircularProgress size={18} sx={{ mr: 1, color: 'white' }} />
                        Creating order...
                      </>
                    ) : (
                      'Submit order'
                    )}
                  </Button>
                </Stack>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={5}>
          <Card>
            <CardContent sx={{ p: 4 }}>
              <Typography variant="h5" sx={{ fontWeight: 800, mb: 3 }}>
                Selected companions
              </Typography>
              <Stack spacing={2}>
                {cartItems.map((item) => (
                  <Box key={item.id}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography>{item.name}</Typography>
                      <Typography sx={{ fontWeight: 700 }}>{getCurrencyLabel(item.price)}</Typography>
                    </Stack>
                  </Box>
                ))}
                <Divider />
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>Total</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{getCurrencyLabel(subtotal)}</Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

export default CheckoutPage;
