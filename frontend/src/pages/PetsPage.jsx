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
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Pagination,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Link } from 'react-router-dom';
import { extractApiError, getPets } from '../services/api';

const getCurrencyLabel = (value) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(Number(value || 0));

function PetsPage() {
  const [pets, setPets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [speciesFilter, setSpeciesFilter] = useState('ALL');
  const [sortMode, setSortMode] = useState('featured');
  const [searchTerm, setSearchTerm] = useState('');

  const fetchPets = async (nextPage = 0, nextSpecies = speciesFilter) => {
    try {
      setLoading(true);
      const params = { page: nextPage, size: 12 };
      if (nextSpecies && nextSpecies !== 'ALL') {
        params.species = nextSpecies;
      }

      const response = await getPets(params);
      setPets(response?.content || []);
      setTotalPages(Math.max(response?.totalPages || 1, 1));
      setPage(response?.number || 0);
      setError('');
    } catch (apiError) {
      setError(extractApiError(apiError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPets(page, speciesFilter);
  }, [speciesFilter]);

  const filteredPets = useMemo(() => {
    const normalized = searchTerm.trim().toLowerCase();
    const result = [...pets];

    if (normalized) {
      return result.filter((pet) => pet.name.toLowerCase().includes(normalized));
    }

    return result;
  }, [pets, searchTerm]);

  const sortedPets = useMemo(() => {
    const result = [...filteredPets];

    if (sortMode === 'low') {
      result.sort((a, b) => Number(a.price) - Number(b.price));
    }

    if (sortMode === 'high') {
      result.sort((a, b) => Number(b.price) - Number(a.price));
    }

    return result;
  }, [filteredPets, sortMode]);

  const handlePageChange = (_event, nextPageIndex) => {
    const pageIndex = nextPageIndex - 1;
    setPage(pageIndex);
    fetchPets(pageIndex, speciesFilter);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: '0.14em' }}>
              Pet catalog
            </Typography>
            <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em' }}>
              Meet your next companion
            </Typography>
          </Box>
        </Stack>

        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
          <TextField
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder="Search by name"
            sx={{ minWidth: { xs: '100%', md: 280 } }}
          />

          <FormControl sx={{ minWidth: { xs: '100%', md: 160 } }}>
            <InputLabel id="species-filter-label">Species</InputLabel>
            <Select
              labelId="species-filter-label"
              id="species-filter"
              value={speciesFilter}
              label="Species"
              onChange={(event) => setSpeciesFilter(event.target.value)}
            >
              <MenuItem value="ALL">All</MenuItem>
              <MenuItem value="CAT">Cats</MenuItem>
              <MenuItem value="DOG">Dogs</MenuItem>
            </Select>
          </FormControl>

          <FormControl sx={{ minWidth: { xs: '100%', md: 180 } }}>
            <InputLabel id="sort-label">Sort by</InputLabel>
            <Select
              labelId="sort-label"
              id="sort-mode"
              value={sortMode}
              label="Sort by"
              onChange={(event) => setSortMode(event.target.value)}
            >
              <MenuItem value="featured">Featured</MenuItem>
              <MenuItem value="low">Price: Low to high</MenuItem>
              <MenuItem value="high">Price: High to low</MenuItem>
            </Select>
          </FormControl>
        </Stack>

        {error && <Alert severity="error">{error}</Alert>}

        {loading ? (
          <Typography color="text.secondary">Loading pets...</Typography>
        ) : sortedPets.length === 0 ? (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
              No pets match your filter.
            </Typography>
            <Button variant="contained" onClick={() => {
              setSearchTerm('');
              setSpeciesFilter('ALL');
              setSortMode('featured');
            }}>
              Reset filters
            </Button>
          </Box>
        ) : (
          <>
            <Grid container spacing={3}>
              {sortedPets.map((pet) => (
                <Grid item xs={12} sm={6} md={4} key={pet.id}>
                  <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <CardMedia
                      component="img"
                      image={pet.image || 'https://images.unsplash.com/photo-1537151625747-768eb6cf92b2?auto=format&fit=crop&w=900&q=80'}
                      alt={pet.name}
                      sx={{ height: 260, objectFit: 'cover' }}
                    />
                    <CardContent sx={{ flexGrow: 1 }}>
                      <Stack direction="row" justifyContent="space-between" spacing={1}>
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          {pet.name}
                        </Typography>
                        <Typography variant="h6" color="primary.main" sx={{ fontWeight: 700 }}>
                          {getCurrencyLabel(pet.price)}
                        </Typography>
                      </Stack>
                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {pet.species} • {pet.breed}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {pet.availabilityStatus || 'AVAILABLE'}
                      </Typography>
                    </CardContent>
                    <CardActions>
                      <Button component={Link} to={`/pets/${pet.id}`} variant="contained" fullWidth>
                        View details
                      </Button>
                    </CardActions>
                  </Card>
                </Grid>
              ))}
            </Grid>

            <Box sx={{ display: 'flex', justifyContent: 'center', pt: 4 }}>
              <Pagination
                page={page}
                count={Math.max(totalPages, 1)}
                color="primary"
                onChange={handlePageChange}
              />
            </Box>
          </>
        )}
      </Stack>
    </Container>
  );
}

export default PetsPage;
