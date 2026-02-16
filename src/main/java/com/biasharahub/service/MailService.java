package com.biasharahub.service;

import com.biasharahub.mail.EmailMessage;
import com.biasharahub.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Production-ready email service: 2FA codes and transactional mail via Gmail OAuth (or fallback).
 * Uses multi-tenant EmailSender; tenant context is set by TenantFilter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final EmailSender emailSender;

    @Value("${app.mail.from:no-reply@biasharahub.local}")
    private String defaultFrom;

    /**
     * Send 2FA verification code to the user. Uses Gmail OAuth when configured.
     */
    public void sendTwoFactorCode(@NonNull String toEmail, @NonNull String code) {
        String text = """
                Hi,

                Your BiasharaHub verification code is: %s

                This code will expire in 10 minutes. If you did not try to sign in, you can safely ignore this email.
                """.formatted(code);
        EmailMessage message = EmailMessage.builder()
                .to(List.of(toEmail))
                .from(defaultFrom)
                .subject("Your BiasharaHub login code")
                .textBody(text)
                .build();
        sendSafe(message, "2FA code", toEmail);
    }

    /**
     * Send WhatsApp link verification code. Used when a user links their WhatsApp number to an existing account.
     */
    public void sendWhatsAppLinkCode(@NonNull String toEmail, @NonNull String code) {
        String text = """
                Hi,

                Your BiasharaHub WhatsApp link code is: %s

                Enter this code in the WhatsApp chat to link your number. This code expires in 10 minutes.
                If you did not request this, you can safely ignore this email.
                """.formatted(code);
        send(toEmail, "Your BiasharaHub WhatsApp link code", text);
    }

    /**
     * Send a generic email (e.g. password reset, notifications). Tenant from context.
     */
    public void send(@NonNull String toEmail, @NonNull String subject, @NonNull String textBody) {
        EmailMessage message = EmailMessage.builder()
                .to(List.of(toEmail))
                .from(defaultFrom)
                .subject(subject)
                .textBody(textBody)
                .build();
        sendSafe(message, subject, toEmail);
    }

    /**
     * Welcome email for a new owner (added by platform admin). Includes business name and temporary password.
     */
    public void sendWelcomeOwner(@NonNull String toEmail, @NonNull String name, String businessName, @NonNull String temporaryPassword) {
        String businessLine = (businessName != null && !businessName.isBlank())
                ? "Your business \"" + businessName + "\" has been set up on BiasharaHub."
                : "You have been added as a business owner on BiasharaHub.";
        String text = """
                Hi %s,

                %s

                Your temporary password is: %s

                Please log in and change your password, and enable two-factor authentication (2FA) in Settings for security.

                If you did not expect this email, please contact support.
                """.formatted(name, businessLine, temporaryPassword);
        send(toEmail, "Welcome to BiasharaHub – Owner account", text);
    }

    /**
     * Welcome email for a new staff member (added by owner). Includes business name and temporary password.
     */
    public void sendWelcomeStaff(@NonNull String toEmail, @NonNull String name, String businessName, @NonNull String temporaryPassword) {
        String businessLine = (businessName != null && !businessName.isBlank())
                ? "You have been added as staff for \"" + businessName + "\" on BiasharaHub."
                : "You have been added as staff on BiasharaHub.";
        String text = """
                Hi %s,

                %s

                Your temporary password is: %s

                On your first login you will receive a verification code by email; enter it to complete sign-in. After that, you can log in with just your email and password. We recommend changing your password after first login.

                If you did not expect this email, please contact your business owner.
                """.formatted(name, businessLine, temporaryPassword);
        send(toEmail, "Welcome to BiasharaHub – Staff account", text);
    }

    /**
     * Welcome email for a new courier (added by owner). Includes business name and temporary password.
     */
    public void sendWelcomeCourier(@NonNull String toEmail, @NonNull String name, String businessName, @NonNull String temporaryPassword) {
        String businessLine = (businessName != null && !businessName.isBlank())
                ? "You have been added as a courier for \"" + businessName + "\" on BiasharaHub."
                : "You have been added as a courier on BiasharaHub.";
        String text = """
                Hi %s,

                %s

                Your temporary password is: %s

                Log in to the BiasharaHub app and use the Courier Portal to view and update deliveries assigned to you.
                We recommend changing your password after first login.

                If you did not expect this email, please contact your business owner.
                """.formatted(name, businessLine, temporaryPassword);
        send(toEmail, "Welcome to BiasharaHub – Courier account", text);
    }

    /**
     * Welcome email for a new assistant admin (added by platform admin). Includes temporary password.
     * 2FA is always on for assistant admins.
     */
    public void sendWelcomeAssistantAdmin(@NonNull String toEmail, @NonNull String name, @NonNull String temporaryPassword) {
        String text = """
                Hi %s,

                You have been added as an assistant administrator on BiasharaHub.

                Your temporary password is: %s

                Please log in and change your password. Two-factor authentication (2FA) is always enabled for your account.

                If you did not expect this email, please contact the platform administrator.
                """.formatted(name, temporaryPassword);
        send(toEmail, "Welcome to BiasharaHub – Assistant admin account", text);
    }

    /**
     * Password reset email. Sends a link with token to the frontend reset-password page.
     */
    public void sendPasswordReset(@NonNull String toEmail, @NonNull String name, @NonNull String resetLink) {
        String text = """
                Hi %s,

                You requested a password reset for your BiasharaHub account.

                Click the link below to set a new password (valid for 1 hour):

                %s

                If you did not request this, you can safely ignore this email. Your password will not be changed.
                """.formatted(name, resetLink);
        send(toEmail, "Reset your BiasharaHub password", text);
    }

    /**
     * Welcome email for a new customer (self-registration) that includes the verification code.
     * They verify once using this code; afterwards they log in with email + password only.
     */
    public void sendWelcomeCustomerWithCode(@NonNull String toEmail, @NonNull String name, @NonNull String code) {
        String text = """
                Hi %s,

                Welcome to BiasharaHub! Your customer account has been created.

                Your verification code is: %s

                Enter this code in the app to verify your account and complete sign-in. You will only need to do this once.
                After verification, you can log in using your email and password without any additional codes.

                If you did not create this account, please contact support.
                """.formatted(name, code);
        send(toEmail, "Welcome to BiasharaHub – verify your account", text);
    }

    private void sendSafe(EmailMessage message, String description, String toEmail) {
        try {
            emailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send {} to {}: {}", description, toEmail, e.getMessage());
        }
    }
}
