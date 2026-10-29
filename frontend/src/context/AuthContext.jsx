import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { getCurrentUser } from '../services/api';

const AuthContext = createContext(null);

const AUTH_STORAGE_KEY = 'pet-planet-auth';

export const AuthProvider = ({ children }) => {
  const [auth, setAuth] = useState(() => {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  });

  useEffect(() => {
    if (auth) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
    } else {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }, [auth]);

  const login = (nextAuth) => {
    setAuth(nextAuth);
  };

  const logout = () => {
    setAuth(null);
  };

  const refreshProfile = async () => {
    if (!auth?.token) {
      return null;
    }

    try {
      const profile = await getCurrentUser(auth.token);
      setAuth((previous) => ({
        ...previous,
        user: profile,
      }));
      return profile;
    } catch (error) {
      setAuth(null);
      return null;
    }
  };

  const value = useMemo(
    () => ({
      auth,
      token: auth?.token || null,
      user: auth?.user || null,
      role: auth?.role || null,
      login,
      logout,
      refreshProfile,
    }),
    [auth]
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
