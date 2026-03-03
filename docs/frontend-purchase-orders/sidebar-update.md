# Sidebar: Make Purchase Orders visible to Supplier

In your dashboard sidebar (e.g. `components/dashboard/sidebar.tsx`), ensure the **supplier** menu includes a link to Purchase orders so they can see POs assigned to them.

## Option A – Add to existing supplier menu items

Where you define menu items for the supplier role (e.g. under `menuItems.supplier` or similar), add:

- **Label:** `Purchase orders`
- **Path:** `/dashboard/purchase-orders`

Example shape:

```ts
// Example: ensure supplier has Purchase orders in the menu
const menuItems = {
  // ...
  supplier: [
    { label: 'My dispatches', href: '/dashboard/supplier-dispatches' },
    { label: 'Purchase orders', href: '/dashboard/purchase-orders' },  // add this
    { label: 'Browse store', href: '/dashboard/storefront' },
    { label: 'Profile', href: '/dashboard/profile' },
  ],
};
```

## Option B – If you filter by path

If the sidebar filters visible items by path (e.g. an `operationsItems` or allowed paths list), add `/dashboard/purchase-orders` to the allowed list for the supplier role so the link is shown.

After this, when a supplier logs in and opens the dashboard, they will see **Purchase orders** and can open the same purchase orders page; the page will call `GET /api/purchase-orders/my` and `GET /api/purchase-orders/{id}` for them.
