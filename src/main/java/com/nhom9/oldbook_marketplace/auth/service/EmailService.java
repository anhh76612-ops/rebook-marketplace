package com.nhom9.oldbook_marketplace.auth.service;

import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String emailNguoiGui;

    public void sendRegisterOtpEmail(
            String emailNguoiNhan,
            String maOtp
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(emailNguoiGui);
        message.setTo(emailNguoiNhan);
        message.setSubject("Mã OTP xác thực tài khoản - OldBook Marketplace");

        message.setText(
                "Xin chào,\n\n"
                        + "Bạn vừa thực hiện đăng ký tài khoản tại OldBook Marketplace.\n\n"
                        + "Mã OTP của bạn là: " + maOtp + "\n\n"
                        + "Mã OTP có hiệu lực trong 5 phút.\n"
                        + "Không chia sẻ mã OTP này với người khác.\n\n"
                        + "Trân trọng,\n"
                        + "OldBook Marketplace"
        );

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Không thể gửi email OTP đến {}", emailNguoiNhan, e);
            throw new BusinessException("Không thể gửi email OTP");
        }
    }
}