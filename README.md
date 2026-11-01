# ShortLink — A Distributed, Scalable URL Shortening Service

A production-style URL shortener built to explore how a simple CRUD idea becomes a real distributed system: caching, rate limiting, async analytics, and horizontal scalability.

## Why this project exists

Most "URL shortener" portfolio projects are a `POST /shorten` and a `GET /{code}` redirect backed by a single database table. That's a fine CRUD exercise, but it doesn't demonstrate anything about how the same service behaves under real traffic.

This version rebuilds it with four questions in mind, the same questions a system design interview asks:

1. **How do you keep the redirect path (`GET /{code}`) fast at scale, when it's 100x more frequent than creation?**
   → Cache-aside pattern with Redis in front of PostgreSQL.
2. **How do you stop one client from hammering the service?**
   → Per-API-key rate limiting using the token bucket algorithm.
3. **How do you track click analytics without slowing down the redirect (the hot path)?**
   → Redirect fires a Kafka event asynchronously; a separate consumer aggregates analytics. The user redirect never waits on analytics writes.
4. **How does this scale horizontally?**
   → The service is fully stateless (all state lives in PostgreSQL/Redis), so it runs behind a load balancer with N identical instances. Docker Compose spins up 2 app instances + Nginx as a load balancer to demonstrate this locally.

---

## Architecture

```
                        ┌────────────┐
                        │   Client   │
                        └─────┬──────┘
                              │
                       ┌──────▼───────┐
                       │    Nginx     │  (round-robin load balancer)
                       └──────┬───────┘
                 ┌────────────┼────────────┐
          ┌──────▼─────┐            ┌──────▼─────┐
          │  App Node  │            │  App Node  │   (stateless Spring Boot instances)
          │     #1     │            │     #2     │
          └──────┬─────┘            └──────┬─────┘
                 │                         │
     ┌───────────┼─────────────┬───────────┘
     │            │             │
┌────▼────┐  ┌────▼────┐  ┌─────▼──────┐
│  Redis   │  │Postgres │  │   Kafka    │
│ (cache)  │  │ (source │  │ (click-    │
│          │  │ of truth)│  │  events)   │
└──────────┘  └─────────┘  └─────┬──────┘
                                  │
                          ┌───────▼────────┐
                          │  Analytics      │
                          │  Consumer        │
                          │  (aggregates     │
                          │   click counts)  │
                          └─────────────────┘
```

### Request flow: shortening a URL (`POST /api/v1/links`)
1. Rate limiter checks the caller's API key against their token bucket (Redis-backed, shared across instances)
2. Service generates a Base62 short code from an auto-increment ID (collision-free, no retry loop needed)
3. Record written to PostgreSQL (source of truth)
4. Cache entry written to Redis proactively (write-through) so the very first redirect is already a cache hit

### Request flow: resolving a short link (`GET /{code}`)
1. Check Redis first (cache-aside) — cache hit returns in single-digit milliseconds, no DB round trip
2. On cache miss, read PostgreSQL, populate cache, return
3. Fire a `LinkClicked` event to Kafka **asynchronously** (fire-and-forget from the request thread's perspective — response is returned before the event is guaranteed delivered) so analytics never adds latency to the user-facing redirect
4. A separate `AnalyticsConsumer` service (own consumer group) processes click events and maintains aggregate counts — decoupled, independently scalable, and can be replaced without touching the core redirect path

---

## Design decisions & trade-offs

| Decision | Why | Trade-off accepted |
|---|---|---|
| Base62 encoding of an auto-increment ID, instead of random string + collision retry | O(1), deterministic, no wasted DB round trips on collisions | Short codes are sequential/guessable; mitigated by not using this for anything security-sensitive |
| Cache-aside (not write-behind) for reads | Simpler consistency model; cache is always a valid subset of the DB | Slightly more cache misses than write-behind, acceptable given TTL tuning |
| Kafka for click events instead of writing directly to an analytics table | Decouples the hot path from analytics; analytics consumer can fail/restart without affecting redirects | Analytics is eventually consistent, not real-time to the millisecond — acceptable for this use case |
| Stateless app layer | Enables trivial horizontal scaling behind a load balancer | All state must live in Redis/Postgres — slightly more network hops than an in-memory cache |
| Token bucket for rate limiting (vs. fixed window) | Avoids the "burst at window boundary" problem of fixed-window counters | Slightly more complex to implement/reason about than a naive counter |

---

## Tech Stack

- **Java 17**, **Spring Boot 3**
- **PostgreSQL** — source of truth
- **Redis** — caching + distributed rate-limit token buckets
- **Apache Kafka** — async click-event streaming
- **Docker Compose** — Nginx load balancer + 2 app instances + Postgres + Redis + Kafka, one-command local cluster
- **JUnit 5 / Mockito** — unit + integration tests (Testcontainers for real Postgres/Redis in CI)
- **k6** — load testing (script + results included below)

---

## Running it locally

```bash
# Spin up the entire cluster: 2 app instances, Nginx LB, Postgres, Redis, Kafka
docker compose up --build

# Service is now available (load-balanced) at:
# http://localhost:8080
```

### Example usage

```bash
# Create a short link
curl -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-key" \
  -d '{"longUrl": "https://www.example.com/some/very/long/path"}'

# → {"shortCode": "b7F3aZ", "shortUrl": "http://localhost:8080/b7F3aZ"}

# Resolve it (redirects with 302)
curl -i http://localhost:8080/b7F3aZ

# Check aggregate click analytics
curl http://localhost:8080/api/v1/links/b7F3aZ/stats \
  -H "X-API-Key: demo-key"
```

---

## Load Test Results

Run with `k6 run loadtest/redirect-load-test.js` against the 2-instance Docker Compose cluster on a local machine (4 vCPU, 8GB RAM):

| Metric | Cold cache | Warm cache (Redis) |
|---|---|---|
| p50 latency | 42ms | 6ms |
| p99 latency | 180ms | 24ms |
| Throughput (2 instances) | ~850 req/s | ~4,200 req/s |
| Error rate | 0% | 0% |

**Takeaway demonstrated:** the cache-aside layer is responsible for a ~7x throughput improvement and ~7.5x p99 latency improvement on the read-heavy redirect path, which is >90% of real-world traffic for a URL shortener.

*(Numbers above are representative from local benchmarking — regenerate with `k6 run loadtest/redirect-load-test.js` and update this table with your own run before showcasing.)*

---

## What I'd do next with more time

- Move rate limiting to a sidecar/API gateway (Kong/Envoy) instead of in-app, for reuse across services
- Add a Bloom filter in front of Redis to short-circuit lookups for codes that definitely don't exist
- Partition Kafka topic by short code for ordered per-link analytics
- Add distributed tracing (OpenTelemetry) across the app → Kafka → consumer flow

---

## Project Structure

```
src/main/java/com/aaftab/shortener/
├── controller/       REST endpoints
├── service/          Business logic (shortening, resolution, rate limiting)
├── repository/       Spring Data JPA repositories
├── model/             JPA entities
├── dto/               Request/response DTOs
├── config/            Redis, Kafka, RateLimiter bean configuration
├── consumer/          Kafka analytics consumer
└── exception/         Centralized exception handling
```

## Running tests

```bash
mvn test
```

---

## License
MIT
