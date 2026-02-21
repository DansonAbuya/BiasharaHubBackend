# BiasharaHub System Documentation

**Version:** 1.0  
**Date:** February 2025  
**Platform:** BiasharaHub Backend – Multi-tenant SME Commerce & Services  
**© Sysnova Technologies**

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Overview & Architecture](#2-system-overview--architecture)
3. [Technology Stack](#3-technology-stack)
4. [Multi-Tenancy](#4-multi-tenancy)
5. [User Roles & Authentication](#5-user-roles--authentication)
6. [Core Domains](#6-core-domains)
7. [Integrations](#7-integrations)
8. [WhatsApp Chatbot](#8-whatsapp-chatbot)
9. [API Reference Summary](#9-api-reference-summary)
10. [Security](#10-security)
11. [Configuration & Deployment](#11-configuration--deployment)
12. [Database & Migrations](#12-database--migrations)
13. [Generating the PDF](#13-generating-the-pdf)

---

## 1. Executive Summary

BiasharaHub is a **multi-tenant backend** that powers an SME commerce and services platform. It provides:

- **Product commerce**: Shops (owners), products, orders, payments (M-Pesa), shipments, disputes, reviews.
- **Service marketplace**: Service offerings (virtual/physical), appointments, booking payments, escrow, Google Meet integration.
- **Tenant wallet**: Credits, payouts (M-Pesa B2C, bank transfer), reconciliation.
- **WhatsApp assistant**: 24/7 chatbot for customers (browse, order, pay, track) and for sellers/providers (orders, confirm, ship, appointments, services).
- **Admin & verification**: Owner and service-provider verification, reports, analytics, accounting/KRA export.

The backend is **API-only** (context path `/api`). All user-facing UI is in a separate frontend that consumes this API. Authentication is JWT-based; optional Kafka supports async order/payment events.

---

## 2. System Overview & Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Frontend (separate project)                        │
│  Storefront / Dashboard / Admin – calls REST API only                     │
└─────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    BiasharaHub Backend (this repo)                       │
│  Context path: /api   │  JWT auth   │  Schema-per-tenant (PostgreSQL)     │
├─────────────────────────────────────────────────────────────────────────┤
│  Controllers (REST)   │  Services (business logic)   │  Repositories (JPA) │
│  Entities / DTOs      │  Security (JWT filter)      │  Config / Liquibase │
└─────────────────────────────────────────────────────────────────────────┘
        │                │                │                │
        ▼                ▼                ▼                ▼
   M-Pesa Daraja    WhatsApp/Twilio   Postmark/Gmail   Cloudflare R2
   (STK, B2C)       (notifications,   (email)          (images, docs)
                    chatbot webhook)
```

### 2.2 Package Structure (`src/main/java/com/biasharahub`)

| Package       | Purpose |
|---------------|---------|
| **config**    | Security, datasource (tenant-aware), R2, Kafka, OAuth2, OpenAPI, WebMvc, order-event publisher |
| **controller**| REST API controllers |
| **courier**   | Courier provider integration (e.g. manual, REST) |
| **dto**       | `request` and `response` DTOs for API contracts |
| **entity**    | JPA entities (User, Order, Product, ServiceOffering, etc.) |
| **mail**      | Email configuration and senders (Gmail OAuth, Postmark) |
| **repository**| Spring Data JPA repositories |
| **security**  | JWT service, auth filter, `AuthenticatedUser` principal |
| **service**   | Business logic and external integrations |

### 2.3 Request Flow

- Incoming HTTP → **Security filter** (JWT parsed, `AuthenticatedUser` set) → **Controller** → **Service** → **Repository** → DB.
- Public endpoints: `/auth/**`, `/public/**`, `/webhooks/**`, and selected GETs (e.g. products, services, courier-services) do not require a JWT.
- Tenant context: `X-Tenant-ID` header selects the PostgreSQL schema for the request.

---

## 3. Technology Stack

| Layer        | Technology |
|-------------|------------|
| Runtime     | Java 17, Spring Boot 3.2 |
| Database    | PostgreSQL 14+ with schema-per-tenant |
| Migrations  | Liquibase (XML/SQL changelogs) |
| Auth        | JWT (access + refresh), Spring Security, BCrypt |
| API         | REST, JSON; OpenAPI/Swagger available |
| Payments    | M-Pesa Daraja (STK Push, B2C) |
| Messaging   | Optional Kafka; in-process async when Kafka disabled |
| Storage     | Cloudflare R2 (S3-compatible) for images and documents |
| Email       | Postmark (recommended) or Gmail OAuth REST API |
| WhatsApp    | Twilio for sending and receiving messages |

---

## 4. Multi-Tenancy

- **Model:** One PostgreSQL database; **one schema per tenant** (e.g. `tenant_default`).
- **Tenant resolution:** `X-Tenant-ID` header (UUID). Default tenant ID is configurable (e.g. `a1b2c3d4-e5f6-7890-abcd-ef1234567890`).
- **Data isolation:** All tenant-scoped data (users, orders, products, services, etc.) lives in that tenant’s schema. The application uses a **tenant-aware datasource** / context to set the schema per request.
- **Public schema:** Used for `tenants`, `courier_services`, and other platform-wide tables.

---

## 5. User Roles & Authentication

### 5.1 Roles

| Role             | Description | Who can add |
|------------------|-------------|-------------|
| **customer**      | Shops and books services | Self (registration) |
| **owner**        | Business owner (products and/or services) | Platform admin only |
| **staff**        | Owner’s staff | Owner only |
| **super_admin**  | Platform administrator | Seed / manual |
| **assistant_admin** | Platform assistant | super_admin only |

### 5.2 Authentication (Web/API)

- **Registration:** `POST /api/auth/register` (name, email, password). Customers get 2FA by email; login is completed with `POST /api/auth/verify-code`.
- **Login:** `POST /api/auth/login` (email, password). Returns JWT access + refresh tokens, or `requiresTwoFactor` + `stateToken` / `authorizationUrl` when 2FA is enabled.
- **2FA:** Email code and/or Google OAuth2. Only **owner** and **super_admin** can enable/disable 2FA; customers, staff, and assistant_admin always use 2FA.
- **Tokens:** Access token (e.g. 1 hour); refresh via `POST /api/auth/refresh` with `refreshToken`.
- **Security:** `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, validates JWT, and sets `AuthenticatedUser` (userId, email, role) in `SecurityContext`.

### 5.3 WhatsApp “Authentication”

- **Identity:** A user is linked to a WhatsApp number by **email + one-time code** sent to that email. After verification, `User.phone` is set to the WhatsApp number.
- **Authorization:** Every message from that number is resolved via `findUserByPhone(phone)` (owner first, then customer). Seller/provider actions require owner role and business verification (see [§8](#8-whatsapp-chatbot)).

---

## 6. Core Domains

### 6.1 Entities (Summary)

| Entity | Description |
|--------|-------------|
| **User** | Account (email, password hash, name, phone, role, businessId, verificationStatus, serviceProviderStatus, etc.). |
| **Tenant** | Tenant record (schema name, domain, branding). |
| **Product** | Catalog item (name, category, price, quantity, businessId, images). |
| **ProductCategory** | Product category (name, display order). |
| **InventoryImage** | Product image (URL, product, is_main). |
| **Order** | Order (user, orderNumber, totalAmount, orderStatus, deliveryMode, items, payments, shipments). |
| **OrderItem** | Line item (order, product, quantity, priceAtOrder). |
| **Payment** | Order payment (order, amount, method, status, M-Pesa refs). |
| **Shipment** | Delivery (order, deliveryMode, status, tracking, OTP, courier/rider fields). |
| **ServiceCategory** | Service category (e.g. Consulting, Repair). |
| **ServiceOffering** | Service (name, category, price, deliveryType VIRTUAL/PHYSICAL, businessId, meetingLink, etc.). |
| **ServiceAppointment** | Booking (service, user, requestedDate/Time, status, meetingLink). |
| **ServiceBookingPayment** | Payment for a booking (appointment, amount, M-Pesa). |
| **ServiceBookingEscrow** | Escrow for service (release/refund). |
| **Dispute** | Order dispute (order, creator, reason, resolution). |
| **OrderReview** | Single review per order (rating, comment). |
| **Notification** | In-app notification (user, type, title, body, read). |
| **VerificationCode** | Email/2FA code (user, code, expiry). |
| **Expense** | Business expense (category, amount, date) for accounting. |
| **TenantWalletEntry** | Wallet ledger (tenant, type, amount). |
| **TenantPayout** | Payout (method, amount, status, encrypted destination). |
| **CourierService** | Platform courier (name, code, config). |

### 6.2 Product & Order Lifecycle

1. **Products:** Owner/staff create/update products via `POST/PUT /api/products`; images via `POST /api/products/upload-image` (R2).
2. **Order creation:** Customer (or frontend on behalf) calls `POST /api/orders` with items and optional shipping address. Stock is validated and decremented; order status `pending`; pending payment created.
3. **Payment:** `POST /api/orders/{orderId}/payments/initiate` triggers M-Pesa STK Push. Safaricom callback hits `POST /api/payments/mpesa/stk-callback`; backend confirms payment and may publish **payment completed** event.
4. **Events:** On order created and payment completed, `OrderEventPublisher` is used (in-process async or Kafka). Handlers can send notifications, create shipment records, etc.
5. **Order status:** Owner/staff can update via `PATCH /api/orders/{id}/status` (e.g. confirmed, delivered). `PATCH /api/orders/{id}/delivery` sets delivery mode and pickup location; if order is confirmed and no shipment exists, a shipment is created.
6. **Shipments:** Created when order is confirmed (or on payment completion in handlers). Courier/self-delivery/OTP flow via `ShipmentController`; status updates can trigger WhatsApp and in-app notifications.

### 6.3 Service & Appointment Lifecycle

1. **Service offerings:** Owner creates services via `POST /api/services` (VIRTUAL or PHYSICAL).
2. **Appointments:** Customer or API creates appointment (e.g. `POST /api/services/.../appointments`) with requested date/time; status PENDING.
3. **Provider actions:** Confirm/cancel via API (or WhatsApp: CONFIRM APT / CANCEL APT). For VIRTUAL services, Google Meet link can be generated and sent.
4. **Payments:** Service booking payments (M-Pesa STK) and escrow release/refund via `ServiceBookingEscrowService` and related endpoints.

### 6.4 Disputes, Reviews, Reports

- **Disputes:** Created against orders; can be responded to and resolved; may apply strikes.
- **Reviews:** One review per order; business rating aggregated.
- **Reports:** User reports (reporter, target, reason, status); admin resolution.

---

## 7. Integrations

| Integration | Purpose | Where used |
|-------------|---------|------------|
| **M-Pesa Daraja** | STK Push (payments), B2C (payouts) | PaymentController, MpesaCallbackController, PayoutService, ServiceBookingEscrowService, WhatsAppChatbotService |
| **WhatsApp (Twilio)** | Send/receive messages | WhatsAppNotificationService, WhatsAppChatbotService; WhatsAppWebhookController |
| **Email (Postmark / Gmail API)** | 2FA, password reset, verification, transactional | AuthService, VerificationCodeService, MailService, ServiceProviderVerificationService |
| **Cloudflare R2** | File storage (S3-compatible) | Product images, verification documents, service media (R2StorageService) |
| **Google Calendar / Meet** | Service appointment scheduling and video links | GoogleCalendarMeetService, ServiceOfferingController |
| **Kafka (optional)** | Order/payment events | KafkaOrderEventPublisher; OrderEventKafkaListener (example consumer) |
| **SMS (e.g. Africa’s Talking)** | SMS notifications | SmsClient, SmsNotificationService |

---

## 8. WhatsApp Chatbot

### 8.1 Overview

- **Webhook:** Twilio sends incoming messages to `POST /api/webhooks/whatsapp`. The backend normalizes the phone number, resolves the user (or runs link flow), and builds a reply via `WhatsAppChatbotService.handleIncomingMessage` → `buildReply`.

### 8.2 Customer Flows

- **Linking:** If the number is not in the DB, user is asked for email → 6-digit code sent to email → user replies with code → `User.phone` is set and number is linked.
- **Menu:** Shops (SHOPS), stock (STOCK / STOCK &lt;shop&gt;), order (ORDER &lt;n&gt; &lt;qty&gt;), pay (PAY), delivery status (DELIVERY), services (SERVICES), bookings (MY BOOKINGS), pay for booking (PAY SERVICE).
- **Numeric shortcuts:** 1–8 from main menu map to shops, stock, my orders, pay, delivery, services, my bookings, pay service.
- **Service booking:** SERVICES → pick provider → BOOK &lt;n&gt; (optional date/time/location). Location can be shared via WhatsApp or “BOOK 1 at &lt;address&gt;”.
- **Payments:** M-Pesa STK is initiated from the chatbot for orders and service bookings when the user follows PAY / PAY SERVICE flows.

### 8.3 Seller Flows (Owner, Product Seller)

- **Authentication:** Same linking as above; owner must have linked WhatsApp number. Seller commands require verified shop (verificationStatus or sellerTier) and businessId.
- **Commands:** ORDERS (shop orders), CONFIRM &lt;n&gt; (confirm order, create shipment if needed), SHIP &lt;n&gt; (mark shipped, set IN_TRANSIT, notify customer), PRODUCTS (my products), LOW STOCK (products with quantity ≤ 30).

### 8.4 Provider Flows (Owner, Service Provider)

- **Authentication:** Same linking; provider commands require serviceProviderStatus = verified and businessId.
- **Commands:** APPOINTMENTS (my appointments), CONFIRM APT &lt;n&gt;, CANCEL APT &lt;n&gt;, MY SERVICES (list my service offerings).

### 8.5 Menu for Owners

When the user is an owner, the main menu (MENU) includes both customer options (1–8) and a “YOUR SHOP” / “YOUR SERVICES” block with the seller and provider commands above.

---

## 9. API Reference Summary

Base URL: **`/api`**. All endpoints below are relative to this.

### 9.1 Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /auth/register | Register customer (name, email, password) |
| POST | /auth/login | Login (email, password) |
| POST | /auth/refresh | Refresh access token (body: refreshToken) |
| POST | /auth/verify-code | Complete login with 2FA code |
| POST | /auth/change-password | Change password (current, new) |
| POST | /auth/2fa/enable | Enable 2FA (owner/super_admin) |
| POST | /auth/2fa/disable | Disable 2FA (owner/super_admin) |
| GET  | /auth/2fa/oauth/authorize | Start OAuth2 2FA (redirect to Google) |
| GET  | /auth/oauth2/callback | OAuth2 callback (redirect with tokens) |
| POST | /auth/forgot-password | Request password reset |
| POST | /auth/reset-password | Reset password with token |

### 9.2 Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /admin/owners | Add owner (super_admin) |
| POST | /admin/assistant-admins | Add assistant admin (super_admin) |
| POST | /admin/business-owners | Admin business owners (if present) |

### 9.3 Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | /users/me | Current user profile |
| PATCH| /users/me | Update current user |
| GET  | /users/staff | List staff (owner) |
| POST | /users/staff | Add staff (owner) |
| GET  | /users/couriers | List couriers (if applicable) |
| GET  | /users/customers | List customers (if applicable) |

### 9.4 Products

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | /products/categories | List categories (no auth) |
| GET  | /products/businesses | List businesses (no auth) |
| GET  | /products | List products (filters: category, businessId, etc.) |
| GET  | /products/{id} | Get product |
| POST | /products | Create product (owner/staff) |
| PUT  | /products/{id} | Update product (owner/staff) |
| DELETE | /products/{id} | Delete product (owner/staff) |
| POST | /products/upload-image | Upload image to R2 (owner/staff) |

### 9.5 Orders & Payments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | /orders | List orders (customer: own; owner/staff: by business) |
| GET  | /orders/{id} | Get order |
| POST | /orders | Create order (items, optional shipping) |
| PATCH | /orders/{id}/delivery | Set delivery mode, pickup location; create shipment if confirmed |
| PATCH | /orders/{id}/status | Update order status (owner/staff) |
| PATCH | /orders/{id}/cancel | Cancel order (pending only) |
| POST | /orders/{orderId}/payments/initiate | Initiate M-Pesa STK (auth) |
| PATCH | /orders/{orderId}/payments/{paymentId}/confirm | Confirm payment (e.g. after callback) |
| PATCH | /orders/{orderId}/payments/{paymentId}/method | Update payment method |

### 9.6 Shipments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | /shipments | List shipments (auth) |
| GET  | /shipments/order/{orderId} | Shipments for order |
| GET  | /shipments/{id}/tracking | Tracking info |
| POST | /shipments | Create shipment (auth) |
| PATCH | /shipments/{id} | Update shipment (e.g. status, tracking) |
| POST | /shipments/{id}/verify-otp | Verify OTP (delivery confirmation) |
| POST | /shipments/{id}/create-with-provider | Create with courier provider |

### 9.7 Services & Appointments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | /services/can-offer | Check if user can offer services |
| GET  | /services/categories | Service categories |
| GET  | /services/providers | List providers |
| GET  | /services | List service offerings (filters) |
| GET  | /services/{id} | Get service |
| POST | /services | Create service (owner) |
| PUT  | /services/{id} | Update service (owner) |
| DELETE | /services/{id} | Delete service (owner) |
| POST | /services/media/upload | Upload service media (R2) |
| POST | /services/.../appointments | Create appointment (varies by controller design) |
| PATCH | /services/.../appointments | Update appointment (confirm, meeting link, etc.) |
| GET  | /services/.../appointments | List appointments |

### 9.8 Other Controllers

- **Disputes:** POST/GET/PATCH /disputes (create, by order/id, respond, resolve).
- **Reviews:** POST/GET /reviews (create, by order, business rating).
- **Reports:** POST/GET/PATCH /reports (create, open list, resolve).
- **Notifications:** GET /notifications, POST /notifications/{id}/read, read-all.
- **Wallet:** GET /wallet/balance, GET/PUT /wallet/payout-destination.
- **Payouts:** POST /payouts/request, GET /payouts.
- **Expenses:** POST/GET /expenses.
- **Accounting:** GET /accounting/summary, KRA CSV export.
- **Reconciliation:** GET /reconciliation/pending-payments, POST match-receipt.
- **Verification:** POST/GET documents (owner + service provider), GET status/checklist, admin GET/PATCH.
- **Courier:** GET /courier-services (list); GET/PATCH /courier/... for courier portal.
- **Webhooks:** GET/POST /webhooks/whatsapp (Twilio verification + inbound).
- **Public:** GET /public/tenants/{id}/branding.

---

## 10. Security

- **Authentication:** JWT in `Authorization: Bearer <token>`. Stateless; no server-side session.
- **Passwords:** BCrypt via Spring Security `PasswordEncoder`.
- **Sensitive data:** Encrypted fields (e.g. name, phone, payout destination) use `EncryptedStringAttributeConverter` at persistence layer.
- **Authorization:** Role-based; `@PreAuthorize("hasAnyRole('...')")` on admin and scoped endpoints. Controllers check that the principal can access the resource (e.g. order belongs to user or user’s business).
- **CORS:** Configured for frontend origins (localhost, UAT, prod).
- **Webhooks:** M-Pesa and WhatsApp webhooks are unauthenticated (verified by signature/query where applicable).

---

## 11. Configuration & Deployment

### 11.1 Environment Variables (Key)

| Variable | Description |
|----------|-------------|
| DB_URL, DB_USERNAME, DB_PASSWORD | PostgreSQL connection |
| JWT_SECRET (or equivalent) | JWT signing key |
| R2_ENABLED, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_PUBLIC_URL | Cloudflare R2 |
| MPESA_* | Daraja consumer key/secret, shortcode, passkey, STK callback URL |
| TWILIO_* (or WhatsApp config) | Twilio for WhatsApp |
| POSTMARK_SERVER_TOKEN, MAIL_FROM | Postmark email |
| GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, OAUTH2_* | OAuth2 2FA and frontend redirect |
| KAFKA_ENABLED, KAFKA_BOOTSTRAP_SERVERS | Kafka (optional) |
| MESSAGING_IN_PROCESS_ENABLED | In-process order/payment events when Kafka off |
| app.storefront-url | Frontend URL (e.g. for links in WhatsApp/emails) |

### 11.2 Running the Application

- **Local:** `mvn spring-boot:run` (or run from IDE). Default context path: `/api`; port from `server.port` (e.g. 5050).
- **UAT/Prod:** Set environment and deploy JAR; API base URLs as in README (e.g. `https://biasharahub-api-test.sysnovatechnologies.com/api`).

### 11.3 Demo Users (tenant_default)

Password for all: **password123**. Use default tenant or `X-Tenant-ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890`.

| Email | Role |
|-------|------|
| admin@biashara.com | super_admin |
| owner@biashara.com | owner |
| staff@biashara.com | staff |
| customer@biashara.com | customer |

---

## 12. Database & Migrations

- **Liquibase:** Changelogs in `src/main/resources/db/changelog/` (e.g. 001–004 for tenants, schema creation, default tenant, demo users).
- **Schema per tenant:** Each tenant has its own schema (e.g. `tenant_default`). Tables for users, orders, products, services, etc. live in the tenant schema.
- **Public schema:** `tenants`, `courier_services`, and other platform tables.
- Migrations run automatically on application startup.

---

## 13. Generating the PDF

You can generate a PDF from this Markdown document in any of the following ways:

### Option A: Pandoc (recommended)

If you have [Pandoc](https://pandoc.org/) installed:

```bash
cd docs
pandoc BiasharaHub_System_Documentation.md -o BiasharaHub_System_Documentation.pdf --pdf-engine=xelatex -V geometry:margin=1in
```

For a simpler engine (no LaTeX):

```bash
pandoc BiasharaHub_System_Documentation.md -o BiasharaHub_System_Documentation.pdf
```

### Option B: VS Code

1. Install the **Markdown PDF** extension (e.g. "Markdown PDF" by yzane).
2. Open `docs/BiasharaHub_System_Documentation.md`.
3. Right-click in the editor → **Markdown PDF: Export (pdf)**.
4. The PDF will be created in the same folder (or as configured).

### Option C: Online or other tools

- Use an online Markdown-to-PDF converter (paste or upload the `.md` file).
- Or open the `.md` in a viewer that supports “Print to PDF” (e.g. Typora, Obsidian, or browser with a Markdown extension).

---

**End of System Documentation**
