# Messaging: Kafka, in-process async, and RabbitMQ (optional)

## Without Kafka or RabbitMQ: in-process async

You can get **similar behaviour** (non-blocking, decoupled handling after order/payment) with **no message broker**:

### 1. In-process async (implemented)

- **What it is**: When an order is created or a payment is completed, a handler runs on a **separate thread** (`@Async`). The API returns immediately; the handler can log, send email, or trigger other work.
- **Enable**: Set `MESSAGING_IN_PROCESS_ENABLED=true` (and leave `KAFKA_ENABLED=false`).
- **Where**: `InProcessOrderEventPublisher` calls `InProcessOrderEventHandlers.onOrderCreated` / `onPaymentCompleted`. Add your logic there (e.g. call `MailService`, create shipment record).
- **Pros**: No extra infrastructure, simple, same JVM.  
- **Cons**: If the app restarts before the handler runs, that run is lost; no replay or separate consumers.

### 2. Database as queue (transactional outbox)

- **What it is**: In the same DB transaction as the order (or payment), insert a row into an `outbox` or `event_log` table. A **scheduled job** or background thread polls the table and processes rows (send email, create shipment), then marks them done or deletes them.
- **Pros**: Durable, at-least-once, no broker; only the existing DB.
- **Cons**: You add a table and a job; ordering and scaling need a bit of care.

### 3. Scheduled polling

- **What it is**: A job runs every N minutes: e.g. “find orders created in the last 5 minutes that don’t have a confirmation email” and send emails; or “find payments completed without a shipment” and create shipments.
- **Pros**: Very simple, no event table, no broker.
- **Cons**: Not truly event-driven; slight delay; need to design queries and idempotency.

### 4. HTTP callbacks (webhooks)

- **What it is**: After saving the order, the API does a non-blocking HTTP POST to an internal or external URL that performs the side effects.
- **Pros**: Decoupled, can call another service.
- **Cons**: No built-in durability or retry; the receiver must be up.

**Summary**: For “no broker, minimal setup”, use **in-process async** (option 1). For “no broker but durable”, add a **DB outbox** (option 2).

---

## Kafka — configured and optional

Kafka is **implemented** and **off by default**. When enabled, order and payment events are published to Kafka topics; example consumers log and can be extended (email, shipment, etc.).

### Enable Kafka

1. **Set environment variables** (or add to `.env`):
   - `KAFKA_ENABLED=true`
   - `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` (or your broker list)

2. **Run a Kafka broker** (e.g. Docker):
   ```bash
   docker run -d --name kafka -p 9092:9092 apache/kafka:latest
   ```
   Or use a managed Kafka (Confluent, AWS MSK, etc.) and set `KAFKA_BOOTSTRAP_SERVERS` accordingly.

3. **Start the application.** With Kafka disabled (default), the app starts without connecting to a broker. With `KAFKA_ENABLED=true`, it will connect and publish events when orders are created and payments are confirmed.

### What’s in place for Kafka

- **Dependency**: `spring-kafka` in `pom.xml`
- **Config**: `app.kafka.enabled`, `app.kafka.bootstrap-servers`, `app.kafka.topics.*` in `application.yml`; `spring.kafka.*` for producer/consumer
- **Producer**: `KafkaOrderEventPublisher` sends to `orders.created` and `payments.completed` (topic names configurable)
- **Event payloads**: `OrderCreatedEvent`, `PaymentCompletedEvent` in `messaging` package (JSON)
- **Example consumers**: `OrderEventKafkaListener` logs events; replace or extend with email, shipment, analytics
- **Optional**: When `app.kafka.enabled=false`, Kafka auto-config is excluded so no broker connection is attempted; `NoOpOrderEventPublisher` is used instead

---

## What’s already there (shared)

1. **`OrderEventPublisher`** (`service/OrderEventPublisher.java`)  
   - `orderCreated(Order order)`  
   - `paymentCompleted(UUID orderId, UUID paymentId)`

2. **`NoOpOrderEventPublisher`**  
   - Used when Kafka is disabled and in-process is disabled (`@ConditionalOnMissingBean(OrderEventPublisher.class)`).

3. **`InProcessOrderEventPublisher`**  
   - Used when Kafka is disabled and `app.messaging.in-process.enabled=true`. Runs `InProcessOrderEventHandlers` with `@Async` (no broker).

4. **Wiring**  
   - **OrderController** and **PaymentController** call the publisher after creating an order and after confirming a payment.

---

## What’s missing to use RabbitMQ

### 1. Dependency

In `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2. Configuration

In `application.yml` (or env):

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

Or use `SPRING_RABBITMQ_HOST`, etc.

### 3. Producer implementation

- Add a **component** that implements `OrderEventPublisher` (e.g. `RabbitOrderEventPublisher`).
- Inject `RabbitTemplate` (or `AmqpTemplate`).
- In `orderCreated`: send a message to a queue or topic, e.g. `orders.created`, with a payload that identifies the order (e.g. order ID, tenant ID). Serialize with JSON (e.g. Jackson).
- In `paymentCompleted`: send to e.g. `payments.completed` with order ID and payment ID (and tenant ID if multi-tenant).
- Define **queues/exchanges** (e.g. via `@Bean` `Queue` / `DirectExchange` and `Binding`, or via RabbitMQ management UI).

### 4. Disable the no-op when RabbitMQ is used

- Make `NoOpOrderEventPublisher` conditional, e.g. `@ConditionalOnMissingBean(OrderEventPublisher.class)` (so if you provide a `RabbitOrderEventPublisher` bean, the no-op is not used), **or**
- Provide the Rabbit implementation only when a property is set, e.g. `@ConditionalOnProperty(name = "messaging.rabbit.enabled", havingValue = "true")`, and keep the no-op as default.

### 5. Consumers (optional but typical)

- **Consumer for `order.created`**: e.g. send order confirmation email, notify warehouse, or trigger inventory reserve (if you move that logic async).
- **Consumer for `payment.completed`**: e.g. update order status to “confirmed”, trigger shipment creation, send receipt email.

Consumers can live in the same app (`@RabbitListener`) or in a separate service that connects to the same RabbitMQ.

---

## What’s missing to use Kafka (reference — already implemented)

Kafka is already implemented; the items below are done. Kept here as reference.

- ~~Dependency~~ — `spring-kafka` in `pom.xml`
- ~~Configuration~~ — `app.kafka.*` and `spring.kafka.*` in `application.yml`
- ~~Producer~~ — `KafkaOrderEventPublisher`; events: `OrderCreatedEvent`, `PaymentCompletedEvent`
- ~~No-op when disabled~~ — `NoOpOrderEventPublisher` is `@ConditionalOnMissingBean`; Kafka publisher is `@ConditionalOnProperty("app.kafka.enabled", havingValue = "true")`
- ~~Consumers~~ — `OrderEventKafkaListener` (example); extend for email, shipment, etc.

---

## Event payload suggestion

Keep payloads small and stable (e.g. IDs + tenant):

- **order.created**: `{ "orderId": "uuid", "tenantId": "uuid", "customerId": "uuid", "total": "..." }`
- **payment.completed**: `{ "orderId": "uuid", "paymentId": "uuid", "tenantId": "uuid" }`

Consumers can load full `Order` / `Payment` from the DB by ID if needed. That avoids sending large entities and keeps the contract simple.

---

## Summary

| Piece | Status |
|-------|--------|
| Event abstraction (`OrderEventPublisher`) | Done |
| No-op implementation | Done |
| **In-process async** (no broker) | Done — set `MESSAGING_IN_PROCESS_ENABLED=true` |
| Kafka (optional) | Done — set `KAFKA_ENABLED=true` |
| Calling publisher on order create / payment complete | Done |
| RabbitMQ | Optional — you add dependency + implementation |
| DB outbox / polling / webhooks | Optional — you add if you need durability or different patterns |

With **in-process async** you get non-blocking, decoupled handling (log, email, shipment) without Kafka or RabbitMQ. For durability or multiple consumers, use Kafka or a DB outbox.
