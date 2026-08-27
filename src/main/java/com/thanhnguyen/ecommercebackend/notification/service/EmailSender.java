package com.thanhnguyen.ecommercebackend.notification.service;

/**
 * Boc JavaMailSender phia sau 1 interface — cung ly do voi VnpayClient boc HTTP call toi VNPay:
 * de spy/mock duoc trong test ma khong can SMTP server that (design doc muc 8.1 dung Mailhog cho dev).
 */
public interface EmailSender {
    void send(String toEmail, String subject, String body);
}
