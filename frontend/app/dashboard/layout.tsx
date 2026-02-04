import Link from "next/link";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="container">
      <nav style={{ padding: "1rem 0", borderBottom: "1px solid #e5e5e5", marginBottom: "1rem" }}>
        <Link href="/" style={{ marginRight: "1rem" }}>Home</Link>
        <Link href="/dashboard/storefront">Storefront</Link>
      </nav>
      {children}
    </div>
  );
}
