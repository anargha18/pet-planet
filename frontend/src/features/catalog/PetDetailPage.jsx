import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardMedia,
  Chip,
  Container,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart';
import { useNavigate, useParams } from 'react-router-dom';
import { useCart } from '../cart';
import { extractApiError, getPetById } from '../../api';
import { petImage } from '../../components/petImage';
import StatusChip from '../../components/StatusChip';
import { humanize } from '../../components/format';

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

function PetDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const [pet, setPet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchPet = async () => {
      try {
        setLoading(true);
        const petResponse = await getPetById(id);
        setPet(petResponse);
      } catch (apiError) {
        setError(extractApiError(apiError));
      } finally {
        setLoading(false);
      }
    };

    fetchPet();
  }, [id]);

  const handleAddToCart = () => {
    if (!pet) {
      return;
    }

    addToCart(pet);
    navigate('/cart');
  };

  if (loading) {
    return (
      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Typography color="text.secondary">Loading pet details...</Typography>
      </Container>
    );
  }

  if (error) {
    return (
      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Alert severity="error">{error}</Alert>
      </Container>
    );
  }

  if (!pet) {
    return (
      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          This pet could not be found.
        </Typography>
      </Container>
    );
  }

  const isAvailable = pet.availabilityStatus === 'AVAILABLE';

  return (
    <Container maxWidth="lg" sx={{ py: 7 }}>
      <Grid container spacing={4} alignItems="center">
        <Grid item xs={12} md={6}>
          <Card>
            <CardMedia
              component="img"
              image={petImage(pet)}
              alt={pet.name}
              sx={{ height: { xs: 360, md: 560 }, objectFit: 'cover' }}
            />
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Stack spacing={2}>
            <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.16em' }}>
              {humanize(pet.species)}
            </Typography>
            <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>
              {pet.name}
            </Typography>

            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label={pet.breed} color="primary" variant="outlined" />
              <StatusChip status={pet.availabilityStatus || 'AVAILABLE'} size="medium" />
            </Stack>

            <Typography variant="h4" color="primary.main" sx={{ fontWeight: 800 }}>
              {getCurrencyLabel(pet.price)}
            </Typography>

            <Typography variant="body1" color="text.secondary">
              {pet.description}
            </Typography>

            <Box sx={{ border: '1px solid rgba(31,95,59,0.12)', borderRadius: 3, p: 2 }}>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">Age</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>{pet.age} {Number(pet.age) === 1 ? 'year' : 'years'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">Gender</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>{humanize(pet.gender)}</Typography>
                </Grid>
              </Grid>
            </Box>

            <Button
              size="large"
              variant="contained"
              startIcon={<AddShoppingCartIcon />}
              disabled={!isAvailable}
              onClick={handleAddToCart}
            >
              {isAvailable ? 'Add to Cart' : 'Unavailable'}
            </Button>
          </Stack>
        </Grid>
      </Grid>
    </Container>
  );
}

export default PetDetailPage;
