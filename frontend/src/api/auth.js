// Login and registration.
import { userApi } from './client';

export const loginUser = async (credentials) => {
  const response = await userApi.post('/api/v1/auth/login', credentials);
  return response.data;
};

export const registerUser = async (payload) => {
  const response = await userApi.post('/api/v1/auth/register', payload);
  return response.data;
};
