package com.thanhnguyen.ecommercebackend.cart.exception;

// Item ton tai nhung khong thuoc cart cua user hien tai -> 403 (design doc Flow 6: "User co truy cap
// tai nguyen khong thuoc quyen -> 403") — thong nhat voi OrderOwnershipException/ProductOwnershipException.
// Truoc day case nay tra 404 CART_ITEM_NOT_FOUND, lech convention voi Product/Order.
public class CartOwnershipException extends RuntimeException {
    public CartOwnershipException() {
        super("Cart item does not belong to current user");
    }
}
