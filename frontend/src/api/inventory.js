// Inventory: read-only admin view (reservations and sales only happen through the checkout saga).
import { http } from './client';

export const listInventory = async (params = {}) => (await http.get('/api/v1/inventory/pets', { params })).data;
