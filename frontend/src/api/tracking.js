// Order tracking. The fulfillment projection is the one tracking view (summary + timeline); payment and notifications
// are read from their owning services for details.
import { http } from './client';

/** { summary, timeline[] } for one order; 404 until the projection has seen the order. */
export const getOrderTracking = async (orderId) => (await http.get(`/api/v1/fulfillment/orders/${orderId}`)).data;

/** Page of the caller's tracking summaries. */
export const getMyTracking = async (params = {}) =>
  (await http.get('/api/v1/fulfillment/orders/me', { params: { size: 100, ...params } })).data;

/** Admin: page of all tracking summaries, optional customerId / escalated filters. */
export const searchTracking = async (params = {}) => (await http.get('/api/v1/fulfillment/orders', { params })).data;

export const getPaymentByOrderId = async (orderId) => (await http.get(`/api/v1/payments/order/${orderId}`)).data;

/** All notifications of an order, oldest first ([] if none yet). */
export const getNotificationsForOrder = async (orderId) =>
  (await http.get(`/api/v1/notifications/order/${orderId}`)).data;
