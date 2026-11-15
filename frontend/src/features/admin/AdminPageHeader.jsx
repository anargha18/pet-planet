import { Box, Button, Stack, Typography } from '@mui/material';
import { Link } from 'react-router-dom';

/** Title row shared by the admin pages: back link, title, optional actions on the right. */
function AdminPageHeader({ title, subtitle, children }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'flex-end' }} spacing={2}>
      <Box>
        <Button component={Link} to="/admin" size="small" sx={{ mb: 1 }}>← Admin</Button>
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.04em' }}>{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
      </Box>
      {children && <Stack direction="row" spacing={1} alignItems="center">{children}</Stack>}
    </Stack>
  );
}

export default AdminPageHeader;
