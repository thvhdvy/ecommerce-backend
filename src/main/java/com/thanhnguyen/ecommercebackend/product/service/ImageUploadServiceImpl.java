package com.thanhnguyen.ecommercebackend.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.thanhnguyen.ecommercebackend.product.exception.InvalidImageFileException;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageUploadServiceImpl implements ImageUploadService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    private final Cloudinary cloudinary;
    private final SellerService sellerService;

    @Override
    public String upload(User currentUser, MultipartFile file) {
        sellerService.requireActiveSeller(currentUser.getId());
        validateExtension(file);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "products",
                    "resource_type", "image"));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new UncheckedIOException("Cloudinary upload failed", e);
        }
    }

    // Chi kiem tra duoi file theo yeu cau — khong doi trong voi content-type do client tu khai bao
    // (de spoof, khong dang tin cay hon duoi file). Neu can chan chat hon (magic byte sniffing) thi
    // lam sau, khong nam trong yeu cau hien tai.
    private void validateExtension(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageFileException(file.getOriginalFilename());
        }
        String filename = file.getOriginalFilename();
        int dotIndex = filename == null ? -1 : filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new InvalidImageFileException(filename);
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidImageFileException(filename);
        }
    }
}
