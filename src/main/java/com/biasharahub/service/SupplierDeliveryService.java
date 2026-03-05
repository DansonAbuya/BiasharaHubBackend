package com.biasharahub.service;

import com.biasharahub.dto.request.AddSupplierDeliveryItemRequest;
import com.biasharahub.dto.request.ConfirmReceiptRequest;
import com.biasharahub.dto.request.CreateSupplierDeliveryRequest;
import com.biasharahub.dto.request.SubmitDispatchRequest;
import com.biasharahub.dto.request.ConvertDeliveryItemRequest;
import com.biasharahub.dto.response.SupplierDeliveryDto;
import com.biasharahub.dto.response.SupplierDeliveryItemDto;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.Supplier;
import com.biasharahub.entity.SupplierDelivery;
import com.biasharahub.entity.SupplierDeliveryItem;
import com.biasharahub.entity.User;
import com.biasharahub.entity.PurchaseOrder;
import com.biasharahub.repository.PurchaseOrderRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.SupplierDeliveryItemRepository;
import com.biasharahub.repository.SupplierDeliveryRepository;
import com.biasharahub.repository.SupplierRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierDeliveryService {

    private final SupplierRepository supplierRepository;
    private final SupplierDeliveryRepository supplierDeliveryRepository;
    private final SupplierDeliveryItemRepository supplierDeliveryItemRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final StockLedgerService stockLedgerService;

    @Transactional(readOnly = true)
    public List<SupplierDeliveryDto> listMyBusinessDeliveries(AuthenticatedUser user) {
        UUID businessId = requireBusinessId(user);
        return supplierDeliveryRepository.findByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .map(d -> {
                    List<SupplierDeliveryItemDto> items = supplierDeliveryItemRepository.findByDeliveryIdWithProduct(d.getDeliveryId())
                            .stream()
                            .map(this::toItemDto)
                            .collect(Collectors.toList());
                    return toDto(d, items);
                })
                .collect(Collectors.toList());
    }

    /**
     * List dispatches submitted by the currently logged-in supplier to the business they supply.
     * Scoped by supplier record linked to the supplier user (email + business).
     */
    @Transactional(readOnly = true)
    public List<SupplierDeliveryDto> listMyDispatchesAsSupplier(AuthenticatedUser user) {
        if (user == null || user.role() == null || !"supplier".equalsIgnoreCase(user.role())) {
            throw new IllegalArgumentException("Only suppliers can view their dispatches");
        }
        User actor = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = actor.getBusinessId();
        if (businessId == null) {
            throw new IllegalArgumentException("Supplier is not linked to a business");
        }
        Supplier supplier = supplierRepository.findByEmailIgnoreCaseAndBusinessId(actor.getEmail(), businessId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier record not found for your account"));

        return supplierDeliveryRepository.findByBusinessIdAndSupplier_SupplierIdOrderByCreatedAtDesc(
                        businessId, supplier.getSupplierId())
                .stream()
                .map(d -> {
                    List<SupplierDeliveryItemDto> items = supplierDeliveryItemRepository
                            .findByDeliveryIdWithProduct(d.getDeliveryId())
                            .stream()
                            .map(this::toItemDto)
                            .collect(Collectors.toList());
                    return toDto(d, items);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SupplierDeliveryDto get(AuthenticatedUser user, UUID deliveryId) {
        UUID businessId = requireBusinessId(user);
        SupplierDelivery d = supplierDeliveryRepository.findByIdWithParties(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!businessId.equals(d.getBusinessId())) throw new IllegalArgumentException("Forbidden");
        List<SupplierDeliveryItemDto> items = supplierDeliveryItemRepository.findByDeliveryIdWithProduct(deliveryId)
                .stream().map(this::toItemDto).collect(Collectors.toList());
        return toDto(d, items);
    }

    /**
     * Supplier submits a dispatch: "I have dispatched these items to the seller."
     * Creates delivery with status DISPATCHED. Seller will then confirm receipt.
     */
    @Transactional
    public SupplierDeliveryDto submitDispatch(AuthenticatedUser user, SubmitDispatchRequest request) {
        if (!"supplier".equalsIgnoreCase(user.role())) {
            throw new IllegalArgumentException("Only suppliers can submit a dispatch");
        }
        User actor = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = actor.getBusinessId();
        if (businessId == null) throw new IllegalArgumentException("Supplier must be linked to a business");
        Supplier supplier = supplierRepository.findByEmailIgnoreCaseAndBusinessId(actor.getEmail(), businessId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier record not found for your account"));

        PurchaseOrder po = null;
        if (request.getPurchaseOrderId() != null) {
            po = purchaseOrderRepository.findByIdWithItems(request.getPurchaseOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
            if (!businessId.equals(po.getBusinessId()) || po.getSupplier() == null
                    || !po.getSupplier().getSupplierId().equals(supplier.getSupplierId())) {
                throw new IllegalArgumentException("Purchase order not found or not assigned to you");
            }
            // Prevent multiple dispatches for the same purchase order by this supplier
            boolean alreadyDispatched = supplierDeliveryRepository.existsByPurchaseOrder_PurchaseOrderId(po.getPurchaseOrderId());
            if (alreadyDispatched) {
                throw new IllegalArgumentException("You have already submitted a dispatch for this purchase order");
            }
            // First dispatch against this PO: mark it as SENT so it no longer appears as a draft.
            if ("DRAFT".equalsIgnoreCase(po.getStatus())) {
                po.setStatus("SENT");
                purchaseOrderRepository.save(po);
            }
        }

        SupplierDelivery d = SupplierDelivery.builder()
                .businessId(businessId)
                .supplier(supplier)
                .purchaseOrder(po)
                .deliveryNoteRef(request.getDeliveryNoteRef() != null ? request.getDeliveryNoteRef().trim() : null)
                .deliveredAt(Instant.now())
                .status("DISPATCHED")
                .createdBy(actor)
                .build();
        d = supplierDeliveryRepository.save(d);

        for (SubmitDispatchRequest.DispatchItem di : request.getItems()) {
            if (di.getProductId() == null || di.getQuantity() == null || di.getQuantity() <= 0) continue;
            Product p = productRepository.findById(di.getProductId()).orElseThrow(() -> new IllegalArgumentException("Product not found: " + di.getProductId()));
            if (p.getBusinessId() == null || !businessId.equals(p.getBusinessId())) {
                throw new IllegalArgumentException("Product does not belong to the business you supply");
            }
            String unitOfMeasure = null;
            if (po != null && po.getItems() != null) {
                unitOfMeasure = po.getItems().stream()
                        .filter(pi -> p.getProductId().equals(pi.getProduct() != null ? pi.getProduct().getProductId() : null))
                        .map(com.biasharahub.entity.PurchaseOrderItem::getUnitOfMeasure)
                        .findFirst()
                        .orElse(null);
            }
            SupplierDeliveryItem item = SupplierDeliveryItem.builder()
                    .delivery(d)
                    .product(p)
                    .productName(p.getName())
                    .quantity(di.getQuantity())
                    .unitCost(di.getUnitCost())
                    .unitOfMeasure(unitOfMeasure != null && !unitOfMeasure.isBlank() ? unitOfMeasure.trim() : null)
                    .build();
            supplierDeliveryItemRepository.save(item);
        }
        return get(user, d.getDeliveryId());
    }

    @Transactional
    public SupplierDeliveryDto create(AuthenticatedUser user, CreateSupplierDeliveryRequest request) {
        User actor = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = requireBusinessId(user);
        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId()).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            if (!businessId.equals(supplier.getBusinessId())) throw new IllegalArgumentException("Forbidden");
        }
        SupplierDelivery d = SupplierDelivery.builder()
                .businessId(businessId)
                .supplier(supplier)
                .deliveryNoteRef(request.getDeliveryNoteRef() != null ? request.getDeliveryNoteRef().trim() : null)
                .deliveredAt(request.getDeliveredAt())
                .status("DRAFT")
                .createdBy(actor)
                .build();
        d = supplierDeliveryRepository.save(d);
        return toDto(d, List.of());
    }

    @Transactional
    public SupplierDeliveryDto addItem(AuthenticatedUser user, UUID deliveryId, AddSupplierDeliveryItemRequest request) {
        UUID businessId = requireBusinessId(user);
        SupplierDelivery d = supplierDeliveryRepository.findById(deliveryId).orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!businessId.equals(d.getBusinessId())) throw new IllegalArgumentException("Forbidden");
        if (!"DRAFT".equalsIgnoreCase(d.getStatus())) throw new IllegalArgumentException("Cannot add items: delivery is not in draft");

        Product p = productRepository.findById(request.getProductId()).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (p.getBusinessId() == null || !businessId.equals(p.getBusinessId())) {
            throw new IllegalArgumentException("Product does not belong to your business");
        }
        SupplierDeliveryItem item = SupplierDeliveryItem.builder()
                .delivery(d)
                .product(p)
                .productName(p.getName())
                .quantity(request.getQuantity())
                .unitCost(request.getUnitCost())
                .unitOfMeasure(request.getUnitOfMeasure() != null && !request.getUnitOfMeasure().isBlank() ? request.getUnitOfMeasure().trim() : null)
                .build();
        supplierDeliveryItemRepository.save(item);
        return get(user, deliveryId);
    }

    /**
     * Seller confirms physical receipt.
     * - For DISPATCHED: sets received_quantity per item (if different from supplier's) and moves directly to RECEIVED.
     * - For DRAFT (manually created): same behaviour.
     * Quantities are added to stock immediately; there is no intermediate processing stage.
     */
    @Transactional
    public SupplierDeliveryDto confirmReceipt(AuthenticatedUser user, UUID deliveryId, ConfirmReceiptRequest request) {
        String role = user.role() != null ? user.role().toLowerCase() : "";
        if (!"owner".equals(role) && !"staff".equals(role)) {
            throw new IllegalArgumentException("Only the business owner or staff can confirm delivery receipt");
        }
        User actor = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = requireBusinessId(user);
        SupplierDelivery d = supplierDeliveryRepository.findByIdWithParties(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!businessId.equals(d.getBusinessId())) throw new IllegalArgumentException("Forbidden");
        if (!"DISPATCHED".equalsIgnoreCase(d.getStatus()) && !"DRAFT".equalsIgnoreCase(d.getStatus())) {
            throw new IllegalArgumentException("Delivery must be dispatched (by supplier) or in draft");
        }

        List<SupplierDeliveryItem> items = supplierDeliveryItemRepository.findByDeliveryIdWithProduct(deliveryId);
        if (items.isEmpty()) throw new IllegalArgumentException("No items in delivery");

        java.util.Map<UUID, Integer> received = request != null && request.getReceivedQuantities() != null
                ? request.getReceivedQuantities() : java.util.Map.of();

        // Receipt only: record what was received; do not update product stock yet.
        // Seller can "Add to stock" later when the previous dispatch is sold out.
        for (SupplierDeliveryItem item : items) {
            Integer qty = received.get(item.getItemId());
            item.setReceivedQuantity(qty != null ? qty : item.getQuantity());
            supplierDeliveryItemRepository.save(item);
        }

        d.setStatus("RECEIVED");
        d.setReceivedAt(Instant.now());
        d.setReceivedBy(actor);
        supplierDeliveryRepository.save(d);

        // When a dispatch linked to a purchase order is received by the seller,
        // mark that purchase order as fulfilled/closed.
        if (d.getPurchaseOrder() != null) {
            PurchaseOrder po = d.getPurchaseOrder();
            if (!"FULFILLED".equalsIgnoreCase(po.getStatus())) {
                po.setStatus("FULFILLED");
                purchaseOrderRepository.save(po);
            }
        }

        return get(user, deliveryId);
    }

    /**
     * Add this delivery's received quantities to product stock. Allowed only when the delivery is RECEIVED
     * and has not already been added, and when every product in this delivery has zero current stock
     * (previous dispatch sold out). Do not mix dispatches.
     */
    @Transactional
    public SupplierDeliveryDto addDeliveryToStock(AuthenticatedUser user, UUID deliveryId) {
        String role = user.role() != null ? user.role().toLowerCase() : "";
        if (!"owner".equals(role) && !"staff".equals(role)) {
            throw new IllegalArgumentException("Only the business owner or staff can add delivery to stock");
        }
        UUID businessId = requireBusinessId(user);
        SupplierDelivery d = supplierDeliveryRepository.findByIdWithParties(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!businessId.equals(d.getBusinessId())) throw new IllegalArgumentException("Forbidden");
        if (!"RECEIVED".equalsIgnoreCase(d.getStatus())) {
            throw new IllegalArgumentException("Delivery must be received before adding to stock");
        }
        if (d.getStockUpdatedAt() != null) {
            throw new IllegalArgumentException("Stock from this delivery has already been added");
        }

        List<SupplierDeliveryItem> items = supplierDeliveryItemRepository.findByDeliveryIdWithProduct(deliveryId);
        java.util.List<String> productsWithExistingStock = new java.util.ArrayList<>();
        for (SupplierDeliveryItem item : items) {
            int receivedQty = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : (item.getQuantity() != null ? item.getQuantity() : 0);
            if (receivedQty <= 0) continue;
            Product product = item.getProduct();
            if (product == null) continue;
            int currentStock = product.getQuantity() != null ? product.getQuantity() : 0;
            if (currentStock > 0) {
                String name = product.getName() != null ? product.getName() : product.getProductId().toString();
                if (!productsWithExistingStock.contains(name)) {
                    productsWithExistingStock.add(name);
                }
            }
        }
        if (!productsWithExistingStock.isEmpty()) {
            throw new IllegalArgumentException(
                    "There is a dispatch whose products are still on sale. Do not mix dispatches. "
                            + "The following products still have stock from that dispatch: "
                            + String.join(", ", productsWithExistingStock)
                            + ". Sell or clear that stock before adding this delivery to stock.");
        }

        User actor = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        for (SupplierDeliveryItem item : items) {
            int receivedQty = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : (item.getQuantity() != null ? item.getQuantity() : 0);
            int converted = item.getConvertedQuantity() != null ? item.getConvertedQuantity() : 0;
            int qtyToAdd = receivedQty - converted;
            if (qtyToAdd <= 0) continue;
            Product product = item.getProduct();
            int prev = product.getQuantity() != null ? product.getQuantity() : 0;
            int next = prev + qtyToAdd;
            product.setQuantity(next);
            productRepository.save(product);
            stockLedgerService.recordSupplierReceive(
                    businessId,
                    product,
                    prev,
                    next,
                    d.getSupplier(),
                    d,
                    actor.getUserId(),
                    d.getDeliveryNoteRef() != null ? ("Delivery note: " + d.getDeliveryNoteRef()) : null
            );
        }
        d.setStockUpdatedAt(Instant.now());
        supplierDeliveryRepository.save(d);
        return get(user, deliveryId);
    }

    /**
     * Subdivide part of a received delivery item into separate sale units / product.
     * Decreases stock on the source product and increases stock on the target product.
     */
    @Transactional
    public SupplierDeliveryDto convertItem(AuthenticatedUser user, UUID deliveryId, UUID itemId, ConvertDeliveryItemRequest request) {
        String role = user.role() != null ? user.role().toLowerCase() : "";
        if (!"owner".equals(role) && !"staff".equals(role)) {
            throw new IllegalArgumentException("Only the business owner or staff can convert received items");
        }
        User actor = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = requireBusinessId(user);

        SupplierDelivery delivery = supplierDeliveryRepository.findByIdWithParties(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!businessId.equals(delivery.getBusinessId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        if (!"RECEIVED".equalsIgnoreCase(delivery.getStatus())) {
            throw new IllegalArgumentException("You can only convert items from a RECEIVED delivery");
        }

        SupplierDeliveryItem item = supplierDeliveryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery item not found"));
        if (!item.getDelivery().getDeliveryId().equals(deliveryId)) {
            throw new IllegalArgumentException("Item does not belong to this delivery");
        }

        Product sourceProduct = item.getProduct();
        if (sourceProduct == null || sourceProduct.getBusinessId() == null || !businessId.equals(sourceProduct.getBusinessId())) {
            throw new IllegalArgumentException("Source product not found for this business");
        }

        int baseReceived = item.getReceivedQuantity() != null
                ? item.getReceivedQuantity()
                : (item.getQuantity() != null ? item.getQuantity() : 0);
        int convertedAlready = item.getConvertedQuantity() != null ? item.getConvertedQuantity() : 0;
        int remainingForConversion = baseReceived - convertedAlready;
        int sourceUsed = request.getSourceQuantityUsed() != null ? request.getSourceQuantityUsed() : remainingForConversion;
        if (sourceUsed <= 0) {
            throw new IllegalArgumentException("Source quantity used must be greater than zero");
        }

        // Derive produced quantity: explicit, or unit-based (targetUnitSize + targetUnit), or piecesPerUnit.
        Integer explicitProduced = request.getProducedQuantity();
        Integer piecesPerUnit = request.getPiecesPerUnit();
        BigDecimal targetUnitSize = request.getTargetUnitSize();
        String targetUnit = request.getTargetUnit() != null ? request.getTargetUnit().trim() : null;
        String sourceUnit = item.getUnitOfMeasure() != null ? item.getUnitOfMeasure().trim() : null;

        int produced;
        BigDecimal derivedCostPerUnit = null; // for unit-based subdivision: cost per sub-unit (no loss)
        if (explicitProduced != null) {
            produced = explicitProduced;
        } else if (targetUnitSize != null && targetUnitSize.compareTo(BigDecimal.ZERO) > 0 && targetUnit != null && !targetUnit.isBlank()
                && sourceUnit != null && !sourceUnit.isBlank()) {
            // Unit-based subdivision: e.g. 10 kg → 500 g → 20 sub-units; cost per sub-unit = total cost / 20
            BigDecimal sourceMultiplier = unitToBaseMultiplier(sourceUnit);
            BigDecimal targetMultiplier = unitToBaseMultiplier(targetUnit);
            if (sourceMultiplier == null || targetMultiplier == null) {
                throw new IllegalArgumentException("Unsupported unit for subdivision. Use kg, g, L, ml, or piece.");
            }
            BigDecimal sourceBase = BigDecimal.valueOf(sourceUsed).multiply(sourceMultiplier);
            BigDecimal targetBase = targetUnitSize.multiply(targetMultiplier);
            if (targetBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Target unit size must be greater than zero");
            }
            produced = sourceBase.divide(targetBase, 0, RoundingMode.DOWN).intValue();
            if (produced <= 0) {
                throw new IllegalArgumentException("Subdivision yields no units: source quantity in target unit is less than one sub-unit size");
            }
            BigDecimal totalCost = item.getUnitCost() != null
                    ? item.getUnitCost().multiply(BigDecimal.valueOf(sourceUsed)).setScale(2, RoundingMode.HALF_UP)
                    : null;
            if (totalCost != null && totalCost.compareTo(BigDecimal.ZERO) > 0) {
                derivedCostPerUnit = totalCost.divide(BigDecimal.valueOf(produced), 2, RoundingMode.HALF_UP);
            }
        } else if (piecesPerUnit != null) {
            if (piecesPerUnit <= 0) {
                throw new IllegalArgumentException("Pieces per unit must be greater than zero");
            }
            produced = sourceUsed * piecesPerUnit;
        } else {
            throw new IllegalArgumentException("Provide produced quantity, or pieces per unit, or target unit size + target unit (e.g. 500 and g for 500 g sub-units)");
        }
        if (produced <= 0) {
            throw new IllegalArgumentException("Produced quantity must be greater than zero");
        }

        // Determine or create target product
        Product targetProduct;
        if (request.getTargetProductId() != null) {
            targetProduct = productRepository.findById(request.getTargetProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Target product not found"));
            if (targetProduct.getBusinessId() == null || !businessId.equals(targetProduct.getBusinessId())) {
                throw new IllegalArgumentException("Target product does not belong to your business");
            }
        } else {
            String name = request.getTargetName() != null && !request.getTargetName().isBlank()
                    ? request.getTargetName().trim()
                    : (sourceProduct.getName() != null ? sourceProduct.getName() : "Converted item");
            // If target price is not provided, derive a default from supplier cost and subdivision (no loss).
            BigDecimal targetPrice = request.getTargetPrice();
            if (targetPrice == null) {
                if (derivedCostPerUnit != null) {
                    targetPrice = derivedCostPerUnit; // unit-based: cost per sub-unit
                } else {
                    BigDecimal unitCost = item.getUnitCost();
                    if (unitCost != null && piecesPerUnit != null && piecesPerUnit > 0) {
                        targetPrice = unitCost
                                .divide(BigDecimal.valueOf(piecesPerUnit), 2, RoundingMode.HALF_UP);
                    } else if (unitCost != null) {
                        targetPrice = unitCost.setScale(2, RoundingMode.HALF_UP);
                    } else {
                        throw new IllegalArgumentException("Cannot derive selling price: supplier cost is missing. Please provide a target price.");
                    }
                }
            }
            targetProduct = Product.builder()
                    .businessId(businessId)
                    .name(name)
                    .price(targetPrice)
                    .quantity(0)
                    .build();
            targetProduct = productRepository.save(targetProduct);
        }

        // If stock has not yet been added for this delivery, we only track how much
        // of the received quantity has been converted, and we create/increase stock
        // on the target product. The original product will receive only the
        // remaining quantity when addDeliveryToStock is called.
        if (delivery.getStockUpdatedAt() == null) {
            int newConverted = convertedAlready + sourceUsed;
            if (newConverted > baseReceived) {
                throw new IllegalArgumentException("You can only convert up to the received quantity for this delivery item");
            }
            item.setConvertedQuantity(newConverted);
            supplierDeliveryItemRepository.save(item);
        } else {
            // Stock already added: decrease source stock now and move into target product.
            int sourceCurrentQty = sourceProduct.getQuantity() != null ? sourceProduct.getQuantity() : 0;
            if (sourceUsed > sourceCurrentQty) {
                throw new IllegalArgumentException("Not enough stock on the source product to convert");
            }
            int srcPrev = sourceCurrentQty;
            int srcNext = srcPrev - sourceUsed;
            sourceProduct.setQuantity(srcNext);
            productRepository.save(sourceProduct);
            stockLedgerService.recordManualAdjustment(
                    businessId,
                    sourceProduct,
                    srcPrev,
                    srcNext,
                    actor.getUserId(),
                    request.getNote() != null ? request.getNote() : "Convert delivery item: consume source quantity"
            );
        }

        // Increase target stock (new sale units are available immediately)
        int tgtPrev = targetProduct.getQuantity() != null ? targetProduct.getQuantity() : 0;
        int tgtNext = tgtPrev + produced;
        targetProduct.setQuantity(tgtNext);
        productRepository.save(targetProduct);
        stockLedgerService.recordManualAdjustment(
                businessId,
                targetProduct,
                tgtPrev,
                tgtNext,
                actor.getUserId(),
                request.getNote() != null ? request.getNote() : "Convert delivery item: produce sale units"
        );

        return get(user, deliveryId);
    }

    private UUID requireBusinessId(AuthenticatedUser user) {
        if (user == null) throw new IllegalArgumentException("Not authenticated");
        return userRepository.findById(user.userId())
                .map(User::getBusinessId)
                .filter(id -> id != null)
                .orElseThrow(() -> new IllegalArgumentException("Business not set"));
    }

    private SupplierDeliveryDto toDto(SupplierDelivery d, List<SupplierDeliveryItemDto> items) {
        int totalQty = items.stream().mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0).sum();
        BigDecimal totalCost = items.stream()
                .map(it -> it.getLineTotal() != null ? it.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceivedCost = items.stream()
                .map(it -> it.getReceivedLineTotal() != null ? it.getReceivedLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal potentialRevenue = BigDecimal.ZERO;
        for (SupplierDeliveryItemDto it : items) {
            int rq = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : 0;
            BigDecimal pp = it.getProductPrice() != null ? it.getProductPrice() : BigDecimal.ZERO;
            potentialRevenue = potentialRevenue.add(pp.multiply(BigDecimal.valueOf(rq)));
        }
        BigDecimal profitLoss = potentialRevenue.subtract(totalReceivedCost);

        return SupplierDeliveryDto.builder()
                .id(d.getDeliveryId())
                .businessId(d.getBusinessId())
                .supplierId(d.getSupplier() != null ? d.getSupplier().getSupplierId() : null)
                .supplierName(d.getSupplier() != null ? d.getSupplier().getName() : null)
                .purchaseOrderId(d.getPurchaseOrder() != null ? d.getPurchaseOrder().getPurchaseOrderId() : null)
                .poNumber(d.getPurchaseOrder() != null ? d.getPurchaseOrder().getPoNumber() : null)
                .deliveryNoteRef(d.getDeliveryNoteRef())
                .deliveredAt(d.getDeliveredAt())
                .receivedAt(d.getReceivedAt())
                .receivedByUserId(d.getReceivedBy() != null ? d.getReceivedBy().getUserId() : null)
                .receivedByName(d.getReceivedBy() != null ? (d.getReceivedBy().getName() != null ? d.getReceivedBy().getName() : d.getReceivedBy().getEmail()) : null)
                .status(d.getStatus())
                .stockUpdatedAt(d.getStockUpdatedAt())
                .createdAt(d.getCreatedAt())
                .items(items)
                .totalQuantity(totalQty)
                .totalCost(totalCost.setScale(2, RoundingMode.HALF_UP))
                .totalReceivedCost(totalReceivedCost.setScale(2, RoundingMode.HALF_UP))
                .potentialRevenue(potentialRevenue.setScale(2, RoundingMode.HALF_UP))
                .profitLoss(profitLoss.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private SupplierDeliveryItemDto toItemDto(SupplierDeliveryItem i) {
        BigDecimal uc = i.getUnitCost() != null ? i.getUnitCost() : BigDecimal.ZERO;
        int qty = i.getQuantity() != null ? i.getQuantity() : 0;
        int rq = i.getReceivedQuantity() != null ? i.getReceivedQuantity() : 0;
        BigDecimal lineTotal = uc.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal receivedLineTotal = uc.multiply(BigDecimal.valueOf(rq)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal productPrice = i.getProduct() != null ? i.getProduct().getPrice() : null;
        return SupplierDeliveryItemDto.builder()
                .id(i.getItemId())
                .productId(i.getProduct() != null ? i.getProduct().getProductId() : null)
                .productName(i.getProductName())
                .quantity(i.getQuantity())
                .receivedQuantity(i.getReceivedQuantity())
                .unitCost(i.getUnitCost())
                .unitOfMeasure(i.getUnitOfMeasure())
                .convertedQuantity(i.getConvertedQuantity())
                .lineTotal(lineTotal)
                .receivedLineTotal(receivedLineTotal)
                .productPrice(productPrice)
                .createdAt(i.getCreatedAt())
                .build();
    }

    /**
     * Multiplier to convert 1 unit to base (grams for weight, ml for volume, 1 for piece).
     * Used for unit-based subdivision so e.g. 10 kg → 500 g gives 20 sub-units.
     */
    private static BigDecimal unitToBaseMultiplier(String unit) {
        if (unit == null || unit.isBlank()) return null;
        String u = unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "kg":
            case "kilogram":
            case "kilograms":
                return new BigDecimal("1000");
            case "g":
            case "gram":
            case "grams":
                return BigDecimal.ONE;
            case "l":
            case "liter":
            case "litre":
            case "liters":
            case "litres":
                return new BigDecimal("1000");
            case "ml":
            case "milliliter":
            case "millilitre":
            case "milliliters":
            case "millilitres":
                return BigDecimal.ONE;
            case "piece":
            case "pieces":
            case "unit":
            case "units":
            case "pcs":
                return BigDecimal.ONE;
            default:
                return null;
        }
    }
}

