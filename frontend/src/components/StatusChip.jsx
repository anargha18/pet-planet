import { Chip } from '@mui/material';
import { humanize } from './format';

const COLORS = {
  success: ['CONFIRMED', 'SUCCEEDED', 'DELIVERED', 'SENT', 'AVAILABLE'],
  warning: ['PAYMENT_PENDING', 'PENDING', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'SHIPPED', 'RESERVED'],
  info: ['CREATED', 'REFUNDED', 'SOLD'],
  error: ['FAILED', 'DELIVERY_FAILED'],
};

export const statusColor = (status) =>
  Object.keys(COLORS).find((color) => COLORS[color].includes(status)) || 'default';

/** Coloured chip for any status value of the backend (order, payment, shipment, delivery, inventory, ...). */
function StatusChip({ status, emptyLabel = 'Not yet', size = 'small', ...rest }) {
  if (!status) {
    return <Chip label={emptyLabel} size={size} variant="outlined" {...rest} />;
  }
  return <Chip label={humanize(status)} color={statusColor(status)} size={size} {...rest} />;
}

export default StatusChip;
