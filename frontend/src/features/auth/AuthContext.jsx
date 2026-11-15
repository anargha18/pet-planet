import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { setAccessToken, setUnauthorizedHandler } from '../../api';

const AuthContext = createContext(null);
const AUTH_STORAGE_KEY = 'pet-planet-auth';

/** Expiry time (ms) from the JWT's `exp` claim, or null if it cannot be read. The token is not verified here. */
const tokenExpiry = (token) => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
};

const isExpired = (token) => {
  const expiry = tokenExpiry(token);
  return expiry !== null && expiry <= Date.now();
};

const readStoredAuth = () => {
  try {
    const stored = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY));
    return stored?.token && !isExpired(stored.token) ? stored : null;
  } catch {
    return null;
  }
};

/**
 * Signed-in user: { token, id, email, role }. The token is handed to the shared HTTP client, which attaches it to every
 * request. Expired sessions end in two ways (R-FE-01): the backend answers 401, or the token's `exp` passes while the
 * app is open. Both clear the session and redirect to /login?expired=1, remembering where the user was.
 */
export const AuthProvider = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [auth, setAuth] = useState(readStoredAuth);

  // Set synchronously during render so child effects already send the token.
  setAccessToken(auth?.token);

  useEffect(() => {
    if (auth) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
    } else {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }, [auth]);

  const expireSession = useCallback(() => {
    setAuth(null);
    setAccessToken(null);
    const here = `${location.pathname}${location.search}`;
    if (location.pathname !== '/login') {
      navigate(`/login?expired=1&next=${encodeURIComponent(here)}`, { replace: true });
    }
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    setUnauthorizedHandler(expireSession);
  }, [expireSession]);

  useEffect(() => {
    const expiry = auth?.token ? tokenExpiry(auth.token) : null;
    if (expiry === null) return undefined;
    const timer = setTimeout(expireSession, Math.max(0, expiry - Date.now()));
    return () => clearTimeout(timer);
  }, [auth?.token, expireSession]);

  /** Accepts the AuthResponse of login/register: { token, id, email, role }. */
  const login = useCallback((authResponse) => {
    const next = { token: authResponse.token, id: authResponse.id, email: authResponse.email, role: authResponse.role };
    setAccessToken(next.token);
    setAuth(next);
  }, []);

  const logout = useCallback(() => {
    setAccessToken(null);
    setAuth(null);
    navigate('/');
  }, [navigate]);

  /** Keeps the displayed email in sync after a profile change. */
  const updateEmail = useCallback((email) => setAuth((current) => (current ? { ...current, email } : current)), []);

  const value = useMemo(
    () => ({
      auth,
      token: auth?.token || null,
      user: auth ? { id: auth.id, email: auth.email, role: auth.role } : null,
      role: auth?.role || null,
      isAdmin: auth?.role === 'ADMIN',
      login,
      logout,
      updateEmail,
    }),
    [auth, login, logout, updateEmail]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
};
