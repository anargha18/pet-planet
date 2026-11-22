import { useEffect, useState } from 'react';
import { listUsers } from '../../api';

/**
 * Customer names for admin tables (orders and tracking only carry the numeric customer ID). Best effort: if the user
 * list cannot be loaded, the tables still show the ID.
 */
export function useUserNames() {
  const [names, setNames] = useState({});

  useEffect(() => {
    let active = true;
    listUsers({ size: 500 })
      .then((page) => {
        if (!active) return;
        setNames(Object.fromEntries((page?.content || []).map((u) => [u.id, { name: `${u.firstName} ${u.lastName}`.trim(), email: u.email }])));
      })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  return names;
}

/** "Jane Customer" with the email and ID underneath, or just "#7" while names are unknown. */
export function CustomerLabel({ id, names }) {
  if (id == null) return '—';
  const user = names[id];
  if (!user) return `#${id}`;
  return (
    <span title={`Customer #${id}`}>
      {user.name}
      <br />
      <small style={{ color: 'rgba(0,0,0,0.6)' }}>{user.email}</small>
    </span>
  );
}
