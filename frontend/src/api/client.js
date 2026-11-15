// The one HTTP client for the whole app (R-FE-01). Every backend call goes through the gateway with this instance:
// - a request interceptor attaches the current bearer token;
// - a response interceptor reports a 401 on an authenticated request to the auth layer (expired or revoked token),
//   which logs out and sends the user to /login?expired=1.
import axios from 'axios';

const apiGatewayBaseURL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8089';

export const http = axios.create({
  baseURL: apiGatewayBaseURL,
  timeout: 20000,
});

let accessToken = null;
let unauthorizedHandler = null;

/** Called by the auth layer whenever the signed-in user changes (null = signed out). */
export const setAccessToken = (token) => {
  accessToken = token || null;
};

/** Called by the auth layer once; invoked when the backend rejects our token with 401. */
export const setUnauthorizedHandler = (handler) => {
  unauthorizedHandler = handler;
};

http.interceptors.request.use((config) => {
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error?.config?.headers?.Authorization);
    if (error?.response?.status === 401 && sentToken && unauthorizedHandler) {
      unauthorizedHandler();
    }
    return Promise.reject(error);
  }
);

export const httpStatus = (error) => error?.response?.status ?? null;

/** True for a 404: callers use it to show "not available yet" instead of an error. */
export const isNotFound = (error) => httpStatus(error) === 404;

/** Human-readable message from the shared backend error body { status, error, message, path, timestamp, correlationId }. */
export const extractApiError = (error) => {
  const responseData = error?.response?.data;

  if (responseData) {
    if (responseData.message) return responseData.message;
    if (responseData.error) return responseData.error;
    if (responseData.details?.length) return responseData.details[0];
    if (Array.isArray(responseData)) return responseData[0];
  }
  if (error?.code === 'ECONNABORTED') return 'The server took too long to answer. Please try again.';
  if (error?.message === 'Network Error') return 'Cannot reach the Pet Planet API. Is the stack running?';

  return error?.message || 'Something went wrong. Please try again.';
};
