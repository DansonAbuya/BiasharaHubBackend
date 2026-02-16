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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final Pattern ORDER_CMD = Pattern.compile("(?i)^order\\s+([a-f0-9-]{36})\\s+(\\d+)\\s*$");
    /** Order by list number: "ORDER 1 2" = product #1, qty 2 */
    private static final Pattern ORDER_INDEX = Pattern.compile("(?i)^order\\s+(\\d+)\\s+(\\d+)\\s*$");
    /** Pay for specific order: "PAY ORD-WA-123" or "PAY #ORD-WA-123" */
    private static final Pattern PAY_ORDER = Pattern.compile("(?i)^pay\\s+#?(\\S+)\\s*$");

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

    @Value("${app.storefront-url:https://biasharahub.com}")
    private String storefrontUrl;

    /** Pending WhatsApp link: phone not in DB, user is providing email then code to link. Expires after 15 min. */
    private static final long LINK_STATE_TTL_SECONDS = 900;
    private final ConcurrentHashMap<String, PendingLinkState> linkStateByPhone = new ConcurrentHashMap<>();

    /** Simple chat stage (for interpreting numeric replies, e.g. shop list). */
    private enum ChatStage { MAIN_MENU, SHOP_LIST }

    private final ConcurrentHashMap<String, ChatStage> stageByPhone = new ConcurrentHashMap<>();
    /** Last product IDs shown to this phone (1-based index maps to list position). Used for "ORDER 1 2". */
    private final ConcurrentHashMap<String, List<UUID>> lastProductIdsByPhone = new ConcurrentHashMap<>();

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");
    private static final Pattern EMAIL_LIKE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Handle incoming WhatsApp message and send reply. Called from webhook.
     * fromWhatsApp is e.g. "whatsapp:+254712345678", body is the message text.
     */
    @Transactional
    public void handleIncomingMessage(String fromWhatsApp, String body) {
        String phone = normalizePhoneFromTwilio(fromWhatsApp);
        if (phone == null || phone.isBlank()) {
            log.warn("WhatsApp webhook: could not normalize From {}", fromWhatsApp);
            return;
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

        // No state: first time or expired – ask for email
        if (state == null) {
            linkStateByPhone.put(phone, new PendingLinkState(PendingLinkState.State.AWAITING_EMAIL, null, null, Instant.now().plusSeconds(LINK_STATE_TTL_SECONDS)));
            return "Hi! We don't have your WhatsApp number on file. Reply with your *account email* to link this number to your BiasharaHub account.";
        }

        if (state.state == PendingLinkState.State.AWAITING_EMAIL) {
            if ("done".equalsIgnoreCase(message.trim())) {
                return "Reply with your account email (e.g. you@example.com) to link this number.";
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
            // Treat "1", "2", ... as STOCK 1, STOCK 2, ...
            String byShop = replyStockByShop(phone, lower);
            // After showing products, go back to main menu context
            stageByPhone.put(phone, ChatStage.MAIN_MENU);
            return byShop;
        }

        // Global numeric shortcuts from main menu: 1–5
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
        // Order: by list number "ORDER 1 2" (product #1, qty 2) or by UUID
        if (lower.startsWith("order ")) {
            var indexMatcher = ORDER_INDEX.matcher(message.trim());
            if (indexMatcher.matches()) {
                int listNum = Integer.parseInt(indexMatcher.group(1));
                int qty = Integer.parseInt(indexMatcher.group(2));
                List<UUID> productIds = lastProductIdsByPhone.get(phone);
                if (productIds != null && listNum >= 1 && listNum <= productIds.size()) {
                    UUID productId = productIds.get(listNum - 1);
                    return createOrderAndReply(customer, productId, qty);
                }
                return "View a product list first: reply 1 for Shops, pick a shop, then reply ORDER <number> <qty> (e.g. ORDER 1 2). Or reply MENU for main menu.";
            }
            if (ORDER_CMD.matcher(message.trim()).matches()) {
                var matcher = ORDER_CMD.matcher(message.trim());
                if (matcher.find()) {
                    try {
                        UUID productId = UUID.fromString(matcher.group(1));
                        int qty = Integer.parseInt(matcher.group(2));
                        return createOrderAndReply(customer, productId, qty);
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
        // Pay: list unpaid orders or pay for specific order
        if (lower.startsWith("pay") || lower.contains("pay now") || lower.contains("payment")) {
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
        sb.append("1. Shops – reply SHOPS to browse by shop\n");
        sb.append("2. Check stock – reply STOCK or STOCK <shop name or number>\n");
        sb.append("3. My orders – reply ORDER\n");
        sb.append("4. Unpaid orders / Pay – reply PAY or UNPAID to see orders to pay, then PAY <order number> (e.g. PAY ORD-WA-123)\n");
        sb.append("5. Delivery status – reply DELIVERY\n\n");
        if (customer == null) {
            sb.append("Register at ").append(storefrontUrl).append(" with your phone to place orders.");
        } else {
            sb.append("Visit ").append(storefrontUrl).append(" to browse and place orders. Reply ORDER to see your orders at any time.");
        }
        sb.append("\n\nReply MENU at any time to return here.");
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
            Order order = createOrderForCustomer(customer, product, qty);
            orderEventPublisher.orderCreated(order);
            return "Order Confirmed! Order #" + order.getOrderNumber() + " – KES " + order.getTotalAmount() + ". Reply PAY "
                    + order.getOrderNumber() + " to pay now.";
        } catch (Exception e) {
            log.warn("WhatsApp order creation failed: {}", e.getMessage());
            return "Could not create order. Please try again or visit " + storefrontUrl;
        }
    }

    private Order createOrderForCustomer(User customer, Product product, int qty) {
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
        Payment payment = Payment.builder()
                .order(order)
                .user(customer)
                .amount(total)
                .paymentStatus("pending")
                .paymentMethod("M-Pesa")
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

    private static final class PendingLinkState {
        enum State { AWAITING_EMAIL, AWAITING_CODE }
        final State state;
        final String email;
        final UUID userId;
        final Instant expiresAt;

        PendingLinkState(State state, String email, UUID userId, Instant expiresAt) {
            this.state = state;
            this.email = email;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
