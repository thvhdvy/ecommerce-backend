package com.thanhnguyen.ecommercebackend.cart.service;

import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.cart.dto.CartResponse;
import com.thanhnguyen.ecommercebackend.cart.dto.UpdateCartItemRequest;
import com.thanhnguyen.ecommercebackend.user.entity.User;

public interface CartService {
    CartResponse getCart(User currentUser);

    CartResponse addItem(User currentUser, AddCartItemRequest request);

    CartResponse updateItem(User currentUser, Long itemId, UpdateCartItemRequest request);

    CartResponse removeItem(User currentUser, Long itemId);

    CartResponse consumeCart(User currentUser);
}
