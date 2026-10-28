import { useState } from 'react';
import {
  AppBar,
  Box,
  Button,
  Chip,
  Container,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

const navItems = [
  { label: 'Home', path: '/' },
  { label: 'Browse Pets', path: '/pets' },
  { label: 'Cart', path: '/cart' },
];

function Layout() {
  const { auth, logout } = useAuth();
  const { cartCount } = useCart();
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <>
      <AppBar position="sticky" color="default" elevation={0}>
        <Container maxWidth="lg">
          <Toolbar disableGutters sx={{ minHeight: 82, gap: 2 }}>
            <Box
              component={Link}
              to="/"
              sx={{ display: 'flex', alignItems: 'center', gap: 1, flexGrow: 1, textDecoration: 'none', color: 'inherit' }}
            >
              <Box
                sx={{
                  width: 42,
                  height: 42,
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg, #1f5f3b, #e77a46)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#fff',
                  fontWeight: 800,
                }}
              >
                P
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800, letterSpacing: '-0.04em' }}>
                  Pet Planet
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Find Your Perfect Companion
                </Typography>
              </Box>
            </Box>

            <Stack direction="row" spacing={2} sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center' }}>
              {navItems.map((item) => (
                <Button key={item.path} component={Link} to={item.path} color="inherit" sx={{ fontWeight: 600 }}>
                  {item.label}
                </Button>
              ))}

              <Button
                component={Link}
                to="/cart"
                startIcon={<ShoppingCartOutlinedIcon />}
                color="inherit"
                sx={{ position: 'relative', fontWeight: 600 }}
              >
                Cart
                {cartCount > 0 && (
                  <Chip
                    label={cartCount}
                    size="small"
                    sx={{
                      minWidth: 22,
                      height: 22,
                      ml: 1,
                      backgroundColor: 'secondary.main',
                      color: '#fff',
                      '.MuiChip-label': { px: 0.9 },
                    }}
                  />
                )}
              </Button>

              {auth ? (
                <>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                    {auth.user?.email || 'Pet lover'}
                  </Typography>
                  <Button variant="outlined" onClick={logout}>
                    Logout
                  </Button>
                </>
              ) : (
                <>
                  <Button component={Link} to="/login" variant="outlined">
                    Login
                  </Button>
                  <Button component={Link} to="/register" variant="contained">
                    Register
                  </Button>
                </>
              )}
            </Stack>

            <IconButton
              edge="end"
              color="inherit"
              sx={{ display: { xs: 'flex', md: 'none' } }}
              onClick={() => setDrawerOpen(true)}
            >
              <MenuIcon />
            </IconButton>
          </Toolbar>
        </Container>
      </AppBar>

      <Drawer anchor="right" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 260, p: 2 }}>
          <Stack spacing={1}>
            {navItems.map((item) => (
              <ListItem key={item.path} disablePadding>
                <ListItemButton component={Link} to={item.path} onClick={() => setDrawerOpen(false)}>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
            <Divider sx={{ my: 1 }} />
            {auth ? (
              <>
                <Typography variant="body2" sx={{ px: 2, py: 1, color: 'text.secondary' }}>
                  {auth.user?.email || 'Pet lover'}
                </Typography>
                <Button variant="outlined" onClick={logout} sx={{ mx: 2 }}>
                  Logout
                </Button>
              </>
            ) : (
              <>
                <Button component={Link} to="/login" variant="outlined" sx={{ mx: 2 }} onClick={() => setDrawerOpen(false)}>
                  Login
                </Button>
                <Button component={Link} to="/register" variant="contained" sx={{ mx: 2 }} onClick={() => setDrawerOpen(false)}>
                  Register
                </Button>
              </>
            )}
          </Stack>
        </Box>
      </Drawer>

      <Box component="main" sx={{ minHeight: 'calc(100vh - 120px)' }}>
        <Outlet />
      </Box>

      <Box component="footer" sx={{ borderTop: '1px solid rgba(31,95,59,0.12)', backgroundColor: 'rgba(255,255,255,0.5)' }}>
        <Container maxWidth="lg" sx={{ py: 5 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={3}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Pet Planet
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Find Your Perfect Companion
              </Typography>
            </Box>
            <Stack direction="row" spacing={3} flexWrap="wrap">
              <Button component={Link} to="/" color="inherit">Home</Button>
              <Button component={Link} to="/pets" color="inherit">Browse Pets</Button>
              <Button component={Link} to="/login" color="inherit">Login</Button>
              <Button component={Link} to="/register" color="inherit">Register</Button>
            </Stack>
          </Stack>
        </Container>
      </Box>
    </>
  );
}

export default Layout;
