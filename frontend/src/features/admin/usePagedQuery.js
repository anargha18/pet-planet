import { useCallback, useEffect, useState } from 'react';
import { extractApiError } from '../../api';

/** Loads a Spring `Page` with the given fetcher whenever `params` change; errors are kept, never hidden. */
export function usePagedQuery(fetcher, params) {
  const [page, setPage] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const key = JSON.stringify(params);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setPage(await fetcher(JSON.parse(key)));
    } catch (loadError) {
      setError(extractApiError(loadError));
    } finally {
      setLoading(false);
    }
  }, [fetcher, key]);

  useEffect(() => {
    load();
  }, [load]);

  return { page, rows: page?.content || [], total: page?.totalElements ?? 0, error, loading, reload: load };
}
