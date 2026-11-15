// Distinct loading, error and empty states (R-FE-05): an error is never shown as "nothing here".
import { Alert, Box, Button, Card, CircularProgress, Typography } from '@mui/material';

export function LoadingState({ label = 'Loading…' }) {
  return (
    <Box display="flex" flexDirection="column" alignItems="center" gap={2} py={6}>
      <CircularProgress />
      <Typography variant="body2" color="text.secondary">{label}</Typography>
    </Box>
  );
}

export function ErrorState({ message, onRetry }) {
  if (!message) return null;
  return (
    <Alert
      severity="error"
      action={onRetry ? <Button color="inherit" size="small" onClick={onRetry}>Retry</Button> : undefined}
    >
      {message}
    </Alert>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <Card sx={{ textAlign: 'center', py: 6, px: 3 }}>
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>{title}</Typography>
      {children && <Typography variant="body2" color="text.secondary" sx={{ mb: action ? 3 : 0 }}>{children}</Typography>}
      {action}
    </Card>
  );
}
