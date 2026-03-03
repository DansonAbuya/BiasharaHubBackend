package com.biasharahub.service;

import com.biasharahub.dto.request.AddSupplierDeliveryItemRequest;
import com.biasharahub.dto.request.ConfirmReceiptRequest;
import com.biasharahub.dto.request.CreateSupplierDeliveryRequest;
import com.biasharahub.dto.request.SubmitDispatchRequest;
import com.biasharahub.dto.response.SupplierDeliveryDto;
import com.biasharahub.dto.response.SupplierDeliveryItemDto;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.Supplier;
import com.biasharahub.entity.SupplierDelivery;
import com.biasharahub.entity.SupplierDeliveryItem;
import com.biasharahub.entity.User;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierDeliveryService {

    private final SupplierRepository supplierRepository;
    private final SupplierDeliveryRepository supplierDeliveryRepository;
    private final SupplierDeliveryItemRepository supplierDeliveryItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockLedgerService stockLedgerService;

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

        SupplierDelivery d = SupplierDelivery.builder()
                .businessId(businessId)
                .supplier(supplier)
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
            SupplierDeliveryItem item = SupplierDeliveryItem.builder()
                    .delivery(d)
                    .product(p)
                    .productName(p.getName())
                    .quantity(di.getQuantity())
                    .unitCost(di.getUnitCost())
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

        for (SupplierDeliveryItem item : items) {
            Integer qty = received.get(item.getItemId());
            // If client sent a non-null quantity (including 0), use it; otherwise fall back to supplier-stated quantity.
            item.setReceivedQuantity(qty != null ? qty : item.getQuantity());
            supplierDeliveryItemRepository.save(item);

            int qtyToAdd = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : (item.getQuantity() != null ? item.getQuantity() : 0);
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

        d.setStatus("RECEIVED");
        d.setReceivedAt(Instant.now());
        d.setReceivedBy(actor);
        supplierDeliveryRepository.save(d);

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
                .deliveryNoteRef(d.getDeliveryNoteRef())
                .deliveredAt(d.getDeliveredAt())
                .receivedAt(d.getReceivedAt())
                .receivedByUserId(d.getReceivedBy() != null ? d.getReceivedBy().getUserId() : null)
                .receivedByName(d.getReceivedBy() != null ? (d.getReceivedBy().getName() != null ? d.getReceivedBy().getName() : d.getReceivedBy().getEmail()) : null)
                .status(d.getStatus())
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
                .lineTotal(lineTotal)
                .receivedLineTotal(receivedLineTotal)
                .productPrice(productPrice)
                .createdAt(i.getCreatedAt())
                .build();
    }
}

