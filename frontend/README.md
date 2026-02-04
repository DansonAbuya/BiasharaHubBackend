# BiasharaHub Frontend

Next.js 14 (App Router) storefront for BiasharaHub. Customers can browse products and filter by **category** or **business / seller**.

## Setup

1. **Install dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Configure API URL** (optional; default: `http://localhost:8080/api`)
   - Create `.env.local` and set:
   ```env
   NEXT_PUBLIC_API_URL=http://localhost:8080/api
   ```

3. **Run the backend** on port 8080 so the API is available.

4. **Run the frontend**
   ```bash
   npm run dev
   ```
   Open [http://localhost:3000](http://localhost:3000). Use **Open Storefront** or go to **Storefront** to browse and filter products.

## Storefront filters

- **Category** — dropdown from `GET /products/categories`; filters products by category name.
- **Business / Seller** — dropdown from `GET /products/businesses`; filters by business (each business has one owner, so this is effectively by business or owner).

Products are loaded with `GET /products` and optional query params `category` and `businessId`. No auth required for browsing.
