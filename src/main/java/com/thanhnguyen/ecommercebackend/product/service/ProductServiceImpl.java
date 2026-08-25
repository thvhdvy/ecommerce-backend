package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductImageRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductImageResponse;
import com.thanhnguyen.ecommercebackend.product.dto.ProductResponse;
import com.thanhnguyen.ecommercebackend.product.dto.ProductSearchCriteria;
import com.thanhnguyen.ecommercebackend.product.dto.ProductStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.entity.Brand;
import com.thanhnguyen.ecommercebackend.product.entity.Category;
import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.product.entity.ProductImage;
import com.thanhnguyen.ecommercebackend.product.exception.BrandNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.CategoryNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.MultiplePrimaryImagesException;
import com.thanhnguyen.ecommercebackend.product.exception.NotASellerException;
import com.thanhnguyen.ecommercebackend.product.exception.ProductNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.ProductOwnershipException;
import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import com.thanhnguyen.ecommercebackend.product.repository.BrandRepository;
import com.thanhnguyen.ecommercebackend.product.repository.CategoryRepository;
import com.thanhnguyen.ecommercebackend.product.repository.ProductRepository;
import com.thanhnguyen.ecommercebackend.product.specification.ProductSpecifications;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public ProductResponse create(User currentUser, ProductCreateRequest request) {
        Seller seller = resolveSeller(currentUser);

        Product product = new Product();
        product.setSeller(seller);
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setBrand(resolveBrand(request.getBrandId()));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStatus(ProductStatus.ACTIVE);

        applyImages(product, request.getImages());

        Product saved = productRepository.save(product);
        inventoryService.initializeInventory(saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(User currentUser, Long productId, ProductUpdateRequest request) {
        Product product = findOwnedProduct(currentUser, productId);

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCategoryId() != null) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }
        if (request.getBrandId() != null) {
            product.setBrand(resolveBrand(request.getBrandId()));
        }
        if (request.getImages() != null) {
            applyImages(product, request.getImages());
        }

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(User currentUser, Long productId, ProductStatusUpdateRequest request) {
        Product product = findOwnedProduct(currentUser, productId);
        product.setStatus(request.getStatus());

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public InventoryResponse updateInventory(User currentUser, Long productId, UpdateInventoryRequest request) {
        Product product = findOwnedProduct(currentUser, productId);
        return inventoryService.updateStock(product.getId(), request.getQuantityAvailable());
    }

    @Override
    public ProductResponse getActiveById(Long productId) {
        Product product = productRepository.findByIdAndStatus(productId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toResponse(product);
    }

    @Override
    public PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = productRepository.findAll(ProductSpecifications.fromCriteria(criteria), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional
    public void updateStockFlag(Long productId, boolean inStock) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        // Bo qua neu khong doi de tranh UPDATE thua + bump updatedAt gia (moi lan reserve/release
        // deu goi ham nay du inStock co doi hay khong — xem InventoryServiceImpl.syncStockFlag).
        if (product.isInStock() == inStock) {
            return;
        }
        product.setInStock(inStock);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void recalculateRating(Long productId, BigDecimal ratingAvg) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setRatingAvg(ratingAvg);
        productRepository.save(product);
    }

    private Product findOwnedProduct(User currentUser, Long productId) {
        Seller seller = resolveSeller(currentUser);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new ProductOwnershipException();
        }

        return product;
    }

    private Seller resolveSeller(User currentUser) {
        return sellerRepository.findByUserId(currentUser.getId())
                .orElseThrow(NotASellerException::new);
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Brand resolveBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new BrandNotFoundException(brandId));
    }

    private void applyImages(Product product, List<ProductImageRequest> imageRequests) {
        product.getImages().clear();

        if (imageRequests == null || imageRequests.isEmpty()) {
            return;
        }

        long primaryCount = imageRequests.stream().filter(ProductImageRequest::isPrimary).count();
        if (primaryCount > 1) {
            throw new MultiplePrimaryImagesException();
        }

        for (ProductImageRequest imageRequest : imageRequests) {
            product.getImages().add(new ProductImage(product, imageRequest.getUrl(), imageRequest.isPrimary()));
        }

        if (primaryCount == 0) {
            product.getImages().get(0).setPrimary(true);
        }
    }

    private ProductResponse toResponse(Product product) {
        List<ProductImageResponse> imageResponses = product.getImages().stream()
                .map(image -> new ProductImageResponse(image.getId(), image.getUrl(), image.isPrimary()))
                .toList();

        Long brandId = product.getBrand() != null ? product.getBrand().getId() : null;

        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getCategory().getId(),
                brandId,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getRatingAvg(),
                imageResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
