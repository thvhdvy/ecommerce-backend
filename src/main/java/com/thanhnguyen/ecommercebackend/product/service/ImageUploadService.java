package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface ImageUploadService {

    /** Upload anh len Cloudinary, tra ve secure_url de dung lam ProductImageRequest.url. */
    String upload(User currentUser, MultipartFile file);
}
