import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth';

/** Signed-in users only (R-FE-02); others go to the login page and come back afterwards. */
function ProtectedRoute() {
  const { token } = useAuth();
  const location = useLocation();
  if (!token) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <Outlet />;
}

export default ProtectedRoute;
