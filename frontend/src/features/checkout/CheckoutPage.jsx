import { useState } from 'react';
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
import { useCart } from '../cart';
import { createOrder, extractApiError } from '../../api';
import { formatCurrency } from '../../components/format';
import SimulatedPaymentNote from '../../components/SimulatedPaymentNote';

function CheckoutPage() {
  const navigate = useNavigate();
  const { cartItems, subtotal, clearCart } = useCart();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

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

      const order = await createOrder(payload);
      clearCart();
      // The order page polls the saga until the order is confirmed, cancelled or failed (R-FE-03).
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (apiError) {
      setError(extractApiError(apiError));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!cartItems.length) {
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
                  <Typography variant="body1" color="text.secondary">
                    Submitting starts the checkout saga: your pets are reserved, paid for and handed to shipping.
                    You can follow every step on the order page.
                  </Typography>
                  <SimulatedPaymentNote />
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
              <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.05em', mb: 3 }}>
                Selected companions
              </Typography>
              <Stack spacing={2}>
                {cartItems.map((item) => (
                  <Box key={item.id}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography>{item.name}</Typography>
                      <Typography sx={{ fontWeight: 700 }}>{formatCurrency(item.price)}</Typography>
                    </Stack>
                  </Box>
                ))}
                <Divider />
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>Total</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{formatCurrency(subtotal)}</Typography>
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
