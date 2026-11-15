// Login and registration (public).
import { http } from './client';

export const loginUser = async (credentials) => (await http.post('/api/v1/auth/login', credentials)).data;

export const registerUser = async (payload) => (await http.post('/api/v1/auth/register', payload)).data;
