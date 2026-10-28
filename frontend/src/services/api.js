import axios from 'axios';

const apiGatewayBaseURL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8089';

const petCatalogApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

const orderApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

const userApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

export const getPetCatalogApi = () => petCatalogApi;
export const getOrderApi = () => orderApi;
export const getUserApi = () => userApi;

export const getAuthHeaders = (token) => ({
  headers: token ? { Authorization: `Bearer ${token}` } : {},
});

export const getPets = async (params = {}) => {
  const response = await petCatalogApi.get('/api/v1/pets', { params });
  return response.data;
};

export const getPetById = async (petId) => {
  const response = await petCatalogApi.get(`/api/v1/pets/${petId}`);
  return response.data;
};

export const loginUser = async (credentials) => {
  const response = await userApi.post('/api/v1/auth/login', credentials);
  return response.data;
};

export const registerUser = async (payload) => {
  const response = await userApi.post('/api/v1/auth/register', payload);
  return response.data;
};

export const getCurrentUser = async (token) => {
  const response = await userApi.get('/api/v1/users/me', getAuthHeaders(token));
  return response.data;
};

export const createOrder = async (payload, token) => {
  const response = await orderApi.post('/api/v1/orders', payload, getAuthHeaders(token));
  return response.data;
};

export const extractApiError = (error) => {
  const responseData = error?.response?.data;

  if (responseData) {
    if (responseData.error) return responseData.error;
    if (responseData.message) return responseData.message;
    if (responseData.details?.length) return responseData.details[0];
    if (Array.isArray(responseData)) return responseData[0];
  }

  return error?.message || 'Something went wrong. Please try again.';
};
