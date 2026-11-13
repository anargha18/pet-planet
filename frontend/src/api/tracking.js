// Order tracking lookups (payment, shipment, notification).
import { getAuthHeaders, orderApi } from './client';

export const getPaymentByOrderId = async (orderId, token) => {
  const response = await orderApi.get(`/api/v1/payments/order/${orderId}`, getAuthHeaders(token));
  return response.data;
};

export const getShipmentByOrderId = async (orderId, token) => {
  const response = await orderApi.get(`/api/v1/shipments/order/${orderId}`, getAuthHeaders(token));
  return response.data;
};

export const getNotificationByOrderId = async (orderId, token) => {
  const response = await orderApi.get(`/api/v1/notifications/order/${orderId}`, getAuthHeaders(token));
  return response.data;
};
