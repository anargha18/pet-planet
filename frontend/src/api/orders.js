// Orders.
import { getAuthHeaders, orderApi } from './client';

export const createOrder = async (payload, token) => {
  const response = await orderApi.post('/api/v1/orders', payload, getAuthHeaders(token));
  return response.data;
};

export const getOrders = async (token, params = {}) => {
  const response = await orderApi.get('/api/v1/orders', {
    ...getAuthHeaders(token),
    params,
  });
  return response.data;
};

export const cancelOrder = async (orderId, token) => {
  const response = await orderApi.patch(`/api/v1/orders/${orderId}/cancel`, {}, getAuthHeaders(token));
  return response.data;
};
