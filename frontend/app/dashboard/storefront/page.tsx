"use client";

import { useEffect, useState, useCallback } from "react";
import {
  listProducts,
  listProductCategories,
  listBusinesses,
  type ProductDto,
  type ProductCategoryDto,
  type BusinessDto,
  type ListProductsParams,
} from "@/lib/api";

export default function StorefrontPage() {
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [categories, setCategories] = useState<ProductCategoryDto[]>([]);
  const [businesses, setBusinesses] = useState<BusinessDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [category, setCategory] = useState<string>("");
  const [businessId, setBusinessId] = useState<string>("");

  const fetchCategories = useCallback(async () => {
    try {
      const data = await listProductCategories();
      setCategories(data);
    } catch {
      setCategories([]);
    }
  }, []);

  const fetchBusinesses = useCallback(async () => {
    try {
      const data = await listBusinesses();
      setBusinesses(data);
    } catch {
      setBusinesses([]);
    }
  }, []);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    const params: ListProductsParams = {};
    if (category) params.category = category;
    if (businessId) params.businessId = businessId;
    try {
      const data = await listProducts(params);
      setProducts(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load products");
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, [category, businessId]);

  useEffect(() => {
    fetchCategories();
    fetchBusinesses();
  }, [fetchCategories, fetchBusinesses]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  return (
    <div>
      <h1 style={{ marginBottom: "1rem" }}>Storefront</h1>
      <p style={{ marginBottom: "1.5rem", color: "#666" }}>
        Filter products by category or by business / seller.
      </p>

      <div className="filters card">
        <div className="filters">
          <label>
            <span>Category</span>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              aria-label="Filter by category"
            >
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c.id} value={c.name}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Business / Seller</span>
            <select
              value={businessId}
              onChange={(e) => setBusinessId(e.target.value)}
              aria-label="Filter by business or seller"
            >
              <option value="">All businesses</option>
              {businesses.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} — {b.ownerName}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => {
              setCategory("");
              setBusinessId("");
            }}
          >
            Clear filters
          </button>
        </div>
      </div>

      {loading && <div className="loading">Loading products…</div>}
      {error && <div className="error">{error}</div>}

      {!loading && !error && (
        <div className="grid grid-products" style={{ marginTop: "1.5rem" }}>
          {products.length === 0 ? (
            <p className="loading">No products match your filters.</p>
          ) : (
            products.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))
          )}
        </div>
      )}
    </div>
  );
}

function ProductCard({ product }: { product: ProductDto }) {
  const imgSrc = product.image || (product.images && product.images[0]) || null;
  return (
    <article className="product-card">
      {imgSrc ? (
        <img src={imgSrc} alt={product.name} />
      ) : (
        <div style={{ width: "100%", aspectRatio: "1", background: "#e5e5e5", display: "flex", alignItems: "center", justifyContent: "center", color: "#999" }}>
          No image
        </div>
      )}
      <div className="body">
        <h3>{product.name}</h3>
        {product.category && (
          <span style={{ fontSize: "0.875rem", color: "#666" }}>{product.category}</span>
        )}
        <span className="price">
          KES {typeof product.price === "number" ? product.price.toLocaleString() : product.price}
        </span>
      </div>
    </article>
  );
}
