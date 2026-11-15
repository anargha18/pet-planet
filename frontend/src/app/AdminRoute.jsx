import { Container } from '@mui/material';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { EmptyState } from '../components/PageState';
import { useAuth } from '../features/auth';

/** role === 'ADMIN' only (R-FE-02). The backend enforces the same rule; this only avoids showing dead pages. */
function AdminRoute() {
  const { token, isAdmin } = useAuth();
  const location = useLocation();
  if (!token) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }
  if (!isAdmin) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <EmptyState title="Admins only">This area needs an administrator account.</EmptyState>
      </Container>
    );
  }
  return <Outlet />;
}

export default AdminRoute;
