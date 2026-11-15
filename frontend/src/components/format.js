// Display formatting shared by every feature.
export const formatCurrency = (value) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));

export const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '—');

/** "PAYMENT_PENDING" -> "Payment pending". */
export const humanize = (value) => {
  if (!value) return '—';
  const text = String(value).replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
};
