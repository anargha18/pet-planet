import { Alert } from '@mui/material';

/** DEC-4: payment is simulated and automatic. Shown wherever the user might expect to pay. */
function SimulatedPaymentNote({ sx }) {
  return (
    <Alert severity="info" sx={sx}>
      Payment is <strong>simulated</strong>: no card is needed and no money moves. The order total is charged
      automatically once your pets are reserved, and refunded if the order is cancelled or fails.
    </Alert>
  );
}

export default SimulatedPaymentNote;
