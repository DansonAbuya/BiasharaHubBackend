'use server';

/**
 * Copy this into your frontend e.g. lib/actions/purchaseOrders.ts
 * Adjust backendFetch to match your API client (e.g. backendFetch from lib/api or similar).
 */

import { backendFetch } from '@/lib/api'; // or your auth API client

export interface PurchaseOrderItemDto {
  id: string;
  purchaseOrderId: string;
  productId?: string;
  productName?: string;
  description?: string;
  unitOfMeasure?: string;
  requestedQuantity: number;
  expectedUnitCost?: number;
  createdAt?: string;
}

export interface PurchaseOrderDto {
  id: string;
  businessId: string;
  supplierId?: string;
  supplierName?: string;
  poNumber?: string;
  deliveryNoteRef?: string;
  expectedDeliveryDate?: string;
  status: string;
  createdAt?: string;
  createdByName?: string;
  items: PurchaseOrderItemDto[];
}

/** List POs for the current business (Owner/Staff). */
export async function listPurchaseOrders(): Promise<PurchaseOrderDto[]> {
  const res = await backendFetch('/purchase-orders');
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { error?: string };
    throw new Error(err?.error || 'Failed to list purchase orders');
  }
  return res.json() as Promise<PurchaseOrderDto[]>;
}

/** List POs for the logged-in supplier. */
export async function listMyPurchaseOrdersAsSupplier(): Promise<PurchaseOrderDto[]> {
  const res = await backendFetch('/purchase-orders/my');
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { error?: string };
    throw new Error(err?.error || 'Failed to list purchase orders');
  }
  return res.json() as Promise<PurchaseOrderDto[]>;
}

/** Get one PO with full item breakdown (works for Owner/Staff and Supplier). */
export async function getPurchaseOrder(id: string): Promise<PurchaseOrderDto> {
  const res = await backendFetch(`/purchase-orders/${id}`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { error?: string };
    throw new Error(err?.error || 'Failed to load purchase order');
  }
  return res.json() as Promise<PurchaseOrderDto>;
}
