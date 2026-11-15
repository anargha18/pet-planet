import { Card, CardActionArea, CardContent, Container, Grid, Typography } from '@mui/material';
import { Link } from 'react-router-dom';

const sections = [
  { path: '/admin/pets', title: 'Pets', text: 'Create, edit and archive catalog pets. Availability comes from inventory.' },
  { path: '/admin/inventory', title: 'Inventory', text: 'Read-only availability: reservations, expiry times and sales.' },
  { path: '/admin/orders', title: 'Orders', text: 'All customers’ orders, filtered by status, with full tracking.' },
  { path: '/admin/deliveries', title: 'Deliveries', text: 'Advance shipments: out for delivery, delivered or failed.' },
  { path: '/admin/users', title: 'Users', text: 'Create users, change roles, activate or deactivate accounts.' },
];

/** Entry point of the admin area (R-FE-04). */
function AdminHomePage() {
  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.05em', mb: 3 }}>Admin</Typography>
      <Grid container spacing={3}>
        {sections.map((section) => (
          <Grid item xs={12} sm={6} md={4} key={section.path}>
            <Card sx={{ height: '100%' }}>
              <CardActionArea component={Link} to={section.path} sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{section.title}</Typography>
                  <Typography variant="body2" color="text.secondary">{section.text}</Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Container>
  );
}

export default AdminHomePage;
