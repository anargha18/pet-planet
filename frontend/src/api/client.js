import axios from 'axios';

const apiGatewayBaseURL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8089';

export const petCatalogApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

export const orderApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

export const userApi = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

export const getPetCatalogApi = () => petCatalogApi;
export const getOrderApi = () => orderApi;
export const getUserApi = () => userApi;

export const getAuthHeaders = (token) => ({
  headers: token ? { Authorization: `Bearer ${token}` } : {},
});

export const extractApiError = (error) => {
  const responseData = error?.response?.data;

  if (responseData) {
    // Shared backend error body: { status, error, message, path, timestamp, correlationId }.
    // `message` is the human-readable explanation; `error` is only the HTTP reason phrase.
    if (responseData.message) return responseData.message;
    if (responseData.error) return responseData.error;
    if (responseData.details?.length) return responseData.details[0];
    if (Array.isArray(responseData)) return responseData[0];
  }

  return error?.message || 'Something went wrong. Please try again.';
};
