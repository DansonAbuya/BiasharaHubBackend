/**
 * BiasharaHub API client. Products and categories/businesses can be used without auth for storefront.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
const DEFAULT_TENANT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

function getBaseHeaders(): HeadersInit {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": DEFAULT_TENANT_ID,
  };
}

export interface ProductCategoryDto {
  id: string;
  name: string;
  displayOrder?: number;
}

export interface BusinessDto {
  id: string;
  name: string;
  ownerName: string;
}

export interface ProductDto {
  id: string;
  name: string;
  category: string | null;
  price: number;
  quantity: number;
  description: string | null;
  image: string | null;
  images: string[];
  businessId: string | null;
}

export interface ListProductsParams {
  category?: string;
  businessId?: string;
  businessName?: string;
  ownerId?: string;
}

export async function listProductCategories(): Promise<ProductCategoryDto[]> {
  const res = await fetch(`${API_BASE}/products/categories`, {
    headers: getBaseHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch categories");
  return res.json();
}

export async function listBusinesses(): Promise<BusinessDto[]> {
  const res = await fetch(`${API_BASE}/products/businesses`, {
    headers: getBaseHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch businesses");
  return res.json();
}

export async function listProducts(params?: ListProductsParams): Promise<ProductDto[]> {
  const search = new URLSearchParams();
  if (params?.category) search.set("category", params.category);
  if (params?.businessId) search.set("businessId", params.businessId);
  if (params?.businessName) search.set("businessName", params.businessName);
  if (params?.ownerId) search.set("ownerId", params.ownerId);
  const qs = search.toString();
  const url = qs ? `${API_BASE}/products?${qs}` : `${API_BASE}/products`;
  const res = await fetch(url, { headers: getBaseHeaders() });
  if (!res.ok) throw new Error("Failed to fetch products");
  return res.json();
}
