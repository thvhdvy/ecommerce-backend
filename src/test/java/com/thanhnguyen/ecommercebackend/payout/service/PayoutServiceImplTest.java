package com.thanhnguyen.ecommercebackend.payout.service;

import com.thanhnguyen.ecommercebackend.order.dto.OrderItemPayoutInfo;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payout.dto.PayoutResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.SellerBalanceResponse;
import com.thanhnguyen.ecommercebackend.payout.entity.LedgerEntryType;
import com.thanhnguyen.ecommercebackend.payout.entity.SellerBalance;
import com.thanhnguyen.ecommercebackend.payout.entity.SellerLedgerEntry;
import com.thanhnguyen.ecommercebackend.payout.entity.SellerPayout;
import com.thanhnguyen.ecommercebackend.payout.exception.PayoutNotAllowedException;
import com.thanhnguyen.ecommercebackend.payout.repository.SellerBalanceRepository;
import com.thanhnguyen.ecommercebackend.payout.repository.SellerLedgerEntryRepository;
import com.thanhnguyen.ecommercebackend.payout.repository.SellerPayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock
    private SellerLedgerEntryRepository ledgerEntryRepository;

    @Mock
    private SellerBalanceRepository sellerBalanceRepository;

    @Mock
    private SellerPayoutRepository sellerPayoutRepository;

    @Mock
    private OrderService orderService;

    private PayoutServiceImpl payoutService;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutServiceImpl(
                ledgerEntryRepository, sellerBalanceRepository, sellerPayoutRepository, orderService,
                new BigDecimal("0.10"));
    }

    @Test
    void recordEarning_shouldCreateOneLedgerEntryPerSeller_andAddNetAmountToBalance() {
        when(ledgerEntryRepository.existsByOrderIdAndType(100L, LedgerEntryType.EARNED)).thenReturn(false);
        when(orderService.getOrderItemsForPayout(100L)).thenReturn(List.of(
                new OrderItemPayoutInfo(5L, new BigDecimal("100.00"), 2), // seller 5: gross 200
                new OrderItemPayoutInfo(5L, new BigDecimal("50.00"), 1),  // seller 5: gross 50 -> total 250
                new OrderItemPayoutInfo(7L, new BigDecimal("30.00"), 1))); // seller 7: gross 30

        payoutService.recordEarning(100L);

        ArgumentCaptor<SellerLedgerEntry> captor = ArgumentCaptor.forClass(SellerLedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture());

        SellerLedgerEntry seller5Entry = captor.getAllValues().stream()
                .filter(e -> e.getSellerId().equals(5L)).findFirst().orElseThrow();
        assertThat(seller5Entry.getType()).isEqualTo(LedgerEntryType.EARNED);
        assertThat(seller5Entry.getOrderId()).isEqualTo(100L);
        assertThat(seller5Entry.getGrossAmount()).isEqualByComparingTo("250.00");
        assertThat(seller5Entry.getCommissionAmount()).isEqualByComparingTo("25.00");
        assertThat(seller5Entry.getNetAmount()).isEqualByComparingTo("225.00");

        SellerLedgerEntry seller7Entry = captor.getAllValues().stream()
                .filter(e -> e.getSellerId().equals(7L)).findFirst().orElseThrow();
        assertThat(seller7Entry.getGrossAmount()).isEqualByComparingTo("30.00");
        assertThat(seller7Entry.getCommissionAmount()).isEqualByComparingTo("3.00");
        assertThat(seller7Entry.getNetAmount()).isEqualByComparingTo("27.00");

        verify(sellerBalanceRepository).addToBalance(5L, new BigDecimal("225.00"));
        verify(sellerBalanceRepository).addToBalance(7L, new BigDecimal("27.00"));
    }

    @Test
    void recordEarning_shouldSkip_whenOrderAlreadyHasEarnedEntry() {
        when(ledgerEntryRepository.existsByOrderIdAndType(100L, LedgerEntryType.EARNED)).thenReturn(true);

        payoutService.recordEarning(100L);

        verify(orderService, never()).getOrderItemsForPayout(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(sellerBalanceRepository, never()).addToBalance(anyLong(), any());
    }

    @Test
    void recordAdjustment_shouldSaveNegativeLedgerEntry_andSubtractFromBalance() {
        payoutService.recordAdjustment(200L, 5L, new BigDecimal("40.00"));

        ArgumentCaptor<SellerLedgerEntry> captor = ArgumentCaptor.forClass(SellerLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        SellerLedgerEntry entry = captor.getValue();
        assertThat(entry.getType()).isEqualTo(LedgerEntryType.ADJUSTED);
        assertThat(entry.getSellerId()).isEqualTo(5L);
        assertThat(entry.getReturnRequestId()).isEqualTo(200L);
        assertThat(entry.getNetAmount()).isEqualByComparingTo("-40.00");

        verify(sellerBalanceRepository).addToBalance(5L, new BigDecimal("-40.00"));
    }

    @Test
    void payOut_shouldZeroOutBalance_andCreatePayoutRecord_whenBalancePositive() {
        SellerBalance balance = new SellerBalance(5L);
        balance.setBalance(new BigDecimal("150.00"));
        when(sellerBalanceRepository.findBySellerId(5L)).thenReturn(Optional.of(balance));
        when(sellerBalanceRepository.saveAndFlush(any())).thenReturn(balance);
        when(sellerPayoutRepository.save(any())).thenAnswer(inv -> {
            SellerPayout p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PayoutResponse response = payoutService.payOut(5L);

        assertThat(response.getAmount()).isEqualByComparingTo("150.00");
        assertThat(response.getSellerId()).isEqualTo(5L);
        assertThat(balance.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void payOut_shouldThrow_whenBalanceIsZeroOrNegative() {
        SellerBalance balance = new SellerBalance(5L);
        balance.setBalance(new BigDecimal("-10.00"));
        when(sellerBalanceRepository.findBySellerId(5L)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> payoutService.payOut(5L)).isInstanceOf(PayoutNotAllowedException.class);
        verify(sellerPayoutRepository, never()).save(any());
    }

    @Test
    void payOut_shouldThrow_whenSellerHasNoBalanceRow() {
        when(sellerBalanceRepository.findBySellerId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payoutService.payOut(5L)).isInstanceOf(PayoutNotAllowedException.class);
    }

    @Test
    void payOut_shouldThrow_whenConcurrentModificationDetected() {
        SellerBalance balance = new SellerBalance(5L);
        balance.setBalance(new BigDecimal("150.00"));
        when(sellerBalanceRepository.findBySellerId(5L)).thenReturn(Optional.of(balance));
        when(sellerBalanceRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(SellerBalance.class, 5L));

        assertThatThrownBy(() -> payoutService.payOut(5L)).isInstanceOf(PayoutNotAllowedException.class);
        verify(sellerPayoutRepository, never()).save(any());
    }

    @Test
    void getBalance_shouldReturnZero_whenSellerHasNoBalanceRowYet() {
        when(sellerBalanceRepository.findBySellerId(9L)).thenReturn(Optional.empty());

        SellerBalanceResponse response = payoutService.getBalance(9L);

        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
