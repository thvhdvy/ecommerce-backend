package com.thanhnguyen.ecommercebackend.common;

// Chuyen tu product.exception len common: exception API-level chung cho moi endpoint co pagination
// (khong rieng gi product) — dung boi PageRequests.capped().
public class InvalidSortFieldException extends RuntimeException {
    public InvalidSortFieldException(String field) {
        super("Cannot sort by field: " + field);
    }
}
