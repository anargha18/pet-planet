// Delivery tracking: carrier progress and the single admin "advance delivery" action.
import { http } from './client';

export const getDeliveryByOrderId = async (orderId) => (await http.get(`/api/v1/deliveries/order/${orderId}`)).data;

/** Admin: status is OUT_FOR_DELIVERY, DELIVERED or DELIVERY_FAILED (409 for an illegal move). */
export const advanceDelivery = async (orderId, status, note) =>
  (await http.put(`/api/v1/deliveries/order/${orderId}/status`, { status, note: note || undefined })).data;

/** Moves an admin may make from a delivery status (mirrors delivery-tracking's DeliveryStatus rules). */
export const nextDeliveryStatuses = (status) => {
  switch (status) {
    case 'IN_TRANSIT':
      return ['OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED'];
    case 'OUT_FOR_DELIVERY':
      return ['DELIVERED', 'DELIVERY_FAILED'];
    default:
      return [];
  }
};
