package com.biasharahub.service;

import com.biasharahub.entity.User;
import com.biasharahub.entity.VerificationCode;
import com.biasharahub.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Saves 2FA verification codes and sends them by email.
 * Runs in the caller's transaction so new users (on registration) are visible
 * to the verification_codes foreign key constraints.
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final MailService mailService;

    @Transactional
    public void createAndSend2FACode(User user) {
        verificationCodeRepository.deleteByUser(user);
        String code = generateVerificationCode();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        VerificationCode verificationCode = VerificationCode.builder()
                .user(user)
                .verificationCode(code)
                .expiresAt(expiresAt)
                .build();
        verificationCodeRepository.save(verificationCode);
        mailService.sendTwoFactorCode(user.getEmail(), code);
    }

    /**
     * Registration-only: send welcome + verification code in a single email.
     * Used for customer sign-up; after successful verification, customer logins no longer require codes.
     */
    @Transactional
    public void createAndSendWelcomeVerification(User user) {
        verificationCodeRepository.deleteByUser(user);
        String code = generateVerificationCode();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        VerificationCode verificationCode = VerificationCode.builder()
                .user(user)
                .verificationCode(code)
                .expiresAt(expiresAt)
                .build();
        verificationCodeRepository.save(verificationCode);
        mailService.sendWelcomeCustomerWithCode(user.getEmail(), user.getName(), code);
    }

    private static String generateVerificationCode() {
        int code = (int) (Math.random() * 900_000) + 100_000;
        return Integer.toString(code);
    }
}
