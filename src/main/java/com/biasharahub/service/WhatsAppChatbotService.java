package com.biasharahub.service;

import com.biasharahub.entity.*;
import com.biasharahub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WhatsApp Business AI Chatbot: 24/7 assistant per the flow diagram.
 * - Shops: list shops (stores), search by shop name or number.
 * - Inventory check: "Is it in stock?" / "STOCK" / "STOCK &lt;shop&gt;" → Checks product stock (by shop when given).
 * - Order: "Order Confirmed!" / creates order or lists orders.
 * - Payment: "Please Pay Now." → Sends M-Pesa payment request.
 * - Shipment: "Delivery Method Set." / status updates.
 * - Customer updates: Order Shipped, Out for Delivery, Delivered! (sent by WhatsAppNotificationService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppChatbotService {

    private static final int MAX_SHOPS_IN_REPLY = 20;
    private static final int MAX_PRODUCTS_IN_REPLY = 12;
    private static final int MAX_ORDERS_IN_REPLY = 5;
    private static final int MAX_SERVICE_PROVIDERS_IN_REPLY = 15;
    private static final int MAX_SERVICES_IN_REPLY = 10;
    private static final int MAX_BOOKINGS_IN_REPLY = 5;
    private static final Pattern ORDER_CMD = Pattern.compile("(?i)^order\\s+([a-f0-9-]{36})\\s+(\\d+)\\s*$");
    /** Order by list number: "ORDER 1 2" = product #1, qty 2 */
    private static final Pattern ORDER_INDEX = Pattern.compile("(?i)^order\\s+(\\d+)\\s+(\\d+)\\s*$");
    /** Pay for specific order: "PAY ORD-WA-123" or "PAY #ORD-WA-123" */
    private static final Pattern PAY_ORDER = Pattern.compile("(?i)^pay\\s+#?(\\S+)\\s*$");
    /** Book a service by list number: "BOOK 1" or "BOOK 2 2026-02-25" or "BOOK 1 at Westlands" */
    private static final Pattern BOOK_SERVICE = Pattern.compile("(?i)^book\\s+(\\d+)(?:\\s+(\\d{4}-\\d{2}-\\d{2}))?(?:\\s+(\\d{1,2}:\\d{2}))?(?:\\s+at\\s+(.+))?\\s*$");
    /** Book with location only: "BOOK 1 at Westlands Mall" (no date) */
    private static final Pattern BOOK_SERVICE_LOCATION = Pattern.compile("(?i)^book\\s+(\\d+)\\s+at\\s+(.+)$");
    /** Pay for service booking: "PAY SERVICE <booking-id>" or "PAY SERVICE 1" */
    private static final Pattern PAY_SERVICE = Pattern.compile("(?i)^pay\\s+service\\s+#?(\\S+)\\s*$");
    /** Location coordinates pattern for WhatsApp location share */
    private static final Pattern LOCATION_COORDS = Pattern.compile("^(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)$");

    private final WhatsAppClient whatsAppClient;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final MpesaClient mpesaClient;
    private final OrderEventPublisher orderEventPublisher;
    private final VerificationCodeService verificationCodeService;
    private final VerificationCodeRepository verificationCodeRepository;
    private final AuthService authService;
    private final MailService mailService;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceAppointmentRepository serviceAppointmentRepository;
    private final ServiceBookingPaymentRepository serviceBookingPaymentRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;

    @Value("${app.storefront-url:https://biasharahub-app.sysnovatechnologies.com}")
    private String storefrontUrl;

    /** Pending WhatsApp link: phone not in DB, user is providing email then code to link. Expires after 15 min. */
    private static final long LINK_STATE_TTL_SECONDS = 900;
    private final ConcurrentHashMap<String, PendingLinkState> linkStateByPhone = new ConcurrentHashMap<>();

    /** Simple chat stage (for interpreting numeric replies, e.g. shop list, service provider list). */
    private enum ChatStage { MAIN_MENU, SHOP_LIST, SERVICE_PROVIDER_LIST, SERVICE_LIST }

    private final ConcurrentHashMap<String, ChatStage> stageByPhone = new ConcurrentHashMap<>();
    /** Last product IDs shown to this phone (1-based index maps to list position). Used for "ORDER 1 2". */
    private final ConcurrentHashMap<String, List<UUID>> lastProductIdsByPhone = new ConcurrentHashMap<>();
    /** Last service provider IDs (owner user IDs) shown. Used for "SERVICE 1". */
    private final ConcurrentHashMap<String, List<UUID>> lastServiceProviderIdsByPhone = new ConcurrentHashMap<>();
    /** Last service offering IDs shown. Used for "BOOK 1". */
    private final ConcurrentHashMap<String, List<UUID>> lastServiceIdsByPhone = new ConcurrentHashMap<>();
    /** Last service appointment IDs shown. Used for "PAY SERVICE 1". */
    private final ConcurrentHashMap<String, List<UUID>> lastBookingIdsByPhone = new ConcurrentHashMap<>();
    /** Pending customer location for next booking. Stores [lat, lng, description]. */
    private final ConcurrentHashMap<String, CustomerLocation> pendingLocationByPhone = new ConcurrentHashMap<>();

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");
    private static final Pattern EMAIL_LIKE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Customer location data for physical service bookings. */
    private record CustomerLocation(Double lat, Double lng, String description, Instant expiresAt) {
        boolean isExpired() { return expiresAt != null && expiresAt.isBefore(Instant.now()); }
    }

    /**
     * Handle incoming WhatsApp message and send reply. Called from webhook.
     * fromWhatsApp is e.g. "whatsapp:+254712345678", body is the message text.
     */
    @Transactional
    public void handleIncomingMessage(String fromWhatsApp, String body) {
        handleIncomingMessage(fromWhatsApp, body, null, null);
    }

    /**
     * Handle incoming WhatsApp message with optional location data. Called from webhook.
     * latitude/longitude are provided when user shares their location on WhatsApp.
     */
    @Transactional
    public void handleIncomingMessage(String fromWhatsApp, String body, String latitude, String longitude) {
        String phone = normalizePhoneFromTwilio(fromWhatsApp);
        if (phone == null || phone.isBlank()) {
            log.warn("WhatsApp webhook: could not normalize From {}", fromWhatsApp);
            return;
        }

        // Handle location sharing from WhatsApp
        if (latitude != null && longitude != null && !latitude.isBlank() && !longitude.isBlank()) {
            try {
                double lat = Double.parseDouble(latitude);
                double lng = Double.parseDouble(longitude);
                String reply = handleLocationShared(phone, lat, lng);
                whatsAppClient.sendMessage(phone, reply);
                return;
            } catch (NumberFormatException e) {
                log.warn("Invalid location coordinates: {}, {}", latitude, longitude);
            }
        }

        User customer = findCustomerByPhone(phone);
        String message = (body != null) ? body.trim() : "";

        // If number not found, run link flow (email → code → link, or register then link)
        if (customer == null) {
            String linkReply = handleUnknownPhoneLinkFlow(phone, message);
            if (linkReply != null) {
                whatsAppClient.sendMessage(phone, linkReply);
                return;
            }
            // Fall through: still no customer (e.g. first message) – will send generic welcome below
        }
        String reply = buildReply(customer, phone, message);
        if (reply != null && !reply.isBlank()) {
            whatsAppClient.sendMessage(phone, reply);
        }
    }

    /**
     * Handle when user shares their location via WhatsApp.
     * Store the location for use in the next physical service booking.
     */
    private String handleLocationShared(String phone, double lat, double lng) {
        // Store location for 30 minutes
        CustomerLocation location = new CustomerLocation(lat, lng, null, Instant.now().plusSeconds(1800));
        pendingLocationByPhone.put(phone, location);

        ChatStage stage = stageByPhone.getOrDefault(phone, ChatStage.MAIN_MENU);
        if (stage == ChatStage.SERVICE_LIST) {
            List<UUID> serviceIds = lastServiceIdsByPhone.get(phone);
            if (serviceIds != null && !serviceIds.isEmpty()) {
                return "📍 Location received! Your location will be used for your next booking.\n\n"
                        + "Reply BOOK <number> to book a service at this location (e.g. BOOK 1).\n"
                        + "Or add a description: BOOK 1 at " + String.format("%.4f, %.4f", lat, lng);
            }
        }

        return "📍 Location received! Your location has been saved for your next physical service booking.\n\n"
                + "To book a service:\n"
                + "1. Reply SERVICES to browse service providers\n"
                + "2. Pick a provider to see their services\n"
                + "3. Reply BOOK <number> to book\n\n"
                + "Your shared location will automatically be used for physical services.\n"
                + "Reply MENU for main menu.";
    }

    private String normalizePhoneFromTwilio(String from) {
        if (from == null || from.isBlank()) return null;
        String s = from.replace("whatsapp:", "").trim();
        String digits = s.replaceAll("\\D", "");
        if (digits.startsWith("254") && digits.length() >= 12) {
            return "+" + digits;
        }
        if (digits.startsWith("0") && digits.length() >= 9) {
            return "+254" + digits.substring(1);
        }
        if (digits.length() >= 9) {
            return "+254" + digits;
        }
        return s;
    }

    private User findCustomerByPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        // Try E.164 (+254...)
        Optional<User> u = userRepository.findFirstByRoleIgnoreCaseAndPhone("customer", phone);
        if (u.isPresent()) return u.get();
        // Try 0...
        String withZero = digits.length() >= 9 ? "0" + digits.substring(digits.length() - 9) : null;
        if (withZero != null) {
            u = userRepository.findFirstByRoleIgnoreCaseAndPhone("customer", withZero);
            if (u.isPresent()) return u.get();
        }
        // Try without +
        String noPlus = digits.startsWith("254") ? digits : "254" + (digits.length() >= 9 ? digits.substring(digits.length() - 9) : digits);
        u = userRepository.findFirstByRoleIgnoreCaseAndPhone("customer", noPlus);
        return u.orElse(null);
    }

    /**
     * When phone is not linked to any customer: ask for email → send code → verify and link;
     * or direct to register then reply DONE and email again.
     */
    private String handleUnknownPhoneLinkFlow(String phone, String message) {
        PendingLinkState state = linkStateByPhone.get(phone);
        if (state != null && state.expiresAt.isBefore(Instant.now())) {
            linkStateByPhone.remove(phone);
            state = null;
        }

        // No state: first time or expired – offer link or sign up
        if (state == null) {
            String trimmed = message.trim();
            if ("signup".equalsIgnoreCase(trimmed) || "register".equalsIgnoreCase(trimmed)) {
                linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
                return "Create a new account: reply with your *email* (e.g. you@example.com).";
            }
            linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
            return "Hi! We don't have your WhatsApp number on file. Reply with your *account email* to link, or type *SIGNUP* to create a new account.";
        }

        if (state.state == PendingLinkState.State.AWAITING_EMAIL) {
            if ("done".equalsIgnoreCase(message.trim())) {
                return "Reply with your account email (e.g. you@example.com) to link this number.";
            }
            if ("signup".equalsIgnoreCase(message.trim()) || "register".equalsIgnoreCase(message.trim())) {
                linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
                return "Create a new account: reply with your *email* (e.g. you@example.com).";
            }
            if (EMAIL_LIKE.matcher(message).matches()) {
                String email = message.trim().toLowerCase();
                Optional<User> userOpt = userRepository.findByEmail(email).filter(u -> "customer".equalsIgnoreCase(u.getRole()));
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    try {
                        verificationCodeService.createAndSendWhatsAppLinkCode(user);
                    } catch (Exception e) {
                        log.warn("Failed to send WhatsApp link code to {}: {}", email, e.getMessage());
                        return "We couldn't send the code. Please try again in a moment or contact support.";
                    }
                    linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_CODE, email, user.getUserId(), Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
                    return "We sent a 6-digit code to your email. Reply with that code to link this WhatsApp number.";
                }
                String signupUrl = storefrontUrl.endsWith("/") ? storefrontUrl + "signup" : storefrontUrl + "/signup";
                return "No account found with that email. Register at " + signupUrl + ". After signing up, reply *DONE* here to link this number.";
            }
            return "Please reply with your account email (e.g. you@example.com) to link this number.";
        }

        if (state.state == PendingLinkState.State.AWAITING_CODE) {
            if (SIX_DIGITS.matcher(message.trim()).matches()) {
                String code = message.trim();
                User user = state.userId != null ? userRepository.findById(state.userId).orElse(null) : null;
                if (user != null && verificationCodeRepository.findByUserAndVerificationCodeAndExpiresAtAfter(user, code, Instant.now()).isPresent()) {
                    user.setPhone(phone);
                    userRepository.save(user);
                    verificationCodeRepository.deleteByUser(user);
                    linkStateByPhone.remove(phone);
                    return "Number linked! " + buildMenu(user);
                }
            }
            linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
            return "Invalid or expired code. Reply with your *email* to get a new code.";
        }

        // ----- Sign-up flow -----
        if (state.state == PendingLinkState.State.AWAITING_SIGNUP_EMAIL) {
            if (EMAIL_LIKE.matcher(message.trim()).matches()) {
                String email = message.trim().toLowerCase();
                if (userRepository.findByEmail(email).filter(u -> "customer".equalsIgnoreCase(u.getRole())).isPresent()) {
                    return "This email is already registered. Reply with your *account email* to link this number, or type *SIGNUP* with a different email to create a new account.";
                }
                linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_NAME, email, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
                return "Reply with your *full name* to continue.";
            }
            return "Please reply with a valid email (e.g. you@example.com) to create your account.";
        }

        if (state.state == PendingLinkState.State.AWAITING_SIGNUP_NAME) {
            String name = message.trim();
            if (name.length() < 2) {
                return "Please reply with your full name (at least 2 characters).";
            }
            String code = String.valueOf((int) (Math.random() * 900_000) + 100_000);
            Instant codeExpires = Instant.now().plus(10, ChronoUnit.MINUTES);
            try {
                mailService.sendWhatsAppSignupCode(state.email, name, code);
            } catch (Exception e) {
                log.warn("Failed to send WhatsApp signup code to {}: {}", state.email, e.getMessage());
                return "We couldn't send the code. Please try again in a moment or contact support.";
            }
            linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_CODE, state.email, null, state.expiresAt, name, code, codeExpires));
            return "We sent a 6-digit code to your email. Reply with that code here to complete sign-up.";
        }

        if (state.state == PendingLinkState.State.AWAITING_SIGNUP_CODE) {
            if (SIX_DIGITS.matcher(message.trim()).matches()) {
                String code = message.trim();
                if (state.signupCode != null && state.signupCode.equals(code) && state.signupCodeExpiresAt != null && state.signupCodeExpiresAt.isAfter(Instant.now())) {
                    try {
                        User user = authService.registerCustomerViaWhatsApp(state.email, state.name, phone);
                        linkStateByPhone.remove(phone);
                        return "Welcome, " + (user.getName() != null ? user.getName() : "there") + "! You're all set. " + buildMenu(user);
                    } catch (IllegalArgumentException e) {
                        linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
                        return "That email is already registered. Reply with your *email* to link this number, or try another email to sign up.";
                    }
                }
            }
            linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_SIGNUP_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
            return "Invalid or expired code. Reply with your *email* to start sign-up again and get a new code.";
        }

        linkStateByPhone.remove(phone);
        return "Reply with your account email to link this number.";
    }

    private String buildReply(User customer, String phone, String message) {
        if (message.equalsIgnoreCase("menu") || message.equalsIgnoreCase("hi") || message.equalsIgnoreCase("hello") || message.isBlank()) {
            stageByPhone.put(phone, ChatStage.MAIN_MENU);
            return buildMenu(customer);
        }
        if (customer == null) {
            return "We don't have your number on file. Reply with your *account email* to link this WhatsApp number to your BiasharaHub account.";
        }

        String lower = message.toLowerCase().trim();
        ChatStage stage = stageByPhone.getOrDefault(phone, ChatStage.MAIN_MENU);

        // If we are on the shops list screen, numeric replies pick a shop
        if (stage == ChatStage.SHOP_LIST && lower.matches("^\\d+$")) {
            String byShop = replyStockByShop(phone, lower);
            stageByPhone.put(phone, ChatStage.MAIN_MENU);
            return byShop;
        }

        // If on service provider list, numeric replies pick a provider
        if (stage == ChatStage.SERVICE_PROVIDER_LIST && lower.matches("^\\d+$")) {
            String byProvider = replyServicesByProviderNumber(phone, lower);
            stageByPhone.put(phone, ChatStage.SERVICE_LIST);
            return byProvider;
        }

        // If on service list, numeric replies (single digit) default to BOOK
        if (stage == ChatStage.SERVICE_LIST && lower.matches("^\\d+$")) {
            return createServiceBookingAndReply(customer, phone, Integer.parseInt(lower), null, null, null, null, null);
        }

        // Global numeric shortcuts from main menu: 1–9
        if (stage == ChatStage.MAIN_MENU) {
            if (lower.equals("1")) {
                stageByPhone.put(phone, ChatStage.SHOP_LIST);
                return replyShops();
            }
            if (lower.equals("2")) {
                stageByPhone.put(phone, ChatStage.SHOP_LIST);
                return replyStockOrShops("stock");
            }
            if (lower.equals("3")) {
                return replyOrderStatus(customer);
            }
            if (lower.equals("4")) {
                return replyPay(customer, phone);
            }
            if (lower.equals("5")) {
                return replyDeliveryStatus(customer);
            }
            if (lower.equals("6")) {
                stageByPhone.put(phone, ChatStage.SERVICE_PROVIDER_LIST);
                return replyServiceProviders(phone);
            }
            if (lower.equals("7")) {
                return replyMyBookings(customer, phone);
            }
            if (lower.equals("8")) {
                return replyUnpaidServiceBookings(customer, phone);
            }
        }

        // List shops / stores
        if (lower.equals("shops") || lower.equals("stores") || lower.equals("list shops") || lower.contains("browse by shop")) {
            stageByPhone.put(phone, ChatStage.SHOP_LIST);
            return replyShops();
        }
        // Stock by shop: "STOCK 1", "STOCK 2", "SHOP 1", "STOCK ABC Store", "SHOP ABC"
        if (lower.startsWith("stock ") || lower.startsWith("shop ")) {
            String rest = message.substring(5).trim();
            if (!rest.isEmpty()) {
                String byShop = replyStockByShop(phone, rest);
                if (byShop != null) return byShop;
            }
        }
        // Inventory check: "is it in stock?" / "stock" (no shop → show shops first)
        if (lower.contains("stock") || lower.contains("in stock") || lower.contains("availability")) {
            return replyStockOrShops(message);
        }

        // ===== SERVICE PROVIDER COMMANDS =====

        // List service providers: "SERVICES", "PROVIDERS", "SERVICE PROVIDERS"
        if (lower.equals("services") || lower.equals("providers") || lower.equals("service providers") || lower.contains("browse services")) {
            stageByPhone.put(phone, ChatStage.SERVICE_PROVIDER_LIST);
            return replyServiceProviders(phone);
        }

        // Services from specific provider: "SERVICE 1", "PROVIDER 1", "SERVICE ABC Consulting"
        if (lower.startsWith("service ") || lower.startsWith("provider ")) {
            String rest = message.substring(message.indexOf(' ') + 1).trim();
            if (!rest.isEmpty()) {
                String byProvider = replyServicesByProviderNumber(phone, rest);
                if (byProvider != null) {
                    stageByPhone.put(phone, ChatStage.SERVICE_LIST);
                    return byProvider;
                }
            }
        }

        // Location of specific provider: "LOCATION 1", "WHERE 1", "WHERE IS 1"
        if (lower.startsWith("location ") || lower.startsWith("where ") || lower.startsWith("where is ")) {
            String rest = message.replaceFirst("(?i)^(location|where is|where)\\s+", "").trim();
            if (!rest.isEmpty()) {
                return replyProviderLocation(phone, rest);
            }
        }

        // Book a service: "BOOK 1" or "BOOK 1 2026-02-25" or "BOOK 1 2026-02-25 10:00" or "BOOK 1 at Westlands"
        if (lower.startsWith("book ")) {
            // Try location-only pattern first: "BOOK 1 at Westlands Mall"
            var locationMatcher = BOOK_SERVICE_LOCATION.matcher(message.trim());
            if (locationMatcher.matches()) {
                int listNum = Integer.parseInt(locationMatcher.group(1));
                String locationDesc = locationMatcher.group(2).trim();
                return createServiceBookingAndReply(customer, phone, listNum, null, null, null, null, locationDesc);
            }
            // Full pattern: "BOOK 1 2026-02-25 10:00 at Westlands"
            var bookMatcher = BOOK_SERVICE.matcher(message.trim());
            if (bookMatcher.matches()) {
                int listNum = Integer.parseInt(bookMatcher.group(1));
                String dateStr = bookMatcher.group(2);
                String timeStr = bookMatcher.group(3);
                String locationDesc = bookMatcher.group(4) != null ? bookMatcher.group(4).trim() : null;
                return createServiceBookingAndReply(customer, phone, listNum, dateStr, timeStr, null, null, locationDesc);
            }
        }

        // My bookings / appointments
        if (lower.equals("bookings") || lower.equals("my bookings") || lower.equals("appointments") || lower.contains("my appointment")) {
            return replyMyBookings(customer, phone);
        }

        // Pay for service booking: "PAY SERVICE 1" or "PAY SERVICE <booking-id>"
        if (lower.startsWith("pay service ")) {
            var payServiceMatcher = PAY_SERVICE.matcher(message.trim());
            if (payServiceMatcher.matches()) {
                String bookingToken = payServiceMatcher.group(1);
                return replyPayForServiceBooking(customer, phone, bookingToken);
            }
        }

        // Unpaid service bookings
        if (lower.contains("unpaid service") || lower.contains("service to pay")) {
            return replyUnpaidServiceBookings(customer, phone);
        }

        // ===== PRODUCT ORDER COMMANDS =====

        // Order: by list number "ORDER 1 2" (product #1, qty 2) or by UUID
        if (lower.startsWith("order ")) {
            var indexMatcher = ORDER_INDEX.matcher(message.trim());
            if (indexMatcher.matches()) {
                int listNum = Integer.parseInt(indexMatcher.group(1));
                int qty = Integer.parseInt(indexMatcher.group(2));
                List<UUID> productIds = lastProductIdsByPhone.get(phone);
                if (productIds != null && listNum >= 1 && listNum <= productIds.size()) {
                    UUID productId = productIds.get(listNum - 1);
                    boolean payCash = lower.endsWith(" cash");
                    return createOrderAndReply(customer, productId, qty, payCash ? "Cash" : "M-Pesa");
                }
                return "View a product list first: reply 1 for Shops, pick a shop, then reply ORDER <number> <qty> (e.g. ORDER 1 2). Or reply MENU for main menu.";
            }
            if (ORDER_CMD.matcher(message.trim()).matches()) {
                var matcher = ORDER_CMD.matcher(message.trim());
                if (matcher.find()) {
                    try {
                        UUID productId = UUID.fromString(matcher.group(1));
                        int qty = Integer.parseInt(matcher.group(2));
                        boolean payCash = lower.endsWith(" cash");
                        return createOrderAndReply(customer, productId, qty, payCash ? "Cash" : "M-Pesa");
                    } catch (Exception e) {
                        log.warn("WhatsApp order parse failed: {}", e.getMessage());
                    }
                }
            }
        }
        if (lower.equals("order") || lower.contains("my order")) {
            return replyOrderStatus(customer);
        }
        // Unpaid orders: same as PAY – list orders that need payment
        if (lower.contains("unpaid") || lower.contains("orders to pay") || lower.contains("pay for order")) {
            var payMatcher = PAY_ORDER.matcher(message.trim());
            if (payMatcher.matches()) {
                String orderToken = payMatcher.group(1);
                return replyPayForSpecificOrder(customer, phone, orderToken);
            }
            return replyPay(customer, phone);
        }
        // Pay: list unpaid orders or pay for specific order (but not "pay service")
        if ((lower.startsWith("pay") || lower.contains("pay now") || lower.contains("payment")) && !lower.contains("service")) {
            var payMatcher = PAY_ORDER.matcher(message.trim());
            if (payMatcher.matches()) {
                String orderToken = payMatcher.group(1);
                return replyPayForSpecificOrder(customer, phone, orderToken);
            }
            return replyPay(customer, phone);
        }
        // Delivery / shipment status
        if (lower.contains("delivery") || lower.contains("shipment") || lower.contains("track")) {
            return replyDeliveryStatus(customer);
        }
        return buildMenu(customer);
    }

    private String buildMenu(User customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("BiasharaHub 24/7 Assistant\n\n");
        sb.append("*PRODUCTS*\n");
        sb.append("1. Product shops – reply SHOPS to browse shops\n");
        sb.append("2. Check stock – reply STOCK or STOCK <shop>\n");
        sb.append("3. My orders – reply ORDER\n");
        sb.append("4. Pay for order – reply PAY\n");
        sb.append("5. Delivery status – reply DELIVERY\n\n");
        sb.append("*SERVICES*\n");
        sb.append("6. Service providers – reply SERVICES\n");
        sb.append("   📍 View provider locations with LOCATION <number>\n");
        sb.append("7. My bookings – reply BOOKINGS\n");
        sb.append("8. Pay for booking – reply PAY SERVICE\n\n");
        if (customer == null) {
            sb.append("Register at ").append(storefrontUrl).append(" with your phone to order products or book services.");
        } else {
            sb.append("Visit ").append(storefrontUrl).append(" to browse and order. Reply MENU at any time.");
        }
        return sb.toString();
    }

    /** List all shops (verified stores). */
    private String replyShops() {
        List<User> owners = userRepository.findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");
        if (owners.isEmpty()) {
            return "No shops available at the moment. Visit " + storefrontUrl + " to check back later.";
        }
        int limit = Math.min(owners.size(), MAX_SHOPS_IN_REPLY);
        StringBuilder sb = new StringBuilder();
        sb.append("Shops on BiasharaHub:\n\n");
        for (int i = 0; i < limit; i++) {
            User o = owners.get(i);
            String name = o.getBusinessName() != null ? o.getBusinessName() : "Shop " + (i + 1);
            sb.append(i + 1).append(". ").append(name).append("\n");
        }
        if (owners.size() > limit) {
            sb.append("\n... and ").append(owners.size() - limit).append(" more.");
        }
        String firstName = owners.get(0).getBusinessName() != null ? owners.get(0).getBusinessName() : "1";
        sb.append("\n\nReply 1, 2, 3, ... or STOCK 1 / STOCK ")
                .append(firstName)
                .append(" to see products from a shop. Reply STOCK ALL for all products.");
        return sb.toString();
    }

    /** Resolve shop by number (1-based), name, or "all"; return products from that shop or all. */
    private String replyStockByShop(String phone, String shopArg) {
        if (shopArg != null && shopArg.trim().equalsIgnoreCase("all")) {
            return replyStock(phone);
        }
        List<User> owners = userRepository.findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");
        if (owners.isEmpty()) return "No shops available.";

        UUID businessId = null;
        String shopName = null;
        try {
            int num = Integer.parseInt(shopArg.trim());
            if (num >= 1 && num <= owners.size()) {
                User o = owners.get(num - 1);
                businessId = o.getBusinessId();
                shopName = o.getBusinessName();
            }
        } catch (NumberFormatException ignored) {
            // Search by name
            String search = shopArg.trim().toLowerCase();
            for (User o : owners) {
                if (o.getBusinessName() != null && o.getBusinessName().toLowerCase().contains(search)) {
                    businessId = o.getBusinessId();
                    shopName = o.getBusinessName();
                    break;
                }
            }
        }
        if (businessId == null) {
            return "Shop not found. Reply SHOPS to see all shops, or try STOCK <shop name>.";
        }
        return replyStockForShop(phone, businessId, shopName);
    }

    /** Products from one shop. Prices are per item. Stores list for "ORDER 1 2". */
    private String replyStockForShop(String phone, UUID businessId, String shopName) {
        List<Product> products = productRepository.findByBusinessId(businessId);
        if (products.isEmpty()) {
            return (shopName != null ? shopName + ": " : "") + "No products in stock right now. Reply SHOPS to see other shops, or MENU for main menu.";
        }
        int limit = Math.min(products.size(), MAX_PRODUCTS_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append(shopName != null ? shopName : "Shop").append(" – product stock (prices per item):\n\n");
        for (int i = 0; i < limit; i++) {
            Product p = products.get(i);
            ids.add(p.getProductId());
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            String name = p.getName() != null ? p.getName() : "Product";
            sb.append(i + 1).append(". ").append(name)
                    .append(" – ").append(qty).append(" in stock. KES ").append(p.getPrice()).append(" each\n");
        }
        lastProductIdsByPhone.put(phone, ids);
        if (products.size() > limit) {
            sb.append("\n... and ").append(products.size() - limit).append(" more. Visit ").append(storefrontUrl).append(" to see all.");
        }
        sb.append("\n\nReply ORDER <number> <qty> to order (e.g. ORDER 1 2). Reply SHOPS for another shop, MENU for main menu.");
        return sb.toString();
    }

    /** Stock with no shop specified: show shops and prompt to pick one. */
    private String replyStockOrShops(String message) {
        // If they said "stock" or "is it in stock?" with no shop, show shops first so they can search by shop
        return replyShops();
    }

    /** All products (all shops). Prices per item. Stores list for "ORDER 1 2". */
    private String replyStock(String phone) {
        List<Product> products = productRepository.findAllWithImages();
        if (products.isEmpty()) {
            return "We don't have any products in stock right now. Reply SHOPS to see shops, or MENU for main menu, or visit " + storefrontUrl;
        }
        int limit = Math.min(products.size(), MAX_PRODUCTS_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("Product stock (all shops, prices per item):\n\n");
        for (int i = 0; i < limit; i++) {
            Product p = products.get(i);
            ids.add(p.getProductId());
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            String name = p.getName() != null ? p.getName() : "Product";
            sb.append(i + 1).append(". ").append(name)
                    .append(" – ").append(qty).append(" in stock. KES ").append(p.getPrice()).append(" each\n");
        }
        lastProductIdsByPhone.put(phone, ids);
        if (products.size() > limit) {
            sb.append("\n... and ").append(products.size() - limit).append(" more. Visit ").append(storefrontUrl).append(" to see all.");
        }
        sb.append("\n\nReply ORDER <number> <qty> to order (e.g. ORDER 1 2). Reply SHOPS to browse by shop, MENU for main menu.");
        return sb.toString();
    }

    private String replyOrderStatus(User customer) {
        List<Order> orders = orderRepository.findByUserIdOrderByOrderedAtDesc(customer.getUserId());
        if (orders.isEmpty()) {
            return "You have no orders yet. Reply STOCK to see products, SHOPS to browse shops, MENU for main menu, or visit " + storefrontUrl + " to browse and order.";
        }
        int limit = Math.min(orders.size(), MAX_ORDERS_IN_REPLY);
        StringBuilder sb = new StringBuilder();
        sb.append("Your orders:\n\n");
        for (int i = 0; i < limit; i++) {
            Order o = orders.get(i);
            boolean hasUnpaid = paymentRepository.findByOrderAndPaymentStatus(o, "pending").isPresent();
            sb.append("• #").append(o.getOrderNumber()).append(" – ").append(o.getOrderStatus());
            if (hasUnpaid) sb.append(" (unpaid)");
            sb.append(" – KES ").append(o.getTotalAmount()).append("\n");
        }
        sb.append("\nTo pay for an unpaid order: reply PAY to see unpaid orders, then PAY <order number> (e.g. PAY ")
                .append(orders.get(0).getOrderNumber())
                .append("). Reply DELIVERY for shipment status, or MENU for main menu.");
        return sb.toString();
    }

    @Transactional
    public String createOrderAndReply(User customer, UUID productId, int qty) {
        return createOrderAndReply(customer, productId, qty, "M-Pesa");
    }

    @Transactional
    public String createOrderAndReply(User customer, UUID productId, int qty, String paymentMethod) {
        Product product = productRepository.findByProductIdWithImages(productId).orElse(null);
        if (product == null) {
            return "Product not found. Reply STOCK to see available products.";
        }
        int available = product.getQuantity() != null ? product.getQuantity() : 0;
        if (qty <= 0) qty = 1;
        if (available < qty) {
            return "Insufficient stock for " + product.getName() + ". Available: " + available + ". Reply STOCK to see all.";
        }
        try {
            Order order = createOrderForCustomer(customer, product, qty, paymentMethod != null && "Cash".equalsIgnoreCase(paymentMethod) ? "Cash" : "M-Pesa");
            orderEventPublisher.orderCreated(order);
            if ("Cash".equalsIgnoreCase(paymentMethod)) {
                return "Order Confirmed! Order #" + order.getOrderNumber() + " – KES " + order.getTotalAmount()
                        + ". Pay in cash when you receive. The seller will confirm payment in the system. Reply ORDER to see your orders.";
            }
            return "Order Confirmed! Order #" + order.getOrderNumber() + " – KES " + order.getTotalAmount() + ". Reply PAY "
                    + order.getOrderNumber() + " to pay now with M-Pesa.";
        } catch (Exception e) {
            log.warn("WhatsApp order creation failed: {}", e.getMessage());
            return "Could not create order. Please try again or visit " + storefrontUrl;
        }
    }

    private Order createOrderForCustomer(User customer, Product product, int qty) {
        return createOrderForCustomer(customer, product, qty, "M-Pesa");
    }

    private Order createOrderForCustomer(User customer, Product product, int qty, String paymentMethod) {
        String orderNumber = "ORD-WA-" + System.currentTimeMillis();
        BigDecimal price = product.getPrice();
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        Order order = Order.builder()
                .user(customer)
                .orderNumber(orderNumber)
                .totalAmount(total)
                .orderStatus("pending")
                .deliveryMode("SELLER_SELF")
                .shippingFee(BigDecimal.ZERO)
                .build();
        InventoryImage img = product.getImages().isEmpty() ? null : product.getImages().get(0);
        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .inventoryImage(img)
                .quantity(qty)
                .priceAtOrder(price)
                .build();
        order.getItems().add(item);
        order = orderRepository.save(order);
        product.setQuantity(product.getQuantity() - qty);
        productRepository.save(product);
        String method = "Cash".equalsIgnoreCase(paymentMethod) ? "Cash" : "M-Pesa";
        Payment payment = Payment.builder()
                .order(order)
                .user(customer)
                .amount(total)
                .paymentStatus("pending")
                .paymentMethod(method)
                .build();
        paymentRepository.save(payment);
        return order;
    }

    private String replyPay(User customer, String phone) {
        List<Order> orders = orderRepository.findByUserIdOrderByOrderedAtDesc(customer.getUserId());
        List<Order> unpaidOrders = orders.stream()
                .filter(o -> "pending".equalsIgnoreCase(o.getOrderStatus()))
                .filter(o -> paymentRepository.findByOrderAndPaymentStatus(o, "pending").isPresent())
                .collect(Collectors.toList());
        if (unpaidOrders.isEmpty()) {
            return "You have no unpaid orders. Reply ORDER to see your orders, or place one via STOCK/SHOPS or at " + storefrontUrl + ".";
        }
        int limit = Math.min(unpaidOrders.size(), MAX_ORDERS_IN_REPLY);
        StringBuilder sb = new StringBuilder();
        sb.append("Unpaid orders:\n\n");
        for (int i = 0; i < limit; i++) {
            Order o = unpaidOrders.get(i);
            sb.append("• #").append(o.getOrderNumber()).append(" – KES ").append(o.getTotalAmount()).append("\n");
        }
        if (unpaidOrders.size() > limit) {
            sb.append("\n... and ").append(unpaidOrders.size() - limit).append(" more unpaid orders.\n");
        }
        sb.append("\nReply PAY <order number> to pay (e.g. PAY ")
                .append(unpaidOrders.get(0).getOrderNumber())
                .append("). Reply ORDER to see all orders, or MENU for main menu.");
        return sb.toString();
    }

    /** Initiate payment for a specific order number (e.g. PAY ORD-WA-123). */
    private String replyPayForSpecificOrder(User customer, String phone, String orderToken) {
        if (orderToken == null || orderToken.isBlank()) {
            return replyPay(customer, phone);
        }
        String orderNumber = orderToken.startsWith("#") ? orderToken.substring(1) : orderToken;
        Optional<Order> optOrder = orderRepository.findByOrderNumber(orderNumber);
        if (optOrder.isEmpty()) {
            return "We couldn't find order #" + orderNumber + ". Reply ORDER to see your orders, then reply PAY <order number> (e.g. PAY ORD-WA-123...).";
        }
        Order order = optOrder.get();
        if (order.getUser() == null || !order.getUser().getUserId().equals(customer.getUserId())) {
            return "That order does not belong to your account. Reply ORDER to see your own orders.";
        }
        if (!"pending".equalsIgnoreCase(order.getOrderStatus())) {
            return "Order #" + order.getOrderNumber() + " is not pending payment (status: " + order.getOrderStatus() + "). Reply ORDER to see your orders.";
        }
        Payment payment = paymentRepository.findByOrderAndPaymentStatus(order, "pending").orElse(null);
        if (payment == null) {
            return "Order #" + order.getOrderNumber() + " has no pending payment. Reply ORDER for other orders.";
        }
        if ("Cash".equalsIgnoreCase(payment.getPaymentMethod())) {
            return "Order #" + order.getOrderNumber() + " is pay-by-cash. Pay the seller when you receive. They will confirm payment in the system. Reply ORDER to see your orders.";
        }
        String phoneForMpesa = phone.replaceAll("\\D", "");
        if (phoneForMpesa.startsWith("254")) {
            // ok
        } else if (phoneForMpesa.length() >= 9) {
            phoneForMpesa = "254" + (phoneForMpesa.length() == 9 ? phoneForMpesa : phoneForMpesa.substring(phoneForMpesa.length() - 9));
        } else {
            return "We need your M-Pesa number to send the payment request. Please ensure your profile has a valid phone number.";
        }
        String checkoutRequestId = mpesaClient.initiateStkPush(phoneForMpesa, order.getTotalAmount(),
                order.getOrderNumber(), "BiasharaHub order payment");
        payment.setTransactionId(checkoutRequestId);
        paymentRepository.save(payment);
        return "Please Pay Now. Check your phone for the M-Pesa prompt to complete payment for order #" + order.getOrderNumber() + ".";
    }

    private String replyDeliveryStatus(User customer) {
        List<Order> orders = orderRepository.findByUserIdOrderByOrderedAtDesc(customer.getUserId());
        List<String> lines = new ArrayList<>();
        int count = 0;
        for (Order order : orders) {
            if (count >= MAX_ORDERS_IN_REPLY) break;
            List<Shipment> shipments = shipmentRepository.findByOrder(order);
            if (shipments.isEmpty()) {
                if ("pending".equalsIgnoreCase(order.getOrderStatus()) || "confirmed".equalsIgnoreCase(order.getOrderStatus())) {
                    lines.add("Order #" + order.getOrderNumber() + " – Payment confirmed. Delivery will be arranged.");
                    count++;
                }
                continue;
            }
            for (Shipment s : shipments) {
                String status = s.getStatus() != null ? s.getStatus() : "CREATED";
                String line = "Order #" + order.getOrderNumber() + " – " + formatShipmentStatus(status);
                String details = formatShipmentDetails(s);
                if (!details.isEmpty()) line += " (" + details + ")";
                lines.add(line);
                count++;
                if (count >= MAX_ORDERS_IN_REPLY) break;
            }
        }
        if (lines.isEmpty()) {
            return "No delivery updates yet. Reply ORDER to see your orders, or PAY to see pending orders and choose one to pay.";
        }
        return "Delivery status:\n\n" + String.join("\n", lines) + "\n\nWe'll send you updates: Order Shipped → Out for Delivery → Delivered!";
    }

    private static String formatShipmentStatus(String status) {
        if ("CREATED".equalsIgnoreCase(status)) return "Dispatched";
        if ("IN_TRANSIT".equalsIgnoreCase(status) || "OUT_FOR_DELIVERY".equalsIgnoreCase(status) || "SHIPPED".equalsIgnoreCase(status)) return "Out for Delivery";
        if ("DELIVERED".equalsIgnoreCase(status) || "COLLECTED".equalsIgnoreCase(status)) return "Delivered!";
        return status;
    }

    private static String formatShipmentDetails(Shipment s) {
        StringBuilder sb = new StringBuilder();
        if (s.getCourierService() != null && !s.getCourierService().isBlank()) {
            sb.append(s.getCourierService());
            if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) sb.append(" ").append(s.getTrackingNumber());
        }
        if (s.getRiderVehicle() != null && !s.getRiderVehicle().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Reg: ").append(s.getRiderVehicle());
        }
        if (s.getRiderName() != null && !s.getRiderName().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.getRiderName());
        }
        return sb.toString();
    }

    // ==================== SERVICE PROVIDER METHODS ====================

    /** List verified service providers (service_provider_status = verified). */
    private String replyServiceProviders(String phone) {
        List<User> providers = userRepository
                .findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");
        if (providers.isEmpty()) {
            return "No service providers available at the moment. Visit " + storefrontUrl + "/services to check back later, or reply SHOPS to browse product shops.";
        }
        int limit = Math.min(providers.size(), MAX_SERVICE_PROVIDERS_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("Service Providers on BiasharaHub:\n\n");
        for (int i = 0; i < limit; i++) {
            User p = providers.get(i);
            ids.add(p.getUserId());
            String name = p.getBusinessName() != null ? p.getBusinessName() : (p.getName() != null ? p.getName() : "Provider " + (i + 1));
            String deliveryType = p.getServiceDeliveryType();
            String typeLabel = "BOTH".equalsIgnoreCase(deliveryType) ? "Online & In-person" :
                    "PHYSICAL".equalsIgnoreCase(deliveryType) ? "📍 In-person" : "🌐 Online";
            sb.append(i + 1).append(". ").append(name).append(" (").append(typeLabel).append(")");

            // Show location for physical/both delivery types
            if (("PHYSICAL".equalsIgnoreCase(deliveryType) || "BOTH".equalsIgnoreCase(deliveryType))) {
                if (p.getServiceLocationDescription() != null && !p.getServiceLocationDescription().isBlank()) {
                    // Truncate long descriptions
                    String locDesc = p.getServiceLocationDescription();
                    if (locDesc.length() > 40) locDesc = locDesc.substring(0, 37) + "...";
                    sb.append("\n   📍 ").append(locDesc);
                } else if (p.getServiceLocationLat() != null && p.getServiceLocationLng() != null) {
                    sb.append("\n   📍 Location available");
                }
            }
            sb.append("\n");
        }
        lastServiceProviderIdsByPhone.put(phone, ids);
        if (providers.size() > limit) {
            sb.append("\n... and ").append(providers.size() - limit).append(" more.");
        }
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("• Reply 1, 2, 3... to see services\n");
        sb.append("• Reply LOCATION <number> for provider location (e.g. LOCATION 1)\n");
        sb.append("• Visit ").append(storefrontUrl).append("/services for map search");
        return sb.toString();
    }

    /** Format online delivery methods for WhatsApp display. */
    private String formatOnlineDeliveryMethods(String methods) {
        if (methods == null || methods.isBlank()) return "Online";
        String[] parts = methods.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String m = parts[i].trim().toUpperCase();
            String label = switch (m) {
                case "VIDEO_CALL" -> "Video Call";
                case "PHONE_CALL" -> "Phone Call";
                case "WHATSAPP" -> "WhatsApp";
                case "LIVE_CHAT" -> "Live Chat";
                case "EMAIL" -> "Email";
                case "SCREEN_SHARE" -> "Screen Share";
                case "FILE_DELIVERY" -> "File Delivery";
                case "RECORDED_CONTENT" -> "Recorded Content";
                case "SOCIAL_MEDIA" -> "Social Media";
                default -> m.replace("_", " ");
            };
            if (i > 0) sb.append(", ");
            sb.append(label);
        }
        return sb.toString();
    }

    /** Get provider location by number or name. */
    private String replyProviderLocation(String phone, String providerArg) {
        List<UUID> providerIds = lastServiceProviderIdsByPhone.get(phone);
        User provider = null;

        try {
            int num = Integer.parseInt(providerArg.trim());
            if (providerIds != null && num >= 1 && num <= providerIds.size()) {
                UUID providerId = providerIds.get(num - 1);
                provider = userRepository.findById(providerId).orElse(null);
            }
        } catch (NumberFormatException ignored) {
            // Search by name
            List<User> providers = userRepository
                    .findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");
            String search = providerArg.trim().toLowerCase();
            for (User p : providers) {
                String name = p.getBusinessName() != null ? p.getBusinessName() : p.getName();
                if (name != null && name.toLowerCase().contains(search)) {
                    provider = p;
                    break;
                }
            }
        }

        if (provider == null) {
            return "Provider not found. Reply SERVICES to see all service providers.";
        }

        String name = provider.getBusinessName() != null ? provider.getBusinessName() : provider.getName();
        String deliveryType = provider.getServiceDeliveryType();

        StringBuilder sb = new StringBuilder();
        sb.append("📍 *").append(name).append(" - Location*\n\n");

        if ("ONLINE".equalsIgnoreCase(deliveryType)) {
            sb.append("🌐 This provider offers *online/remote services only*.\n\n");
            sb.append("*Services can be delivered via:*\n");
            sb.append("📹 Video Call (Zoom, Google Meet, etc.)\n");
            sb.append("📞 Phone Call\n");
            sb.append("💬 WhatsApp chat/call\n");
            sb.append("📧 Email consultations\n");
            sb.append("🖥️ Screen sharing sessions\n");
            sb.append("📁 Digital file delivery\n");
            sb.append("🎬 Recorded content/tutorials\n\n");
            sb.append("Each service specifies its delivery method.\n");
            sb.append("Reply SERVICE ").append(providerArg).append(" to see their services and book.");
            return sb.toString();
        }

        boolean hasLocation = false;

        if (provider.getServiceLocationDescription() != null && !provider.getServiceLocationDescription().isBlank()) {
            sb.append("📌 *Address:*\n").append(provider.getServiceLocationDescription()).append("\n\n");
            hasLocation = true;
        }

        if (provider.getServiceLocationLat() != null && provider.getServiceLocationLng() != null) {
            sb.append("🗺️ *View on Google Maps:*\n");
            sb.append("https://www.google.com/maps?q=")
                    .append(provider.getServiceLocationLat()).append(",").append(provider.getServiceLocationLng()).append("\n\n");
            hasLocation = true;
        }

        if (!hasLocation) {
            sb.append("Location details not available for this provider.\n");
            sb.append("Contact them directly for service location.\n\n");
        }

        if (provider.getPhone() != null && !provider.getPhone().isBlank()) {
            sb.append("📞 *Contact:* ").append(provider.getPhone()).append("\n\n");
        }

        String typeLabel = "BOTH".equalsIgnoreCase(deliveryType) ? "Online & In-person" : "In-person only";
        sb.append("🏷️ *Service type:* ").append(typeLabel).append("\n\n");

        sb.append("Reply SERVICE ").append(providerArg).append(" to see their services and book.");
        return sb.toString();
    }

    /** List services from a provider by number or name. */
    private String replyServicesByProviderNumber(String phone, String providerArg) {
        List<UUID> providerIds = lastServiceProviderIdsByPhone.get(phone);
        UUID providerId = null;
        String providerName = null;

        try {
            int num = Integer.parseInt(providerArg.trim());
            if (providerIds != null && num >= 1 && num <= providerIds.size()) {
                providerId = providerIds.get(num - 1);
            }
        } catch (NumberFormatException ignored) {
            // Search by name
            List<User> providers = userRepository
                    .findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");
            String search = providerArg.trim().toLowerCase();
            for (User p : providers) {
                String name = p.getBusinessName() != null ? p.getBusinessName() : p.getName();
                if (name != null && name.toLowerCase().contains(search)) {
                    providerId = p.getUserId();
                    providerName = name;
                    break;
                }
            }
        }

        if (providerId == null) {
            return "Provider not found. Reply SERVICES to see all service providers, or try SERVICE <name>.";
        }

        User provider = userRepository.findById(providerId).orElse(null);
        if (provider == null || provider.getBusinessId() == null) {
            return "Provider not found. Reply SERVICES to see all.";
        }

        if (providerName == null) {
            providerName = provider.getBusinessName() != null ? provider.getBusinessName() : provider.getName();
        }

        return replyServicesForProvider(phone, provider.getBusinessId(), providerName, provider);
    }

    /** List services from a specific provider/business. */
    private String replyServicesForProvider(String phone, UUID businessId, String providerName, User provider) {
        Set<UUID> businessIds = Set.of(businessId);
        List<ServiceOffering> services = serviceOfferingRepository.findByBusinessIdIn(businessIds, false);
        if (services.isEmpty()) {
            return (providerName != null ? providerName + ": " : "") + "No services available. Reply SERVICES for other providers, or MENU for main menu.";
        }

        // Determine if provider offers physical services
        boolean hasPhysicalServices = services.stream()
                .anyMatch(s -> "PHYSICAL".equalsIgnoreCase(s.getDeliveryType()) || "BOTH".equalsIgnoreCase(s.getDeliveryType()));
        String providerDeliveryType = provider != null ? provider.getServiceDeliveryType() : null;
        boolean isPhysicalProvider = "PHYSICAL".equalsIgnoreCase(providerDeliveryType) || "BOTH".equalsIgnoreCase(providerDeliveryType);

        int limit = Math.min(services.size(), MAX_SERVICES_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📋 ").append(providerName != null ? providerName : "Services").append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // Show provider location prominently for physical services
        if (isPhysicalProvider || hasPhysicalServices) {
            if (provider != null && provider.getServiceLocationDescription() != null && !provider.getServiceLocationDescription().isBlank()) {
                sb.append("📍 *Provider Location:*\n");
                sb.append(provider.getServiceLocationDescription()).append("\n");
                if (provider.getServiceLocationLat() != null && provider.getServiceLocationLng() != null) {
                    sb.append("🗺️ View on map: https://www.google.com/maps?q=")
                            .append(provider.getServiceLocationLat()).append(",").append(provider.getServiceLocationLng()).append("\n");
                }
                sb.append("\n");
            } else if (provider != null && provider.getServiceLocationLat() != null && provider.getServiceLocationLng() != null) {
                sb.append("📍 *Provider Location:*\n");
                sb.append("🗺️ https://www.google.com/maps?q=")
                        .append(provider.getServiceLocationLat()).append(",").append(provider.getServiceLocationLng()).append("\n\n");
            }
        }

        // Show contact info if available
        if (provider != null && provider.getPhone() != null && !provider.getPhone().isBlank()) {
            sb.append("📞 Contact: ").append(provider.getPhone()).append("\n\n");
        }

        sb.append("*Available Services:*\n\n");
        for (int i = 0; i < limit; i++) {
            ServiceOffering s = services.get(i);
            ids.add(s.getServiceId());
            String deliveryType = s.getDeliveryType();
            String typeLabel = "PHYSICAL".equalsIgnoreCase(deliveryType) ? "📍 In-person" : "🌐 Online";
            String duration = s.getDurationMinutes() != null ? " (~" + s.getDurationMinutes() + "min)" : "";
            sb.append(i + 1).append(". ").append(s.getName()).append("\n");
            sb.append("   💰 KES ").append(s.getPrice()).append(" | ").append(typeLabel).append(duration).append("\n");
            if (s.getDescription() != null && !s.getDescription().isBlank()) {
                String desc = s.getDescription().length() > 55 ? s.getDescription().substring(0, 52) + "..." : s.getDescription();
                sb.append("   ").append(desc).append("\n");
            }
            // Show online delivery methods for virtual services
            if ("VIRTUAL".equalsIgnoreCase(deliveryType) && s.getOnlineDeliveryMethods() != null && !s.getOnlineDeliveryMethods().isBlank()) {
                sb.append("   ✨ Via: ").append(formatOnlineDeliveryMethods(s.getOnlineDeliveryMethods())).append("\n");
            }
        }
        lastServiceIdsByPhone.put(phone, ids);
        if (services.size() > limit) {
            sb.append("\n... and ").append(services.size() - limit).append(" more.");
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📝 *To book:*\n");
        sb.append("• Reply BOOK <number> (e.g. BOOK 1)\n");
        sb.append("• With date: BOOK 1 2026-02-25\n");
        if (hasPhysicalServices) {
            sb.append("• For in-person: Share your location or type BOOK 1 at <your address>\n");
        }
        sb.append("\nReply SERVICES for other providers.");
        return sb.toString();
    }

    /** Create a service booking for the customer (legacy overload). */
    @Transactional
    public String createServiceBookingAndReply(User customer, String phone, int listNum, String dateStr, String timeStr) {
        return createServiceBookingAndReply(customer, phone, listNum, dateStr, timeStr, null, null, null);
    }

    /** Create a service booking for the customer with optional location. */
    @Transactional
    public String createServiceBookingAndReply(User customer, String phone, int listNum, String dateStr, String timeStr,
                                                Double locationLat, Double locationLng, String locationDescription) {
        List<UUID> serviceIds = lastServiceIdsByPhone.get(phone);
        if (serviceIds == null || listNum < 1 || listNum > serviceIds.size()) {
            return "View a service list first: reply SERVICES to browse providers, pick one, then reply BOOK <number> (e.g. BOOK 1). Or reply MENU for main menu.";
        }
        UUID serviceId = serviceIds.get(listNum - 1);
        ServiceOffering service = serviceOfferingRepository.findByServiceIdWithCategory(serviceId).orElse(null);
        if (service == null || service.getIsActive() == null || !service.getIsActive()) {
            return "Service not available. Reply SERVICES to browse other services.";
        }

        LocalDate requestedDate;
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                requestedDate = LocalDate.parse(dateStr);
                if (requestedDate.isBefore(LocalDate.now())) {
                    return "Date must be today or in the future. Reply BOOK " + listNum + " <date> (e.g. BOOK " + listNum + " 2026-02-25).";
                }
            } catch (Exception e) {
                return "Invalid date format. Use YYYY-MM-DD (e.g. BOOK " + listNum + " 2026-02-25).";
            }
        } else {
            requestedDate = LocalDate.now().plusDays(1); // Default to tomorrow
        }

        LocalTime requestedTime = null;
        if (timeStr != null && !timeStr.isBlank()) {
            try {
                requestedTime = LocalTime.parse(timeStr);
            } catch (Exception e) {
                // Ignore invalid time
            }
        }

        // Check if location is needed for physical services
        boolean isPhysical = "PHYSICAL".equalsIgnoreCase(service.getDeliveryType()) || "BOTH".equalsIgnoreCase(service.getDeliveryType());

        // Use provided location or check for pending shared location
        Double finalLat = locationLat;
        Double finalLng = locationLng;
        String finalLocationDesc = locationDescription;

        if (isPhysical && (finalLat == null || finalLng == null)) {
            CustomerLocation pending = pendingLocationByPhone.get(phone);
            if (pending != null && !pending.isExpired()) {
                finalLat = pending.lat();
                finalLng = pending.lng();
                if (finalLocationDesc == null && pending.description() != null) {
                    finalLocationDesc = pending.description();
                }
                // Clear the pending location after use
                pendingLocationByPhone.remove(phone);
            }
        }

        // For physical services, prompt for location if not provided
        if (isPhysical && finalLat == null && finalLng == null && (finalLocationDesc == null || finalLocationDesc.isBlank())) {
            return "📍 This is an in-person service. Please provide your location:\n\n"
                    + "Option 1: Share your location using WhatsApp's location feature (📎 → Location)\n\n"
                    + "Option 2: Type your address:\n"
                    + "BOOK " + listNum + " at <your address>\n"
                    + "(e.g. BOOK " + listNum + " at Westlands Mall, Nairobi)\n\n"
                    + "Option 3: Book with date and location:\n"
                    + "BOOK " + listNum + " 2026-02-25 at <your address>";
        }

        try {
            ServiceAppointment appointment = ServiceAppointment.builder()
                    .service(service)
                    .user(customer)
                    .requestedDate(requestedDate)
                    .requestedTime(requestedTime)
                    .status("PENDING")
                    .notes("Booked via WhatsApp")
                    .customerLocationLat(finalLat)
                    .customerLocationLng(finalLng)
                    .customerLocationDescription(finalLocationDesc)
                    .build();
            appointment = serviceAppointmentRepository.save(appointment);

            ServiceBookingPayment payment = ServiceBookingPayment.builder()
                    .appointment(appointment)
                    .user(customer)
                    .amount(service.getPrice())
                    .paymentStatus("pending")
                    .paymentMethod("M-Pesa")
                    .build();
            serviceBookingPaymentRepository.save(payment);

            // Send notifications
            try {
                inAppNotificationService.notifyServiceBookingCreated(appointment);
                inAppNotificationService.notifyProviderServiceBookingCreated(appointment);
                whatsAppNotificationService.notifyProviderServiceBookingCreated(appointment);
            } catch (Exception ex) {
                log.warn("Failed to send service booking notifications: {}", ex.getMessage());
            }

            String dateDisplay = requestedDate.toString();
            String timeDisplay = requestedTime != null ? " at " + requestedTime : "";
            String deliveryInfo = isPhysical ? " (In-person)" : " (Online – meeting link will be sent)";

            StringBuilder response = new StringBuilder();
            response.append("✅ Booking Confirmed!\n\n");
            response.append("\"").append(service.getName()).append("\"\n");
            response.append("📅 ").append(dateDisplay).append(timeDisplay).append("\n");
            response.append("💰 KES ").append(service.getPrice()).append("\n");
            response.append(deliveryInfo).append("\n");

            if (isPhysical && finalLocationDesc != null && !finalLocationDesc.isBlank()) {
                response.append("📍 Location: ").append(finalLocationDesc).append("\n");
            } else if (isPhysical && finalLat != null && finalLng != null) {
                response.append("📍 Location: ").append(String.format("%.4f, %.4f", finalLat, finalLng)).append("\n");
            }

            response.append("\nReply PAY SERVICE ").append(appointment.getAppointmentId()).append(" to pay now with M-Pesa.\n");
            response.append("Or reply BOOKINGS to see your bookings.");

            return response.toString();
        } catch (Exception e) {
            log.warn("WhatsApp service booking failed: {}", e.getMessage());
            return "Could not book service. Please try again or visit " + storefrontUrl + "/services";
        }
    }

    /** List customer's service bookings/appointments. */
    private String replyMyBookings(User customer, String phone) {
        List<ServiceAppointment> bookings = serviceAppointmentRepository.findByUserIdOrderByRequestedDateDesc(customer.getUserId());
        if (bookings.isEmpty()) {
            return "You have no service bookings yet. Reply SERVICES to browse service providers, or ORDER to see your product orders.";
        }
        int limit = Math.min(bookings.size(), MAX_BOOKINGS_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("Your service bookings:\n\n");
        for (int i = 0; i < limit; i++) {
            ServiceAppointment a = bookings.get(i);
            ids.add(a.getAppointmentId());
            String serviceName = a.getService() != null ? a.getService().getName() : "Service";
            String dateStr = a.getRequestedDate() != null ? a.getRequestedDate().toString() : "";
            String timeStr = a.getRequestedTime() != null ? " at " + a.getRequestedTime() : "";
            String status = a.getStatus() != null ? a.getStatus() : "PENDING";
            BigDecimal price = a.getService() != null ? a.getService().getPrice() : BigDecimal.ZERO;

            // Check payment status
            boolean unpaid = serviceBookingPaymentRepository.findByAppointmentAndPaymentStatus(a, "pending").isPresent();

            sb.append(i + 1).append(". ").append(serviceName).append(" – ").append(dateStr).append(timeStr).append("\n");
            sb.append("   Status: ").append(status);
            if (unpaid) sb.append(" (unpaid – KES ").append(price).append(")");
            sb.append("\n");
        }
        lastBookingIdsByPhone.put(phone, ids);
        if (bookings.size() > limit) {
            sb.append("\n... and ").append(bookings.size() - limit).append(" more.");
        }
        sb.append("\n\nReply PAY SERVICE <number> to pay (e.g. PAY SERVICE 1).\n");
        sb.append("Reply SERVICES to browse more, MENU for main menu.");
        return sb.toString();
    }

    /** List unpaid service bookings. */
    private String replyUnpaidServiceBookings(User customer, String phone) {
        List<ServiceAppointment> bookings = serviceAppointmentRepository.findByUserIdOrderByRequestedDateDesc(customer.getUserId());
        List<ServiceAppointment> unpaidBookings = bookings.stream()
                .filter(a -> "PENDING".equalsIgnoreCase(a.getStatus()) || "CONFIRMED".equalsIgnoreCase(a.getStatus()))
                .filter(a -> serviceBookingPaymentRepository.findByAppointmentAndPaymentStatus(a, "pending").isPresent())
                .collect(Collectors.toList());

        if (unpaidBookings.isEmpty()) {
            return "You have no unpaid service bookings. Reply BOOKINGS to see all bookings, or SERVICES to browse and book services.";
        }
        int limit = Math.min(unpaidBookings.size(), MAX_BOOKINGS_IN_REPLY);
        List<UUID> ids = new ArrayList<>(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("Unpaid service bookings:\n\n");
        for (int i = 0; i < limit; i++) {
            ServiceAppointment a = unpaidBookings.get(i);
            ids.add(a.getAppointmentId());
            String serviceName = a.getService() != null ? a.getService().getName() : "Service";
            BigDecimal price = a.getService() != null ? a.getService().getPrice() : BigDecimal.ZERO;
            sb.append(i + 1).append(". ").append(serviceName).append(" – KES ").append(price).append("\n");
        }
        lastBookingIdsByPhone.put(phone, ids);
        if (unpaidBookings.size() > limit) {
            sb.append("\n... and ").append(unpaidBookings.size() - limit).append(" more.");
        }
        sb.append("\n\nReply PAY SERVICE <number> to pay (e.g. PAY SERVICE 1).\n");
        sb.append("Reply BOOKINGS for all bookings, MENU for main menu.");
        return sb.toString();
    }

    /** Initiate payment for a service booking. */
    private String replyPayForServiceBooking(User customer, String phone, String bookingToken) {
        if (bookingToken == null || bookingToken.isBlank()) {
            return replyUnpaidServiceBookings(customer, phone);
        }

        UUID appointmentId = null;

        // Try by list number first
        try {
            int num = Integer.parseInt(bookingToken.trim());
            List<UUID> bookingIds = lastBookingIdsByPhone.get(phone);
            if (bookingIds != null && num >= 1 && num <= bookingIds.size()) {
                appointmentId = bookingIds.get(num - 1);
            }
        } catch (NumberFormatException ignored) {
            // Try by UUID
            try {
                appointmentId = UUID.fromString(bookingToken.trim());
            } catch (Exception e) {
                return "Invalid booking reference. Reply BOOKINGS to see your bookings, then PAY SERVICE <number>.";
            }
        }

        if (appointmentId == null) {
            return "Booking not found. Reply BOOKINGS to see your bookings.";
        }

        Optional<ServiceAppointment> optAppt = serviceAppointmentRepository.findByAppointmentIdWithDetails(appointmentId);
        if (optAppt.isEmpty()) {
            return "Booking not found. Reply BOOKINGS to see your bookings.";
        }

        ServiceAppointment appointment = optAppt.get();
        if (appointment.getUser() == null || !appointment.getUser().getUserId().equals(customer.getUserId())) {
            return "That booking does not belong to your account. Reply BOOKINGS to see your bookings.";
        }

        ServiceBookingPayment payment = serviceBookingPaymentRepository.findByAppointmentAndPaymentStatus(appointment, "pending").orElse(null);
        if (payment == null) {
            return "Booking \"" + (appointment.getService() != null ? appointment.getService().getName() : "Service")
                    + "\" has no pending payment. Reply BOOKINGS for your bookings.";
        }

        String phoneForMpesa = phone.replaceAll("\\D", "");
        if (phoneForMpesa.startsWith("254")) {
            // ok
        } else if (phoneForMpesa.length() >= 9) {
            phoneForMpesa = "254" + (phoneForMpesa.length() == 9 ? phoneForMpesa : phoneForMpesa.substring(phoneForMpesa.length() - 9));
        } else {
            return "We need your M-Pesa number to send the payment request. Please ensure your profile has a valid phone number.";
        }

        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "Service";
        String checkoutRequestId = mpesaClient.initiateStkPush(phoneForMpesa, payment.getAmount(),
                "SVC-" + appointment.getAppointmentId(), "BiasharaHub service: " + serviceName);
        payment.setTransactionId(checkoutRequestId);
        serviceBookingPaymentRepository.save(payment);

        return "Please Pay Now. Check your phone for the M-Pesa prompt to complete payment for \"" + serviceName + "\" (KES " + payment.getAmount() + ").";
    }

    private static final class PendingLinkState {
        enum State { AWAITING_EMAIL, AWAITING_CODE, AWAITING_SIGNUP_EMAIL, AWAITING_SIGNUP_NAME, AWAITING_SIGNUP_CODE }
        final State state;
        final String email;
        final UUID userId;
        final Instant expiresAt;
        /** For sign-up flow: display name */
        final String name;
        /** For sign-up flow: 6-digit code sent to email */
        final String signupCode;
        /** For sign-up flow: code expiry */
        final Instant signupCodeExpiresAt;

        PendingLinkState(State state, String email, UUID userId, Instant expiresAt) {
            this(state, email, userId, expiresAt, null, null, null);
        }

        PendingLinkState(State state, String email, UUID userId, Instant expiresAt, String name, String signupCode, Instant signupCodeExpiresAt) {
            this.state = state;
            this.email = email;
            this.userId = userId;
            this.expiresAt = expiresAt;
            this.name = name;
            this.signupCode = signupCode;
            this.signupCodeExpiresAt = signupCodeExpiresAt;
        }
    }
}
