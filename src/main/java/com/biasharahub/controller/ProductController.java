package com.biasharahub.controller;

import com.biasharahub.dto.response.BusinessDto;
import com.biasharahub.dto.response.ProductCategoryDto;
import com.biasharahub.dto.response.ProductDto;
import com.biasharahub.entity.InventoryImage;
import com.biasharahub.entity.ProductCategory;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ProductCategoryRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final UserRepository userRepository;
    private final Optional<R2StorageService> r2StorageService;

    /** List shops (verified owners’ businesses) for marketplace. Each seller has one shop; only verified shops appear. No auth required. */
    @GetMapping("/businesses")
    @Cacheable(
            cacheNames = "marketplaceBusinesses",
            key = "T(com.biasharahub.config.TenantContext).getTenantSchema()"
    )
    public List<BusinessDto> listBusinesses() {
        List<BusinessDto> businesses = userRepository.findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified")
                .stream()
                .filter(u -> u.getAccountStatus() == null || "active".equalsIgnoreCase(u.getAccountStatus()))
                .map(u -> BusinessDto.builder()
                        .id(u.getBusinessId())
                        .name(u.getBusinessName() != null ? u.getBusinessName() : "—")
                        .ownerName(u.getName() != null ? u.getName() : u.getEmail())
                        .sellerTier(u.getSellerTier() != null ? u.getSellerTier() : "tier1")
                        .build())
                .collect(Collectors.toList());
        return businesses;
    }

    /** List product categories for frontend dropdown (e.g. when uploading/creating a product). No auth required. */
    @GetMapping("/categories")
    @Cacheable(
            cacheNames = "productCategories",
            key = "T(com.biasharahub.config.TenantContext).getTenantSchema()"
    )
    public List<ProductCategoryDto> listCategories() {
        List<ProductCategoryDto> categories = productCategoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(c -> ProductCategoryDto.builder()
                        .id(c.getCategoryId())
                        .name(c.getName())
                        .displayOrder(c.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());
        return categories;
    }

    /**
     * List products.
     * - Owner / staff (Seller Center): only products belonging to their business (any moderation status).
     * - Customers / anonymous: only verified businesses and approved products; optional filter by businessId (shop) so customers see all products for the shop they select.
     */
    @GetMapping
    @Cacheable(
            cacheNames = "publicProducts",
            key = "T(com.biasharahub.config.TenantContext).getTenantSchema() + '|' + T(java.util.Objects).toString(#category) + '|' + T(java.util.Objects).toString(#businessId) + '|' + T(java.util.Objects).toString(#businessName) + '|' + T(java.util.Objects).toString(#ownerId)",
            condition = "#currentUser == null || (#currentUser.role() != null && #currentUser.role().toLowerCase() == 'customer')"
    )
    public List<ProductDto> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) String businessName,
            @RequestParam(required = false) UUID ownerId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<Product> products;
        Set<UUID> businessIds;

        if (isOwnerOrStaff(currentUser)) {
            // Seller Center: owner/staff see only their business's products (including pending/rejected)
            UUID myBusinessId = getBusinessId(currentUser);
            if (myBusinessId == null) {
                products = List.of();
            } else {
                businessIds = Set.of(myBusinessId);
                products = category != null && !category.isBlank()
                        ? productRepository.findByBusinessIdInAndCategory(businessIds, category)
                        : productRepository.findByBusinessIdIn(businessIds);
                // No moderation filter — they see all their products in Seller Center
            }
        } else {
            // Customers / anonymous: apply request filters, then restrict to verified + approved (storefront)
            businessIds = resolveBusinessFilter(businessId, businessName, ownerId);
            if (businessIds != null && businessIds.isEmpty()) {
                products = List.of();
            } else if (businessIds != null) {
                products = category != null && !category.isBlank()
                        ? productRepository.findByBusinessIdInAndCategory(businessIds, category)
                        : productRepository.findByBusinessIdIn(businessIds);
            } else {
                products = category != null && !category.isBlank()
                        ? productRepository.findByCategory(category)
                        : productRepository.findAllWithImages();
            }
            Set<UUID> verifiedBusinessIds = userRepository.findByRoleIgnoreCaseAndVerificationStatusOrderByCreatedAtAsc("owner", "verified")
                    .stream()
                    .filter(u -> u.getAccountStatus() == null || "active".equalsIgnoreCase(u.getAccountStatus()))
                    .map(User::getUserId)
                    .collect(Collectors.toSet());
            products = products.stream()
                    .filter(p -> p.getBusinessId() != null && verifiedBusinessIds.contains(p.getBusinessId())
                            && "approved".equalsIgnoreCase(p.getModerationStatus()))
                    .toList();
        }
        return products.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Resolve businessId, businessName, or ownerId to a set of business IDs for customer filter. Returns null if no filter. */
    private Set<UUID> resolveBusinessFilter(UUID businessId, String businessName, UUID ownerId) {
        if (businessId != null) {
            return Set.of(businessId);
        }
        if (ownerId != null) {
            return userRepository.findById(ownerId)
                    .filter(u -> "owner".equalsIgnoreCase(u.getRole()) && u.getBusinessId() != null)
                    .map(u -> Set.of(u.getBusinessId()))
                    .orElse(Set.of());
        }
        if (businessName != null && !businessName.isBlank()) {
            List<UUID> ids = userRepository.findByRoleIgnoreCaseAndBusinessNameContainingIgnoreCase("owner", businessName.trim())
                    .stream()
                    .map(User::getBusinessId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
        }
        return null;
    }

    /**
     * Get single product.
     * - Owner / staff: only products belonging to their business (any moderation status, for Seller Center).
     * - Customers / anonymous: only products visible on storefront (verified business, approved).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return productRepository.findByProductIdWithImages(id)
                .filter(product -> canAccessProduct(product, currentUser))
                .filter(product -> allowViewProduct(product, currentUser))
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Owner/staff: only their business. Customer/anonymous: only storefront-visible (verified + approved). */
    private boolean allowViewProduct(Product product, AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return isProductVisibleOnStorefront(product);
        }
        if (isOwnerOrStaff(currentUser)) {
            return canAccessProduct(product, currentUser); // already checked above; allows their business only
        }
        return isProductVisibleOnStorefront(product); // customer: only approved from verified shops
    }

    /** True if product is from a verified business and approved (same rules as listProducts for public storefront). */
    private boolean isProductVisibleOnStorefront(Product product) {
        if (product.getBusinessId() == null || !"approved".equalsIgnoreCase(product.getModerationStatus())) {
            return false;
        }
        Set<UUID> verifiedBusinessIds = userRepository.findByRoleIgnoreCaseAndVerificationStatusOrderByCreatedAtAsc("owner", "verified")
                .stream()
                .map(User::getUserId)
                .collect(Collectors.toSet());
        return verifiedBusinessIds.contains(product.getBusinessId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> createProduct(
            @RequestBody ProductDto dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User owner = userRepository.findById(businessId).orElse(null);
        if (owner == null || !"verified".equalsIgnoreCase(owner.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Business must be verified before adding products. Submit documents in Verification and wait for admin approval."));
        }
        Product product = Product.builder()
                .name(dto.getName())
                .category(dto.getCategory())
                .price(dto.getPrice())
                .quantity(dto.getQuantity() != null ? dto.getQuantity() : 0)
                .description(dto.getDescription())
                .businessId(businessId)
                .build();
        attachImages(product, dto.getImages(), dto.getImage());
        product = productRepository.save(product);
        return ResponseEntity.ok(toDto(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable UUID id,
            @RequestBody ProductDto dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return productRepository.findByProductIdAndBusinessId(id, businessId)
                .map(product -> {
                    if (dto.getName() != null) product.setName(dto.getName());
                    if (dto.getCategory() != null) product.setCategory(dto.getCategory());
                    if (dto.getPrice() != null) product.setPrice(dto.getPrice());
                    if (dto.getQuantity() != null) product.setQuantity(dto.getQuantity());
                    if (dto.getDescription() != null) product.setDescription(dto.getDescription());
                    if (dto.getImages() != null) attachImages(product, dto.getImages(), dto.getImage());
                    product = productRepository.save(product);
                    return ResponseEntity.ok(toDto(product));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (productRepository.existsByProductIdAndBusinessId(id, businessId)) {
            productRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (r2StorageService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Image upload is not configured (R2 disabled)."));
        }
        try {
            String url = r2StorageService.get().uploadProductImage(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /** Attach image URLs to product (e.g. from R2 upload). mainImageUrl optionally marks one as main. */
    private void attachImages(Product product, List<String> imageUrls, String mainImageUrl) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        product.getImages().clear();
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) continue;
            InventoryImage img = InventoryImage.builder()
                    .product(product)
                    .imageUrl(url.trim())
                    .isMain(url.equals(mainImageUrl) || (mainImageUrl == null && product.getImages().isEmpty()))
                    .build();
            product.getImages().add(img);
        }
        if (!product.getImages().isEmpty() && product.getImages().stream().noneMatch(InventoryImage::getIsMain)) {
            product.getImages().get(0).setIsMain(true);
        }
    }

    private boolean isOwnerOrStaff(AuthenticatedUser user) {
        if (user == null) return false;
        String r = user.role();
        return "owner".equalsIgnoreCase(r) || "staff".equalsIgnoreCase(r);
    }

    private UUID getBusinessId(AuthenticatedUser user) {
        if (user == null) return null;
        return userRepository.findById(user.userId())
                .map(User::getBusinessId)
                .orElse(null);
    }

    private boolean canAccessProduct(Product product, AuthenticatedUser currentUser) {
        if (currentUser == null) return true;
        if (isOwnerOrStaff(currentUser)) {
            UUID businessId = getBusinessId(currentUser);
            return businessId != null && businessId.equals(product.getBusinessId());
        }
        return true;
    }

    private ProductDto toDto(Product p) {
        String mainImage = p.getImages().stream()
                .filter(InventoryImage::getIsMain)
                .findFirst()
                .map(InventoryImage::getImageUrl)
                .orElse(p.getImages().isEmpty() ? null : p.getImages().get(0).getImageUrl());
        List<String> images = p.getImages().stream().map(InventoryImage::getImageUrl).toList();
        return ProductDto.builder()
                .id(p.getProductId())
                .name(p.getName())
                .category(p.getCategory())
                .price(p.getPrice())
                .quantity(p.getQuantity())
                .description(p.getDescription())
                .image(mainImage)
                .images(images)
                .businessId(p.getBusinessId() != null ? p.getBusinessId().toString() : null)
                .moderationStatus(p.getModerationStatus())
                .build();
    }
}
