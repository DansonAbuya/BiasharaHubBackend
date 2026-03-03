# Purchase Orders – Frontend Integration

Use these files in your **Next.js frontend** so that:

1. **Sellers (Owner/Staff)** see purchase orders with a clear **item breakdown** and **status highlighted in color**.
2. **Suppliers** see the same purchase orders (those assigned to them) and the same breakdown/status UI.

## Backend (already done)

- `GET /api/purchase-orders` – list POs for business (Owner/Staff).
- `GET /api/purchase-orders/my` – list POs for logged-in supplier.
- `GET /api/purchase-orders/{id}` – get one PO with full items (works for both seller and supplier when allowed).

## What to add in the frontend

1. **Sidebar**  
   Add a “Purchase orders” link for the **supplier** role so they can open the same purchase orders page.  
   See `sidebar-update.md`.

2. **Purchase orders page**  
   Use `page.tsx` (or merge its logic into your existing `app/dashboard/purchase-orders/page.tsx`).  
   It:
   - Uses role to call either “list for business” or “list for supplier”.
   - Shows each PO with **status badge (color by status)** and an expandable **item breakdown** (product/description, qty, unit, expected cost).
   - Allows opening a detail view for one PO with full breakdown.

3. **Actions**  
   Use or adapt `actions.ts` so the page can call:
   - `listPurchaseOrders()` for Owner/Staff,
   - `listMyPurchaseOrdersAsSupplier()` for Supplier,
   - `getPurchaseOrder(id)` for both (backend resolves by role).

## Status colors (suggested)

- **DRAFT** – gray  
- **SENT** – blue  
- **PARTIALLY_FULFILLED** – amber  
- **FULFILLED** – green  
- **CANCELLED** – red  

These are implemented in the reference `page.tsx` via a `getStatusColor` helper.
