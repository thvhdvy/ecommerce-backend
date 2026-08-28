package com.thanhnguyen.ecommercebackend.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.thanhnguyen.ecommercebackend.product.exception.InvalidImageFileException;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.exception.NotASellerException;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceImplTest {

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    @Mock
    private SellerService sellerService;

    private ImageUploadServiceImpl imageUploadService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        imageUploadService = new ImageUploadServiceImpl(cloudinary, sellerService);
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        return user;
    }

    @Test
    void upload_shouldReturnSecureUrl_whenExtensionAllowed() throws IOException {
        when(sellerService.requireActiveSeller(USER_ID)).thenReturn(new Seller());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/products/abc.jpg"));
        MockMultipartFile file = new MockMultipartFile("file", "shirt.jpg", "image/jpeg", "fake-bytes".getBytes());

        String url = imageUploadService.upload(user(), file);

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/products/abc.jpg");
    }

    @Test
    void upload_shouldThrow_whenExtensionNotAllowed() {
        when(sellerService.requireActiveSeller(USER_ID)).thenReturn(new Seller());
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> imageUploadService.upload(user(), file))
                .isInstanceOf(InvalidImageFileException.class);
        verify(cloudinary, never()).uploader();
    }

    @Test
    void upload_shouldThrow_whenFileHasNoExtension() {
        when(sellerService.requireActiveSeller(USER_ID)).thenReturn(new Seller());
        MockMultipartFile file = new MockMultipartFile("file", "noext", "image/jpeg", "x".getBytes());

        assertThatThrownBy(() -> imageUploadService.upload(user(), file))
                .isInstanceOf(InvalidImageFileException.class);
    }

    @Test
    void upload_shouldThrow_whenFileEmpty() {
        when(sellerService.requireActiveSeller(USER_ID)).thenReturn(new Seller());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> imageUploadService.upload(user(), file))
                .isInstanceOf(InvalidImageFileException.class);
        verify(cloudinary, never()).uploader();
    }

    @Test
    void upload_shouldPropagate_whenCurrentUserNotActiveSeller() {
        // Kiem tra seller identity truoc, khong doc file/goi Cloudinary neu currentUser khong phai
        // active seller — tranh lang phi upload request cho 1 request se bi tu choi.
        when(sellerService.requireActiveSeller(USER_ID)).thenThrow(new NotASellerException());
        MockMultipartFile file = new MockMultipartFile("file", "shirt.jpg", "image/jpeg", "x".getBytes());

        assertThatThrownBy(() -> imageUploadService.upload(user(), file))
                .isInstanceOf(NotASellerException.class);
        verify(cloudinary, never()).uploader();
    }

    @Test
    void upload_shouldWrapIOException_asUncheckedIOException() throws IOException {
        when(sellerService.requireActiveSeller(USER_ID)).thenReturn(new Seller());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("network down"));
        MockMultipartFile file = new MockMultipartFile("file", "shirt.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> imageUploadService.upload(user(), file))
                .isInstanceOf(UncheckedIOException.class);
    }
}
