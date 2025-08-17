const BASE_URL = "https://ecommerceag-6fa0e6a5edbf.herokuapp.com".replace(/\/+$/, "");

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { credentials: "omit" });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}