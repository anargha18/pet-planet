// Own profile (any signed-in user) and user administration (admin).
import { http } from './client';

export const getCurrentUser = async () => (await http.get('/api/v1/users/me')).data;

export const updateCurrentUser = async (profile) => (await http.patch('/api/v1/users/me', profile)).data;

export const changePassword = async (currentPassword, newPassword) => {
  await http.put('/api/v1/users/me/password', { currentPassword, newPassword });
};

export const listUsers = async (params = {}) => (await http.get('/api/v1/users', { params })).data;

export const createUser = async (user) => (await http.post('/api/v1/users', user)).data;

/** Partial update: email, firstName, lastName, role, active. */
export const updateUser = async (userId, changes) => (await http.patch(`/api/v1/users/${userId}`, changes)).data;

/** Soft deactivation (409 for the last active admin). */
export const deactivateUser = async (userId) => {
  await http.delete(`/api/v1/users/${userId}`);
};
