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

// The API returns every notification of the order (oldest first, Phase 4); the current page shows the latest one.
// Phase 5 replaces these lookups with the fulfillment tracking view and its timeline.
export const getNotificationByOrderId = async (orderId, token) => {
  const response = await orderApi.get(`/api/v1/notifications/order/${orderId}`, getAuthHeaders(token));
  const notifications = Array.isArray(response.data) ? response.data : [];
  return notifications.length > 0 ? notifications[notifications.length - 1] : null;
};
