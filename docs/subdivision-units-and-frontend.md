# Subdivision with units – backend done, frontend guide

## Backend summary

- **Unit on delivery items**: `supplier_delivery_items.unit_of_measure` (e.g. `kg`, `g`, `L`, `piece`) is added and exposed in APIs. When a dispatch is created from a PO, the unit is copied from the PO line so the seller sees e.g. "10 kg at 2000 per unit".
- **Unit-based subdivision**: The convert (subdivide) API supports **target unit size + target unit** so the system can compute the number of sub-units and cost per sub-unit with no loss.

### API behaviour

1. **Delivery item DTO** (`SupplierDeliveryItemDto`) now has `unitOfMeasure`. Show it on the seller’s delivery/receiving and subdivision screens (e.g. "Quantity: 10", "Unit: kg", "Cost per unit: 2000").
2. **Subdivision (convert) request** (`POST .../items/{itemId}/convert`) accepts:
   - **Existing**: `targetName`, `targetPrice`, `producedQuantity`, `sourceQuantityUsed`, `piecesPerUnit` (e.g. 3 fillets per fish).
   - **New (unit-based)**:
     - `targetUnitSize`: decimal, e.g. `500` for 500 g.
     - `targetUnit`: string, e.g. `"g"`, `"kg"`, `"L"`, `"ml"`, `"piece"`.
   - If the delivery item has a unit and you send `targetUnitSize` + `targetUnit`, the backend:
     - Converts source quantity to a common base (e.g. 10 kg → 10,000 g).
     - Computes **number of sub-units** = source base / target base (e.g. 10,000 / 500 = 20).
     - Sets **cost per sub-unit** = total cost / sub-units (no loss); seller can override with `targetPrice`.

### Example (10 kg at 2000 per kg → 500 g sub-units)

- Request: `targetName`: "Fish 500g", `targetUnitSize`: 500, `targetUnit`: "g", optional `sourceQuantityUsed`: 10.
- Backend: 10 kg → 10,000 g; 10,000 / 500 = **20** sub-units; total cost 20,000 → **1,000** per 500 g.
- Response: New product (or existing) with quantity 20 and price 1000 (or seller’s `targetPrice`).

### Supported units

- Weight: `kg`, `g`, `gram`, `grams`, `kilogram`, `kilograms`
- Volume: `L`, `liter`, `liters`, `ml`, `milliliter`, `milliliters`
- Count: `piece`, `pieces`, `unit`, `units`, `pcs`

## Frontend changes to make (in your Next.js app)

1. **Deliveries / receiving**
   - For each delivery item, show **unit** (e.g. "Unit: kg") next to quantity and cost.
   - Use `item.unitOfMeasure` from the delivery DTO.

2. **Purchase orders**
   - You already have `unitOfMeasure` on PO items; keep showing it so the seller sees e.g. "10 kg" and "2000 per kg".

3. **Subdivision (convert) dialog**
   - If the selected delivery item has `unitOfMeasure` (e.g. `kg`, `g`):
     - Add **Subdivide by unit**:
       - **Target unit size** (number): e.g. `500`.
       - **Target unit** (select or text): e.g. `g`, `kg`, `L`, `ml`, `piece`.
     - On submit, send `targetUnitSize`, `targetUnit`, and `targetName`; optionally `sourceQuantityUsed` and `targetPrice` (to override the computed price).
     - Show a short hint: "e.g. 10 kg → 500 g gives 20 units; cost per 500 g is computed from supply cost (you can adjust price)."
   - Keep the existing **Pieces per unit** flow for count-based subdivision (e.g. 3 fillets per fish).
   - When the item has no unit, only show pieces-per-unit (or explicit produced quantity + target price).

4. **Add delivery item (manual)**
   - If you have a form to add an item to a delivery, add an optional **Unit** field and send it as `unitOfMeasure` in the request body.
