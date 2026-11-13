// Single entry point for backend calls; features import from 'api', never from individual modules.
export { extractApiError, getAuthHeaders, getOrderApi, getPetCatalogApi, getUserApi } from './client';
export * from './catalog';
export * from './auth';
export * from './users';
export * from './orders';
export * from './tracking';
