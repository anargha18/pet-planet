import { useCallback, useEffect, useRef, useState } from 'react';
import { extractApiError, getNotificationsForOrder, getOrder, getOrderTracking, isNotFound } from '../../api';

const POLL_INTERVAL_MS = 2000;
const POLL_LIMIT_MS = 60000;
const OPEN_STATUSES = ['CREATED', 'PAYMENT_PENDING'];

/** Still worth polling: the saga is running, or a confirmed order has not reached shipping yet. */
const stillMoving = (order, tracking) => {
  if (!order) return true;
  if (OPEN_STATUSES.includes(order.status)) return true;
  if (!tracking) return true; // the projection has not caught up yet
  return order.status === 'CONFIRMED' && !tracking.summary?.shipmentStatus;
};

/**
 * Loads one order, its tracking view and its notifications. With `poll`, refreshes every 2 s while the order is
 * moving, for at most 60 s (R-FE-03). A 404 from the tracking view means "not available yet", not an error.
 */
export function useOrderTracking(orderId, { poll = false } = {}) {
  const [order, setOrder] = useState(null);
  const [tracking, setTracking] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [polling, setPolling] = useState(poll);
  const startedAt = useRef(Date.now());

  const load = useCallback(async () => {
    try {
      const nextOrder = await getOrder(orderId);
      let nextTracking = null;
      try {
        nextTracking = await getOrderTracking(orderId);
      } catch (trackingError) {
        if (!isNotFound(trackingError)) throw trackingError;
      }
      const nextNotifications = await getNotificationsForOrder(orderId);
      setOrder(nextOrder);
      setTracking(nextTracking);
      setNotifications(nextNotifications);
      setError('');
      return stillMoving(nextOrder, nextTracking);
    } catch (loadError) {
      setError(extractApiError(loadError));
      return false;
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    let cancelled = false;
    let timer = null;
    startedAt.current = Date.now();
    setLoading(true);

    const tick = async () => {
      const moving = await load();
      if (cancelled) return;
      const withinLimit = Date.now() - startedAt.current < POLL_LIMIT_MS;
      if (poll && moving && withinLimit) {
        setPolling(true);
        timer = setTimeout(tick, POLL_INTERVAL_MS);
      } else {
        setPolling(false);
      }
    };
    tick();

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [load, poll]);

  return { order, tracking, notifications, error, loading, polling, reload: load };
}
