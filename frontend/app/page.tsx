import Link from "next/link";

export default function Home() {
  return (
    <div className="container" style={{ paddingTop: "2rem" }}>
      <h1>BiasharaHub</h1>
      <p style={{ marginTop: "0.5rem", marginBottom: "1.5rem" }}>
        Browse products from businesses on the platform.
      </p>
      <Link href="/dashboard/storefront" className="primary" style={{ display: "inline-block", padding: "0.5rem 1rem", background: "#0d9488", color: "#fff", borderRadius: "6px" }}>
        Open Storefront
      </Link>
    </div>
  );
}
