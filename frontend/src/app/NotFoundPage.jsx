import { Button, Container } from '@mui/material';
import { Link } from 'react-router-dom';
import { EmptyState } from '../components/PageState';

/** Shown for any path the router does not know (instead of silently rendering the home page). */
function NotFoundPage() {
  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <EmptyState
        title="Page not found"
        action={<Button component={Link} to="/" variant="contained">Back to home</Button>}
      >
        The page you are looking for does not exist or has moved.
      </EmptyState>
    </Container>
  );
}

export default NotFoundPage;
