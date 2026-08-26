package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductResponse;
import com.thanhnguyen.ecommercebackend.product.dto.ProductSearchCriteria;
import com.thanhnguyen.ecommercebackend.product.dto.ProductStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public interface ProductService {
    ProductResponse create(User currentUser, ProductCreateRequest request);

    ProductResponse update(User currentUser, Long productId, ProductUpdateRequest request);

    ProductResponse updateStatus(User currentUser, Long productId, ProductStatusUpdateRequest request);

    InventoryResponse updateInventory(User currentUser, Long productId, UpdateInventoryRequest request);

    ProductResponse getActiveById(Long productId);

    /**
     * Batch variant của getActiveById — load nhiều sản phẩm ACTIVE bằng 1 query IN (chống N+1,
     * dùng bởi Cart module khi map giỏ hàng). Không throw khi thiếu id: id không ACTIVE/không tồn tại
     * chỉ vắng mặt trong Map trả về, caller tự quyết cách xử lý (VD: loại item mồ côi khỏi giỏ).
     */
    Map<Long, ProductResponse> getActiveByIds(Collection<Long> productIds);

    /** Public search: chỉ trả sản phẩm ACTIVE, filter động qua JPA Specification + pagination/sort. */
    PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable);

    /**
     * Đồng bộ cờ inStock (denormalized từ Inventory.quantityAvailable > 0) — gọi bởi InventoryService
     * sau mỗi lần thay đổi quantityAvailable (reserve/release/update), không phải hành động của Product module.
     */
    void updateStockFlag(Long productId, boolean inStock);

    /**
     * Lookup Product entity theo id (moi status) cho module can giu FK that toi Product
     * (VD: Review.product) — throw ProductNotFoundException neu khong ton tai.
     */
    Product getEntityById(Long productId);

    /**
     * Ghi de rating_avg — goi boi Review module sau khi tao/an/hien review. Product module khoa
     * row product TRUOC roi moi goi ratingAvgSupplier (Review tinh AVG tu bang reviews cua no):
     * neu 2 review cung product duoc tao gan dong thoi, request thua phai cho lock, AVG doc lai
     * se thay ca 2 review thay vi ghi de nhau. Supplier de moi module giu dung ownership du lieu
     * cua minh (lock thuoc Product, AVG thuoc Review) ma van dam bao thu tu lock-truoc-doc-sau.
     */
    void recalculateRating(Long productId, Supplier<BigDecimal> ratingAvgSupplier);
}
