import { useState } from 'react';
import {
  Alert,
  Button,
  Chip,
  Container,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { createUser, deactivateUser, extractApiError, listUsers, updateUser } from '../../../api';
import { formatDateTime } from '../../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../../components/PageState';
import { useAuth } from '../../auth';
import AdminPageHeader from '../AdminPageHeader';
import { usePagedQuery } from '../usePagedQuery';
import UserFormDialog from './UserFormDialog';

/**
 * User administration (R-FE-04): list, create, change role, activate/deactivate. The backend refuses (409) to demote
 * or deactivate the last active admin; that message is shown as is.
 */
function AdminUsersPage() {
  const { user: me } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [message, setMessage] = useState(null);
  const { page, rows, error, loading, reload } = usePagedQuery(listUsers, { page: pageIndex, size: 20, sort: 'id' });

  const run = async (action, success) => {
    try {
      await action();
      setMessage({ severity: 'success', text: success });
      reload();
    } catch (actionError) {
      setMessage({ severity: 'error', text: extractApiError(actionError) });
    }
  };

  const create = async (user) => {
    try {
      await createUser(user);
    } catch (createError) {
      throw new Error(extractApiError(createError));
    }
    setDialogOpen(false);
    setMessage({ severity: 'success', text: `Created ${user.email}.` });
    reload();
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <AdminPageHeader title="Users" subtitle="Deactivation is soft: the account stays, but can no longer sign in.">
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>New user</Button>
        </AdminPageHeader>
        {message && <Alert severity={message.severity} onClose={() => setMessage(null)}>{message.text}</Alert>}
        <ErrorState message={error} onRetry={reload} />
        {loading ? <LoadingState /> : !error && rows.length === 0 ? (
          <EmptyState title="No users" />
        ) : rows.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((user) => (
                  <TableRow key={user.id} hover>
                    <TableCell>{user.id}</TableCell>
                    <TableCell>{user.firstName} {user.lastName}{user.id === me?.id && ' (you)'}</TableCell>
                    <TableCell>{user.email}</TableCell>
                    <TableCell>
                      <Select size="small" value={user.role}
                        onChange={(event) => run(() => updateUser(user.id, { role: event.target.value }), `${user.email} is now ${event.target.value}.`)}>
                        <MenuItem value="USER">User</MenuItem>
                        <MenuItem value="ADMIN">Admin</MenuItem>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={user.active ? 'Active' : 'Inactive'} color={user.active ? 'success' : 'default'} />
                    </TableCell>
                    <TableCell>{formatDateTime(user.createdAt)}</TableCell>
                    <TableCell align="right">
                      {user.active ? (
                        <Button size="small" color="error"
                          onClick={() => window.confirm(`Deactivate ${user.email}?`)
                            && run(() => deactivateUser(user.id), `Deactivated ${user.email}.`)}>
                          Deactivate
                        </Button>
                      ) : (
                        <Button size="small" onClick={() => run(() => updateUser(user.id, { active: true }), `Reactivated ${user.email}.`)}>
                          Reactivate
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {page?.totalPages > 1 && (
          <Pagination count={page.totalPages} page={pageIndex + 1} onChange={(_, value) => setPageIndex(value - 1)} />
        )}
      </Stack>
      <UserFormDialog open={dialogOpen} onClose={() => setDialogOpen(false)} onSave={create} />
    </Container>
  );
}

export default AdminUsersPage;
