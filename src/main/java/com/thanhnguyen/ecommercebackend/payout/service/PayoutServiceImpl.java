package com.thanhnguyen.ecommercebackend.payout.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemPayoutInfo;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payout.dto.LedgerEntryResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayoutServiceImpl implements PayoutService {

    private final SellerLedgerEntryRepository ledgerEntryRepository;
    private final SellerBalanceRepository sellerBalanceRepository;
    private final SellerPayoutRepository sellerPayoutRepository;
    private final OrderService orderService;
    private final BigDecimal commissionRate;

    // @Lazy: pha vong lap khoi tao bean — OrderMaintenanceProcessor (order module) goi
    // PayoutService.recordEarning(), con PayoutServiceImpl lai can goi nguoc lai OrderService de
    // doc breakdown order_item (cung ly do voi @Lazy PaymentService trong OrderServiceImpl).
    public PayoutServiceImpl(
            SellerLedgerEntryRepository ledgerEntryRepository,
            SellerBalanceRepository sellerBalanceRepository,
            SellerPayoutRepository sellerPayoutRepository,
            @Lazy OrderService orderService,
            @Value("${payout.commission-rate:0.10}") BigDecimal commissionRate) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.sellerBalanceRepository = sellerBalanceRepository;
        this.sellerPayoutRepository = sellerPayoutRepository;
        this.orderService = orderService;
        this.commissionRate = commissionRate;
    }

    @Override
    @Transactional
    public void recordEarning(Long orderId) {
        if (ledgerEntryRepository.existsByOrderIdAndType(orderId, LedgerEntryType.EARNED)) {
            return; // idempotent guard - order nay da duoc ghi nhan roi (design doc v2 muc 9.3)
        }

        List<OrderItemPayoutInfo> items = orderService.getOrderItemsForPayout(orderId);
        Map<Long, BigDecimal> grossBySeller = new HashMap<>();
        for (OrderItemPayoutInfo item : items) {
            BigDecimal itemGross = item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
            grossBySeller.merge(item.getSellerId(), itemGross, BigDecimal::add);
        }

        for (Map.Entry<Long, BigDecimal> entry : grossBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            BigDecimal gross = entry.getValue();
            BigDecimal commission = gross.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(commission);

            ledgerEntryRepository.save(SellerLedgerEntry.earned(sellerId, orderId, gross, commission, net));
            sellerBalanceRepository.addToBalance(sellerId, net);
        }
    }

    @Override
    @Transactional
    public void recordAdjustment(Long returnRequestId, Long sellerId, BigDecimal amount) {
        BigDecimal netAmount = amount.negate();
        ledgerEntryRepository.save(SellerLedgerEntry.adjusted(sellerId, returnRequestId, netAmount));
        sellerBalanceRepository.addToBalance(sellerId, netAmount);
    }

    @Override
    @Transactional
    public PayoutResponse payOut(Long sellerId) {
        SellerBalance balance = sellerBalanceRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new PayoutNotAllowedException("Seller has no balance to pay out"));

        if (balance.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PayoutNotAllowedException("Seller balance must be positive to pay out, current: "
                    + balance.getBalance());
        }

        BigDecimal amount = balance.getBalance();
        balance.setBalance(BigDecimal.ZERO);
        try {
            sellerBalanceRepository.saveAndFlush(balance);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new PayoutNotAllowedException("Seller balance was just modified, please retry");
        }

        SellerPayout payout = sellerPayoutRepository.save(new SellerPayout(sellerId, amount));
        return toPayoutResponse(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerBalanceResponse getBalance(Long sellerId) {
        BigDecimal balance = sellerBalanceRepository.findBySellerId(sellerId)
                .map(SellerBalance::getBalance)
                .orElse(BigDecimal.ZERO);
        return new SellerBalanceResponse(sellerId, balance);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> listLedger(Long sellerId, Pageable pageable) {
        return PageResponse.from(ledgerEntryRepository
                .findAllBySellerIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(this::toLedgerResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutResponse> listPayoutsForSeller(Long sellerId, Pageable pageable) {
        return PageResponse.from(sellerPayoutRepository
                .findAllBySellerIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(this::toPayoutResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutResponse> listAllPayouts(Long sellerId, Pageable pageable) {
        if (sellerId != null) {
            return listPayoutsForSeller(sellerId, pageable);
        }
        return PageResponse.from(sellerPayoutRepository.findAll(pageable).map(this::toPayoutResponse));
    }

    private LedgerEntryResponse toLedgerResponse(SellerLedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(), e.getSellerId(), e.getOrderId(), e.getReturnRequestId(), e.getType(),
                e.getGrossAmount(), e.getCommissionAmount(), e.getNetAmount(), e.getCreatedAt());
    }

    private PayoutResponse toPayoutResponse(SellerPayout p) {
        return new PayoutResponse(p.getId(), p.getSellerId(), p.getAmount(), p.getCreatedAt());
    }
}
