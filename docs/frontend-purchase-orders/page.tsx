'use client';

/**
 * Reference: app/dashboard/purchase-orders/page.tsx
 * - Breakdown of what is contained in each purchase order (line items).
 * - Status highlighted in different colors.
 * - Works for both Seller (Owner/Staff) and Supplier; suppliers see only their POs.
 *
 * Prerequisites:
 * - useAuth(), useToast(), backendFetch or actions from lib/actions/purchaseOrders
 * - UI: Card, Button, Badge, Table, Dialog/Sheet from your design system
 */

import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth'; // or your auth hook
import { useToast } from '@/hooks/useToast'; // or your toast
import {
  listPurchaseOrders,
  listMyPurchaseOrdersAsSupplier,
  getPurchaseOrder,
  type PurchaseOrderDto,
  type PurchaseOrderItemDto,
} from '@/lib/actions/purchaseOrders'; // or from docs/frontend-purchase-orders/actions
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

function getStatusColor(status: string): string {
  const s = (status || '').toUpperCase();
  if (s === 'DRAFT') return 'bg-gray-500';
  if (s === 'SENT') return 'bg-blue-500';
  if (s === 'PARTIALLY_FULFILLED') return 'bg-amber-500';
  if (s === 'FULFILLED') return 'bg-green-600';
  if (s === 'CANCELLED') return 'bg-red-600';
  return 'bg-gray-500';
}

function formatDate(iso?: string) {
  if (!iso) return '–';
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      dateStyle: 'short',
    });
  } catch {
    return iso;
  }
}

function formatMoney(n?: number | null) {
  if (n == null) return '–';
  return new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency: 'KES',
  }).format(n);
}

export default function PurchaseOrdersPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [list, setList] = useState<PurchaseOrderDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<PurchaseOrderDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const isSupplier = user?.role?.toLowerCase() === 'supplier';

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const data = isSupplier
          ? await listMyPurchaseOrdersAsSupplier()
          : await listPurchaseOrders();
        if (!cancelled) setList(Array.isArray(data) ? data : []);
      } catch (e) {
        if (!cancelled) {
          toast?.({ title: 'Error', description: (e as Error).message, variant: 'destructive' });
          setList([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [isSupplier, toast]);

  const openDetail = async (id: string) => {
    setDetail(null);
    setDetailLoading(true);
    try {
      const po = await getPurchaseOrder(id);
      setDetail(po);
    } catch (e) {
      toast?.({ title: 'Error', description: (e as Error).message, variant: 'destructive' });
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold">
          {isSupplier ? 'Purchase orders (assigned to you)' : 'Purchase orders'}
        </h1>
        <p className="text-muted-foreground mt-1">
          {isSupplier
            ? 'View orders the seller has placed with you.'
            : 'Create and view purchase orders for your suppliers.'}
        </p>
      </div>

      {loading ? (
        <p className="text-muted-foreground">Loading…</p>
      ) : list.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">
            No purchase orders found.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {list.map((po) => (
            <Card key={po.id} className="overflow-hidden">
              <CardHeader className="pb-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <CardTitle className="text-lg">
                    {po.poNumber || `PO ${po.id.slice(0, 8)}`}
                    {po.supplierName && (
                      <span className="ml-2 text-sm font-normal text-muted-foreground">
                        – {po.supplierName}
                      </span>
                    )}
                  </CardTitle>
                  <Badge className={getStatusColor(po.status)}>
                    {po.status?.replace(/_/g, ' ') || 'Unknown'}
                  </Badge>
                </div>
                <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                  <span>Expected: {formatDate(po.expectedDeliveryDate)}</span>
                  <span>Created: {formatDate(po.createdAt)}</span>
                  {po.createdByName && <span>By: {po.createdByName}</span>}
                </div>
              </CardHeader>
              <CardContent className="pt-0">
                {/* Inline breakdown: summary of items in this PO */}
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Item</TableHead>
                        <TableHead className="text-right">Qty</TableHead>
                        <TableHead>Unit</TableHead>
                        <TableHead className="text-right">Expected cost</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {(po.items || []).map((item: PurchaseOrderItemDto) => (
                        <TableRow key={item.id}>
                          <TableCell>
                            {item.productName || item.description || '–'}
                          </TableCell>
                          <TableCell className="text-right">{item.requestedQuantity ?? '–'}</TableCell>
                          <TableCell>{item.unitOfMeasure || '–'}</TableCell>
                          <TableCell className="text-right">
                            {formatMoney(item.expectedUnitCost)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                <div className="mt-3">
                  <Button variant="outline" size="sm" onClick={() => openDetail(po.id)}>
                    View full details
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Detail modal with full breakdown */}
      <Dialog open={!!detail || detailLoading} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {detail?.poNumber || `PO ${detail?.id?.slice(0, 8)}`} – Full breakdown
            </DialogTitle>
          </DialogHeader>
          {detailLoading && !detail ? (
            <p className="text-muted-foreground">Loading…</p>
          ) : detail ? (
            <div className="space-y-4">
              <div className="flex flex-wrap gap-2">
                <Badge className={getStatusColor(detail.status)}>
                  {detail.status?.replace(/_/g, ' ') || 'Unknown'}
                </Badge>
                {detail.supplierName && (
                  <span className="text-sm text-muted-foreground">Supplier: {detail.supplierName}</span>
                )}
                <span className="text-sm text-muted-foreground">
                  Expected: {formatDate(detail.expectedDeliveryDate)}
                </span>
              </div>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Product / Description</TableHead>
                      <TableHead className="text-right">Qty</TableHead>
                      <TableHead>Unit</TableHead>
                      <TableHead className="text-right">Expected unit cost</TableHead>
                      <TableHead className="text-right">Line total</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(detail.items || []).map((item: PurchaseOrderItemDto) => (
                      <TableRow key={item.id}>
                        <TableCell>
                          {item.productName || item.description || '–'}
                        </TableCell>
                        <TableCell className="text-right">{item.requestedQuantity ?? '–'}</TableCell>
                        <TableCell>{item.unitOfMeasure || '–'}</TableCell>
                        <TableCell className="text-right">
                          {formatMoney(item.expectedUnitCost)}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatMoney(
                            item.requestedQuantity != null && item.expectedUnitCost != null
                              ? item.requestedQuantity * Number(item.expectedUnitCost)
                              : null
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
