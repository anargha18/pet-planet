// Orders. Users only ever see their own; admins may filter by customerId and status.
import { http } from './client';

export const createOrder = async (payload) => (await http.post('/api/v1/orders', payload)).data;

export const getOrder = async (orderId) => (await http.get(`/api/v1/orders/${orderId}`)).data;

export const getOrders = async (params = {}) => (await http.get('/api/v1/orders', { params })).data;

export const cancelOrder = async (orderId) => (await http.patch(`/api/v1/orders/${orderId}/cancel`, {})).data;
