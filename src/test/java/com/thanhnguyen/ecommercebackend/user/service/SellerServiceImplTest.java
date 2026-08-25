package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.exception.NotEligibleForSellerException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.user.repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerServiceImplTest {

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private SellerServiceImpl sellerService;

    private User customer() {
        User user = new User();
        user.setId(1L);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }

    @Test
    void becomeSeller_shouldCreateSeller_whenCustomerHasNoSellerYet() {
        User user = customer();
        BecomeSellerRequest request = new BecomeSellerRequest("My Store", "Selling stuff");

        when(sellerRepository.existsByUserId(1L)).thenReturn(false);
        when(sellerRepository.save(any(Seller.class))).thenAnswer(invocation -> {
            Seller s = invocation.getArgument(0);
            s.setId(10L);
            s.setCreatedAt(java.time.LocalDateTime.now());
            return s;
        });

        SellerResponse response = sellerService.becomeSeller(user, request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStoreName()).isEqualTo("My Store");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    void becomeSeller_shouldThrow_whenUserIsNotCustomer() {
        User admin = customer();
        admin.setRole(UserRole.ADMIN);
        BecomeSellerRequest request = new BecomeSellerRequest("My Store", null);

        assertThatThrownBy(() -> sellerService.becomeSeller(admin, request))
                .isInstanceOf(NotEligibleForSellerException.class);
    }

    @Test
    void becomeSeller_shouldThrow_whenSellerAlreadyExists() {
        User user = customer();
        BecomeSellerRequest request = new BecomeSellerRequest("My Store", null);

        when(sellerRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> sellerService.becomeSeller(user, request))
                .isInstanceOf(SellerAlreadyExistsException.class);
    }
}
