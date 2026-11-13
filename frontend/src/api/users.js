// Current user profile.
import { getAuthHeaders, userApi } from './client';

export const getCurrentUser = async (token) => {
  const response = await userApi.get('/api/v1/users/me', getAuthHeaders(token));
  return response.data;
};
