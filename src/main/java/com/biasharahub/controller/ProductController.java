package com.biasharahub.controller;

import com.biasharahub.dto.response.ProductDto;
import com.biasharahub.entity.InventoryImage;
import com.biasharahub.entity.Product;
import com.biasharahub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<ProductDto>> listProducts(@RequestParam(required = false) String category) {
        List<Product> products = category != null && !category.isBlank()
                ? productRepository.findByCategory(category)
                : productRepository.findAll();
        return ResponseEntity.ok(products.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable UUID id) {
        return productRepository.findById(id)
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto dto) {
        Product product = Product.builder()
                .name(dto.getName())
                .category(dto.getCategory())
                .price(dto.getPrice())
                .quantity(dto.getQuantity() != null ? dto.getQuantity() : 0)
                .description(dto.getDescription())
                .build();
        product = productRepository.save(product);
        return ResponseEntity.ok(toDto(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable UUID id, @RequestBody ProductDto dto) {
        return productRepository.findById(id)
                .map(product -> {
                    if (dto.getName() != null) product.setName(dto.getName());
                    if (dto.getCategory() != null) product.setCategory(dto.getCategory());
                    if (dto.getPrice() != null) product.setPrice(dto.getPrice());
                    if (dto.getQuantity() != null) product.setQuantity(dto.getQuantity());
                    if (dto.getDescription() != null) product.setDescription(dto.getDescription());
                    product = productRepository.save(product);
                    return ResponseEntity.ok(toDto(product));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
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
                .build();
    }
}
