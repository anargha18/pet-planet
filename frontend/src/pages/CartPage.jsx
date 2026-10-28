import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardMedia,
  Container,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { useCart } from '../context/CartContext';

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

function CartPage() {
  const navigate = useNavigate();
  const { cartItems, removeFromCart, subtotal } = useCart();

  if (cartItems.length === 0) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 6 }}>
            <Typography variant="h4" sx={{ fontWeight: 800, mb: 2 }}>
              Your cart is empty
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
              Add a pet you love and prepare to bring home your next companion.
            </Typography>
            <Button variant="contained" onClick={() => navigate('/pets')}>
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
        <Grid item xs={12} md={8}>
          <Stack spacing={3}>
            {cartItems.map((item) => (
              <Card key={item.id}>
                <Grid container alignItems="center">
                  <Grid item xs={12} sm={4}>
                    <CardMedia
                      component="img"
                      image={item.image || 'https://images.unsplash.com/photo-1537151625747-768eb6cf92b2?auto=format&fit=crop&w=900&q=80'}
                      alt={item.name}
                      sx={{ height: 180, objectFit: 'cover' }}
                    />
                  </Grid>
                  <Grid item xs={12} sm={8}>
                    <CardContent>
                      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography variant="h5" sx={{ fontWeight: 700 }}>
                            {item.name}
                          </Typography>
                          <Typography color="text.secondary">
                            {item.species}
                          </Typography>
                        </Box>
                        <Typography variant="h6" color="primary.main" sx={{ fontWeight: 700 }}>
                          {getCurrencyLabel(item.price)}
                        </Typography>
                      </Stack>

                      <Box sx={{ mt: 2 }}>
                        <Button
                          color="error"
                          startIcon={<DeleteOutlineIcon />}
                          onClick={() => removeFromCart(item.id)}
                        >
                          Remove
                        </Button>
                      </Box>
                    </CardContent>
                  </Grid>
                </Grid>
              </Card>
            ))}
          </Stack>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h5" sx={{ fontWeight: 800, mb: 2 }}>
                Order summary
              </Typography>

              <Stack spacing={2}>
                <Box display="flex" justifyContent="space-between">
                  <Typography color="text.secondary">Subtotal</Typography>
                  <Typography sx={{ fontWeight: 700 }}>{getCurrencyLabel(subtotal)}</Typography>
                </Box>
                <Box display="flex" justifyContent="space-between">
                  <Typography color="text.secondary">Estimated total</Typography>
                  <Typography sx={{ fontWeight: 700 }}>{getCurrencyLabel(subtotal)}</Typography>
                </Box>
              </Stack>

              <Divider sx={{ my: 3 }} />

              <Alert severity="info" sx={{ mb: 2 }}>
                Cart prices and availability are provisional and revalidated during checkout.
              </Alert>

              <Button variant="contained" fullWidth onClick={() => navigate('/checkout')} sx={{ mb: 1 }}>
                Proceed to Checkout
              </Button>
              <Button variant="outlined" fullWidth onClick={() => navigate('/pets')}>
                Continue Shopping
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

export default CartPage;
