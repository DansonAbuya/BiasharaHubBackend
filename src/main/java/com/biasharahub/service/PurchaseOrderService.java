package com.biasharahub.service;

import com.biasharahub.dto.request.CreatePurchaseOrderRequest;
import com.biasharahub.dto.response.PurchaseOrderDto;
import com.biasharahub.dto.response.PurchaseOrderItemDto;
import com.biasharahub.entity.*;
import com.biasharahub.repository.PurchaseOrderRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.SupplierDeliveryRepository;
import com.biasharahub.repository.SupplierRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierDeliveryRepository supplierDeliveryRepository;
    private final UserRepository userRepository;

    @Transactional
    public PurchaseOrderDto create(AuthenticatedUser user, CreatePurchaseOrderRequest request) {
        User actor = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = requireBusinessId(user);

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        if (!businessId.equals(supplier.getBusinessId())) {
            throw new IllegalArgumentException("Supplier does not belong to your business");
        }

        String generatedPoNumber = generatePoNumber(businessId);

        PurchaseOrder po = PurchaseOrder.builder()
                .businessId(businessId)
                .supplier(supplier)
                // Always use a system-generated PO number; ignore any client-provided value
                .poNumber(generatedPoNumber)
                .deliveryNoteRef(request.getDeliveryNoteRef() != null ? request.getDeliveryNoteRef().trim() : null)
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .status("DRAFT")
                .createdBy(actor)
                .build();

        for (CreatePurchaseOrderRequest.Item itemReq : request.getItems()) {
            Product product = null;
            if (itemReq.getProductId() != null) {
                product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.getProductId()));
                if (product.getBusinessId() == null || !businessId.equals(product.getBusinessId())) {
                    throw new IllegalArgumentException("Product does not belong to your business");
                }
            }
            if (product == null && (itemReq.getDescription() == null || itemReq.getDescription().isBlank())) {
                throw new IllegalArgumentException("Description is required when product is not selected");
            }

            // If the seller did not select an existing product, automatically create
            // a placeholder product for this PO line. The seller can later edit the
            // product's customer-facing name, price and other details.
            if (product == null) {
                String baseDescription = itemReq.getDescription() != null ? itemReq.getDescription().trim() : "Unnamed item";
                String name = (itemReq.getCustomerName() != null && !itemReq.getCustomerName().isBlank())
                        ? itemReq.getCustomerName().trim()
                        : baseDescription;
                BigDecimal price = itemReq.getCustomerPrice() != null
                        ? itemReq.getCustomerPrice()
                        : (itemReq.getExpectedUnitCost() != null ? itemReq.getExpectedUnitCost() : BigDecimal.ZERO);
                // This product is used to talk to the supplier (unit, description),
                // not directly as a customer-facing item.
                product = Product.builder()
                        .businessId(businessId)
                        .name(name)
                        .price(price)
                        .quantity(0)
                        .supplierFacingOnly(true)
                        // moderationStatus defaults to pending_review
                        .build();
                product = productRepository.save(product);
            }

            PurchaseOrderItem poi = PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .product(product)
                    .description(itemReq.getDescription() != null ? itemReq.getDescription().trim() : null)
                    .unitOfMeasure(itemReq.getUnitOfMeasure() != null ? itemReq.getUnitOfMeasure().trim() : null)
                    .requestedQuantity(itemReq.getRequestedQuantity())
                    .expectedUnitCost(itemReq.getExpectedUnitCost())
                    .build();
            po.getItems().add(poi);
        }

        po = purchaseOrderRepository.save(po);
        return toDto(po);
    }

    private String generatePoNumber(UUID businessId) {
        long countForBusiness = purchaseOrderRepository.countByBusinessId(businessId);
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        long sequence = countForBusiness + 1;
        return String.format("PO-%s-%04d", datePart, sequence);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> listForBusiness(AuthenticatedUser user) {
        UUID businessId = requireBusinessId(user);
        return purchaseOrderRepository.findByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> listForSupplier(AuthenticatedUser user) {
        if (user == null || user.role() == null || !"supplier".equalsIgnoreCase(user.role())) {
            throw new IllegalArgumentException("Only suppliers can view their purchase orders");
        }
        User supplierUser = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = supplierUser.getBusinessId();
        if (businessId == null) {
            throw new IllegalArgumentException("Supplier is not linked to a business");
        }
        Supplier supplier = supplierRepository.findByEmailIgnoreCaseAndBusinessId(supplierUser.getEmail(), businessId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier record not found for your account"));

        return purchaseOrderRepository
                .findByBusinessIdAndSupplier_SupplierIdOrderByCreatedAtDesc(businessId, supplier.getSupplierId())
                .stream()
                // Once a dispatch has been submitted for a PO, hide it from the supplier
                .filter(po -> !supplierDeliveryRepository.existsByPurchaseOrder_PurchaseOrderId(po.getPurchaseOrderId()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto getForBusiness(AuthenticatedUser user, UUID id) {
        UUID businessId = requireBusinessId(user);
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!businessId.equals(po.getBusinessId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        return toDto(po);
    }

    /**
     * Supplier portal: get a single purchase order (with full item breakdown) for the logged-in supplier.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderDto getForSupplier(AuthenticatedUser user, UUID id) {
        if (user == null || user.role() == null || !"supplier".equalsIgnoreCase(user.role())) {
            throw new IllegalArgumentException("Only suppliers can view purchase order details");
        }
        User supplierUser = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = supplierUser.getBusinessId();
        if (businessId == null) {
            throw new IllegalArgumentException("Supplier is not linked to a business");
        }
        Supplier supplier = supplierRepository.findByEmailIgnoreCaseAndBusinessId(supplierUser.getEmail(), businessId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier record not found for your account"));
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!businessId.equals(po.getBusinessId()) || po.getSupplier() == null
                || !supplier.getSupplierId().equals(po.getSupplier().getSupplierId())) {
            throw new IllegalArgumentException("Purchase order not found or access denied");
        }
        return toDto(po);
    }

    private UUID requireBusinessId(AuthenticatedUser user) {
        if (user == null) throw new IllegalArgumentException("Not authenticated");
        return userRepository.findById(user.userId())
                .map(User::getBusinessId)
                .filter(id -> id != null)
                .orElseThrow(() -> new IllegalArgumentException("Business not set"));
    }

    private PurchaseOrderDto toDto(PurchaseOrder po) {
        List<PurchaseOrderItemDto> itemDtos = po.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
        return PurchaseOrderDto.builder()
                .id(po.getPurchaseOrderId())
                .businessId(po.getBusinessId())
                .supplierId(po.getSupplier() != null ? po.getSupplier().getSupplierId() : null)
                .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : null)
                .poNumber(po.getPoNumber())
                .deliveryNoteRef(po.getDeliveryNoteRef())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .createdByName(po.getCreatedBy() != null
                        ? (po.getCreatedBy().getName() != null ? po.getCreatedBy().getName() : po.getCreatedBy().getEmail())
                        : null)
                .items(itemDtos)
                .build();
    }

    private PurchaseOrderItemDto toItemDto(PurchaseOrderItem item) {
        return PurchaseOrderItemDto.builder()
                .id(item.getItemId())
                .purchaseOrderId(item.getPurchaseOrder() != null ? item.getPurchaseOrder().getPurchaseOrderId() : null)
                .productId(item.getProduct() != null ? item.getProduct().getProductId() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .description(item.getDescription())
                .unitOfMeasure(item.getUnitOfMeasure())
                .requestedQuantity(item.getRequestedQuantity())
                .expectedUnitCost(item.getExpectedUnitCost())
                .createdAt(item.getCreatedAt())
                .build();
    }
}

