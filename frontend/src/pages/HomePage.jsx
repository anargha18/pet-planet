import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  CardMedia,
  Container,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { Link } from 'react-router-dom';
import { extractApiError, getPets } from '../services/api';

const categories = [
  {
    name: 'Cats',
    species: 'CAT',
    image: 'https://images.unsplash.com/photo-1511044568932-338cba0ad803?auto=format&fit=crop&w=900&q=80',
  },
  {
    name: 'Dogs',
    species: 'DOG',
    image: 'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=900&q=80',
  },
];

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

function HomePage() {
  const [featuredPets, setFeaturedPets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchFeaturedPets = async () => {
      try {
        const response = await getPets({ page: 0, size: 4 });
        const items = response?.content || [];
        setFeaturedPets(items);
      } catch (apiError) {
        setError(extractApiError(apiError));
      } finally {
        setLoading(false);
      }
    };

    fetchFeaturedPets();
  }, []);

  const categoryCards = useMemo(
    () => categories.map((category) => ({
      ...category,
      href: `/pets?species=${category.species}`,
    })),
    []
  );

  return (
    <>
      <Box
        sx={{
          background: 'linear-gradient(135deg, rgba(31,95,59,0.08), rgba(231,122,70,0.08))',
          py: { xs: 6, md: 10 },
        }}
      >
        <Container maxWidth="lg">
          <Grid container spacing={4} alignItems="center">
            <Grid item xs={12} md={6}>
              <Stack spacing={3}>
                <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.18em' }}>
                  Thoughtful care. Happy tails.
                </Typography>
                <Typography variant="h2" sx={{ fontWeight: 800, letterSpacing: '-0.05em', maxWidth: 580 }}>
                  Find Your Perfect Companion
                </Typography>
                <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 540 }}>
                  Explore healthy, friendly pets from trusted breeders and rescues—and bring home a companion that fits your home, your routine, and your heart.
                </Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <Button component={Link} to="/pets" variant="contained" size="large" endIcon={<ArrowForwardIcon />}>
                    Explore Pets
                  </Button>
                  <Button component={Link} to="/register" variant="outlined" size="large">
                    Create an account
                  </Button>
                </Stack>
              </Stack>
            </Grid>
            <Grid item xs={12} md={6}>
              <Box
                sx={{
                  position: 'relative',
                  overflow: 'hidden',
                  borderRadius: 5,
                  boxShadow: '0 28px 60px rgba(31,95,59,0.15)',
                }}
              >
                <CardMedia
                  component="img"
                  image="https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=1200&q=80"
                  alt="Happy dog"
                  sx={{ height: { xs: 370, md: 470 }, objectFit: 'cover' }}
                />
              </Box>
            </Grid>
          </Grid>
        </Container>
      </Box>

      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Stack spacing={3}>
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.04em' }}>
            Shop by companion type
          </Typography>
          <Grid container spacing={3}>
            {categoryCards.map((category) => (
              <Grid item xs={12} sm={6} key={category.name}>
                <Card component={Link} to={category.href} sx={{ height: '100%', display: 'block' }}>
                  <CardMedia component="img" image={category.image} alt={category.name} sx={{ height: 220, objectFit: 'cover' }} />
                  <CardContent>
                    <Typography variant="h5" sx={{ fontWeight: 700 }}>
                      {category.name}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Browse available {category.name.toLowerCase()} companions.
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Stack>
      </Container>

      <Container maxWidth="lg" sx={{ pb: 8 }}>
        <Stack spacing={3}>
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.04em' }}>
            Featured companions
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          {loading ? (
            <Typography color="text.secondary">Loading featured companions...</Typography>
          ) : (
            <Grid container spacing={3}>
              {featuredPets.map((pet) => (
                <Grid item xs={12} sm={6} md={3} key={pet.id}>
                  <Card sx={{ height: '100%' }}>
                    <CardMedia
                      component="img"
                      image={pet.image || 'https://images.unsplash.com/photo-1537151625747-768eb6cf92b2?auto=format&fit=crop&w=900&q=80'}
                      alt={pet.name}
                      sx={{ height: 220, objectFit: 'cover' }}
                    />
                    <CardContent>
                      <Stack direction="row" justifyContent="space-between" spacing={1}>
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          {pet.name}
                        </Typography>
                        <Typography variant="h6" color="primary.main" sx={{ fontWeight: 700 }}>
                          {getCurrencyLabel(pet.price)}
                        </Typography>
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {pet.species} • {pet.breed}
                      </Typography>
                    </CardContent>
                    <CardActions>
                      <Button component={Link} to={`/pets/${pet.id}`} size="small" variant="contained">
                        View details
                      </Button>
                    </CardActions>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
        </Stack>
      </Container>

      <Box sx={{ backgroundColor: 'rgba(31,95,59,0.04)', py: 8 }}>
        <Container maxWidth="lg">
          <Grid container spacing={4}>
            <Grid item xs={12} md={4}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
                Trusted support
              </Typography>
              <Typography color="text.secondary">
                We help you match with the right companion and provide transparent guidance on care, temperament, and fit.
              </Typography>
            </Grid>
            <Grid item xs={12} md={4}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
                Healthy pets
              </Typography>
              <Typography color="text.secondary">
                Every listing is tracked through the catalog and inventory services so availability reflects live backend status.
              </Typography>
            </Grid>
            <Grid item xs={12} md={4}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
                Easy checkout
              </Typography>
              <Typography color="text.secondary">
                Add your favorite companion to the cart, review your order, and complete checkout with the real order API.
              </Typography>
            </Grid>
          </Grid>
        </Container>
      </Box>
    </>
  );
}

export default HomePage;
