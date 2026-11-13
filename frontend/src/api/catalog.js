// Pet catalog (public reads).
import { petCatalogApi } from './client';

export const getPets = async (params = {}) => {
  const response = await petCatalogApi.get('/api/v1/pets', { params });
  return response.data;
};

export const getPetById = async (petId) => {
  const response = await petCatalogApi.get(`/api/v1/pets/${petId}`);
  return response.data;
};
