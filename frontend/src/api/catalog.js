// Pet catalog: public reads, admin writes. Availability is read-only here (inventory owns it).
import { http } from './client';

export const getPets = async (params = {}) => (await http.get('/api/v1/pets', { params })).data;

export const getPetById = async (petId) => (await http.get(`/api/v1/pets/${petId}`)).data;

export const createPet = async (pet) => (await http.post('/api/v1/pets', pet)).data;

export const updatePet = async (petId, pet) => (await http.put(`/api/v1/pets/${petId}`, pet)).data;

/** Soft delete: the pet becomes RETIRED (409 if reserved or sold). */
export const archivePet = async (petId) => {
  await http.delete(`/api/v1/pets/${petId}`);
};
