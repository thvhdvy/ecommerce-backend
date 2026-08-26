package com.thanhnguyen.ecommercebackend.cart.service;

import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.cart.dto.CartItemResponse;
import com.thanhnguyen.ecommercebackend.cart.dto.CartResponse;
import com.thanhnguyen.ecommercebackend.cart.dto.UpdateCartItemRequest;
import com.thanhnguyen.ecommercebackend.cart.entity.Cart;
import com.thanhnguyen.ecommercebackend.cart.entity.CartItem;
import com.thanhnguyen.ecommercebackend.cart.exception.CartItemNotFoundException;
import com.thanhnguyen.ecommercebackend.cart.repository.CartItemRepository;
import com.thanhnguyen.ecommercebackend.cart.repository.CartRepository;
import com.thanhnguyen.ecommercebackend.product.dto.ProductResponse;
import com.thanhnguyen.ecommercebackend.product.service.ProductService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    @Override
    @Transactional
    public CartResponse getCart(User currentUser) {
        return cartRepository.findByUserId(currentUser.getId())
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(List.of(), BigDecimal.ZERO));
    }

    @Override
    @Transactional
    public CartResponse addItem(User currentUser, AddCartItemRequest request) {
        ProductResponse product = productService.getActiveById(request.getProductId());

        Cart cart = cartRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> createCart(currentUser));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        if (item != null) {
            item.setQuantity(item.getQuantity() + request.getQuantity());
        } else {
            item = new CartItem(cart, product.getId(), request.getQuantity());
        }
        cartItemRepository.save(item);

        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(User currentUser, Long itemId, UpdateCartItemRequest request) {
        Cart cart = resolveCart(currentUser, itemId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException(itemId));

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(User currentUser, Long itemId) {
        Cart cart = resolveCart(currentUser, itemId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException(itemId));

        cartItemRepository.delete(item);

        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse consumeCart(User currentUser) {
        Cart cart = cartRepository.findByUserIdForUpdate(currentUser.getId()).orElse(null);
        if (cart == null) {
            return new CartResponse(List.of(), BigDecimal.ZERO);
        }

        CartResponse response = toResponse(cart);
        cartItemRepository.deleteAllByCartId(cart.getId());
        return response;
    }

    private Cart resolveCart(User currentUser, Long itemId) {
        return cartRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new CartItemNotFoundException(itemId));
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> responses = new ArrayList<>();

        // Batch load 1 query IN thay vi goi getActiveById() tung item (N+1) — toResponse chay tren
        // moi thao tac cart (get/add/update/remove/consume) nen day la hot path.
        List<CartItem> items = cartItemRepository.findAllByCartId(cart.getId());
        Map<Long, ProductResponse> products = productService.getActiveByIds(
                items.stream().map(CartItem::getProductId).toList());

        for (CartItem item : items) {
            ProductResponse product = products.get(item.getProductId());
            if (product == null) {
                // San pham bi an/xoa khi dang nam trong gio -> loai khoi gio (edge case Flow 2, design doc)
                cartItemRepository.delete(item);
            } else {
                responses.add(toItemResponse(item, product));
            }
        }

        BigDecimal totalAmount = responses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(responses, totalAmount);
    }

    private CartItemResponse toItemResponse(CartItem item, ProductResponse product) {
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                subtotal
        );
    }
}
