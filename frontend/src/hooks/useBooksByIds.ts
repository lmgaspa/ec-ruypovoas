import { useEffect, useMemo, useState } from 'react';
import { getBookById, type BookDTO } from '../api/booksDTO';

const cache = new Map<string, BookDTO>();

export function useBooksByIds(ids: string[]) {
  const uniq = useMemo(() => Array.from(new Set(ids.filter(Boolean))), [ids]);
  const [data, setData] = useState<Record<string, BookDTO | undefined>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const missing = uniq.filter(id => !cache.has(id));
        if (missing.length) {
          const results = await Promise.allSettled(missing.map(getBookById));
          results.forEach((r, i) => {
            const id = missing[i];
            if (r.status === 'fulfilled') cache.set(id, r.value);
          });
        }
        if (!cancelled) {
          const map: Record<string, BookDTO | undefined> = {};
          uniq.forEach(id => { map[id] = cache.get(id); });
          setData(map);
          setError(null);
        }
      } catch (e: unknown) {
        if (!cancelled) setError(e instanceof Error ? e : new Error(String(e)));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [uniq]);

  return { data, loading, error };
}
