import { Box, Stack, Typography } from '@mui/material';
import { formatDateTime } from '../../components/format';

const SOURCE_LABEL = {
  'order-service': 'Orders',
  'inventory-service': 'Inventory',
  'payment-service': 'Payment',
  'shipping-service': 'Shipping',
  'delivery-tracking-service': 'Delivery',
  'notification-service': 'Notification',
  'fulfillment-service': 'Tracking',
};

/** The order's timeline from the fulfillment projection, oldest first. */
function TrackingTimeline({ timeline }) {
  if (!timeline?.length) {
    return <Typography variant="body2" color="text.secondary">No events recorded yet.</Typography>;
  }
  return (
    <Stack spacing={0} component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {timeline.map((entry, index) => (
        <Box
          component="li"
          key={`${entry.occurredAt}-${index}`}
          sx={{ display: 'flex', gap: 2, pb: 2, position: 'relative' }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Box
              sx={{
                width: 12,
                height: 12,
                mt: 0.6,
                borderRadius: '50%',
                bgcolor: entry.eventType === 'Escalated' || /Failed/.test(entry.eventType) ? 'error.main' : 'primary.main',
              }}
            />
            {index < timeline.length - 1 && <Box sx={{ flex: 1, width: 2, bgcolor: 'divider', mt: 0.5 }} />}
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{entry.description}</Typography>
            <Typography variant="caption" color="text.secondary">
              {formatDateTime(entry.occurredAt)} · {SOURCE_LABEL[entry.source] || entry.source}
            </Typography>
          </Box>
        </Box>
      ))}
    </Stack>
  );
}

export default TrackingTimeline;
