# CACHE.md

Tài liệu mô tả toàn bộ hệ thống cache trong project `java-hexagonal`, bao gồm hai cơ chế độc lập: **Version Cache** (annotation-driven) và **Dual Cache Service** (programmatic).

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Cơ chế 1 — Version Cache (@VersionCache / @BumpVersion)](#2-cơ-chế-1--version-cache)
   - [Thành phần](#21-thành-phần)
   - [Key format](#22-key-format)
   - [Flow đọc (READ)](#23-flow-đọc-read)
   - [Flow ghi (WRITE) + Pub/Sub broadcast](#24-flow-ghi-write--pubsub-broadcast)
   - [Sequence diagram](#25-sequence-diagram)
   - [Cách sử dụng](#26-cách-sử-dụng)
3. [Cơ chế 2 — Dual Cache Service](#3-cơ-chế-2--dual-cache-service)
   - [Thành phần](#31-thành-phần)
   - [Flow đọc (stale-while-revalidate)](#32-flow-đọc-stale-while-revalidate)
   - [Sequence diagram](#33-sequence-diagram)
   - [Cách sử dụng](#34-cách-sử-dụng)
4. [Cơ chế 3 — Spring @Cacheable (CacheConfig)](#4-cơ-chế-3--spring-cacheable)
5. [So sánh ba cơ chế](#5-so-sánh-ba-cơ-chế)
6. [Best practice patterns](#6-best-practice-patterns)

---

## 1. Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────┐
│                      Hệ thống Cache                         │
│                                                             │
│  ┌──────────────────────┐   ┌──────────────────────────┐   │
│  │   Version Cache      │   │   Dual Cache Service     │   │
│  │  @VersionCache       │   │  DualCacheService.get()  │   │
│  │  @BumpVersion        │   │  DualCacheService.evict()│   │
│  │  + Redis Pub/Sub     │   │  + Refresh-ahead         │   │
│  └──────────┬───────────┘   └────────────┬─────────────┘   │
│             │                            │                  │
│  ┌──────────▼────────────────────────────▼─────────────┐   │
│  │              L1: Local Cache (JVM)                  │   │
│  │  VersionLocalCache (Caffeine, 30-39s, 50k entries)  │   │
│  │  DualCacheServiceImpl.localCache (ConcurrentHashMap)│   │
│  └──────────────────────────┬──────────────────────────┘   │
│                             │                              │
│  ┌──────────────────────────▼──────────────────────────┐   │
│  │              L2: Redis (Lettuce driver)             │   │
│  │  StringRedisTemplate  — version keys & data keys    │   │
│  │  RedisTemplate<String,Object> — DualCacheService    │   │
│  │  RedisMessageListenerContainer — Pub/Sub subscriber │   │
│  └──────────────────────────┬──────────────────────────┘   │
│                             │                              │
│  ┌──────────────────────────▼──────────────────────────┐   │
│  │                   Database (H2/MySQL)               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Cơ chế 1 — Version Cache

### 2.1 Thành phần

| File | Layer | Vai trò |
|---|---|---|
| `@VersionCache` | annotation | Đánh dấu method đọc cần cache |
| `@BumpVersion` | annotation | Đánh dấu method ghi cần invalidate cache |
| `VersionCacheAspect` | infrastructure/anotation/handler | AOP interceptor xử lý cả read và write |
| `VersionLocalCache` | infrastructure/anotation/handler | L1 cache (Caffeine) lưu version number |
| `VersionInvalidateSubscriber` | infrastructure/cache | Redis Pub/Sub subscriber, gọi `localCache.invalidate()` |
| `RedisPubSubConfig` | infrastructure/config | Đăng ký listener container, khai báo channel name |

**Ý tưởng cốt lõi:** Thay vì xóa data cache khi có write, hệ thống tăng một số version. Data key cũ tự nhiên trở nên "vô hình" vì không còn key nào trỏ tới nó (sẽ tự expire theo TTL). Không cần delete explicit.

### 2.2 Key format

```
Version key : "version:{entity}:{userId}"
              "version:users:GLOBAL"
              "version:order:user-123"

Data key    : "{entity}:data:{userId}:{extraKey}:v{version}"
              "users:data:GLOBAL:0_10_id_ASC:v3"
              "order:data:user-123:default:v7"

Pub/Sub ch  : "version:invalidate"
```

### 2.3 Flow đọc (READ)

```
@VersionCache method được gọi
        │
        ▼
[1] Lấy vKey = "version:{entity}:{userId}"
        │
        ▼
[2] VersionLocalCache.get(vKey)
        │
   ┌────┴────┐
  HIT       MISS
   │         │
   │        [3] Redis GET vKey
   │         │
   │    ┌────┴────┐
   │   HIT       MISS
   │    │         │
   │    │        version = "0"
   │    │         │
   │    └────┬────┘
   │    VersionLocalCache.put(vKey, version)
   │         │
   └────┬────┘
        │
        ▼
[4] extraKey = join(SpEL(extraKeys[]))
        │
        ▼
[5] finalKey = "{entity}:data:{userId}:{extraKey}:v{version}"
        │
        ▼
[6] Redis GET finalKey
        │
   ┌────┴────┐
  HIT       MISS
   │         │
   │        [7] joinPoint.proceed() → DB
   │         │
   │        [8] Redis SET finalKey = JSON(result), TTL
   │         │
   └────┬────┘
        │
        ▼
    return result
```

### 2.4 Flow ghi (WRITE) + Pub/Sub broadcast

```
@BumpVersion method được gọi (và return thành công)
        │
        ▼ (@AfterReturning)
[1] vKey = "version:{entity}:{userId}"
        │
        ▼
[2] Redis INCR vKey  →  v3 → v4 (atomic)
        │
        ▼
[3] Redis EXPIRE vKey 3 DAYS
        │
        ▼
[4] Redis PUBLISH "version:invalidate" vKey
        │
        ├─────────────────────────────────────┐
        │                                     │
        ▼                                     ▼
  Instance A                           Instance B, C, ...
  VersionInvalidateSubscriber          VersionInvalidateSubscriber
  .onMessage(vKey)                     .onMessage(vKey)
        │                                     │
        ▼                                     ▼
  VersionLocalCache                    VersionLocalCache
  .invalidate(vKey)                    .invalidate(vKey)
```

Lần đọc tiếp theo trên bất kỳ instance nào:
- `localCache.get(vKey)` = `null` (đã invalidate)
- Redis trả về version mới (v4)
- Data key mới = `...v4` → cache miss → fetch DB fresh

### 2.5 Sequence diagram

#### READ flow (multi-instance)

```
Client        Instance A          VersionLocalCache    Redis           DB
  │                │                      │              │              │
  │─GET /users────►│                      │              │              │
  │                │──get(vKey)──────────►│              │              │
  │                │◄── null (MISS) ──────│              │              │
  │                │──GET vKey────────────────────────►  │              │
  │                │◄── "3" ──────────────────────────   │              │
  │                │──put(vKey,"3")───────►│              │              │
  │                │──GET data:v3─────────────────────►  │              │
  │                │◄── null (MISS) ───────────────────  │              │
  │                │─────────────────────────────────────────────────►  │
  │                │◄──────────────── List<UserDto> ─────────────────   │
  │                │──SET data:v3 = JSON ─────────────►  │              │
  │◄─ 200 ─────── │                      │              │              │
```

#### WRITE flow + Pub/Sub (multi-instance)

```
Client   Instance A        Redis          Instance B        Instance C
  │           │               │                │                 │
  │─POST /u──►│               │                │                 │
  │           │─INCR vKey────►│                │                 │
  │           │◄── "4" ───────│                │                 │
  │           │─EXPIRE vKey──►│                │                 │
  │           │─PUBLISH ──────►────────────────►────────────────►│
  │           │  "version:    │  onMessage()   │  onMessage()    │
  │           │   invalidate" │  invalidate()  │  invalidate()   │
  │           │               │                │                 │
  │◄─ 200 ───│               │                │                 │
  │           │               │                │                 │
  │           │  [Next READ on Instance B]      │                 │
  │           │               │ ──get(vKey)───►│                 │
  │           │               │ ◄─ null (MISS)─│                 │
  │           │               │ ──GET vKey───► │                 │
  │           │               │ ◄── "4" ────── │                 │
  │           │               │ ──GET data:v4─►│                 │
  │           │               │ ◄─ null ─────  │  (fetch DB)     │
```

### 2.6 Cách sử dụng

#### Trường hợp 1 — Cache toàn bộ danh sách (global, không phân biệt user)

```java
private static final String CACHE_NAME = "users";

@VersionCache(
    entity = CACHE_NAME,
    userId = "'GLOBAL'",            // Hằng số string literal trong SpEL
    extraKeys = {"#page", "#size", "#sort", "#direction"},
    ttl = 5,
    unit = TimeUnit.MINUTES
)
public RestPage<UserDto> getUsers(Integer page, Integer size, String sort, String direction) {
    // ...
}

@BumpVersion(entity = CACHE_NAME, userId = "'GLOBAL'")
public void clearCache() {}         // Body rỗng — AOP tự xử lý hoàn toàn
```

#### Trường hợp 2 — Cache theo từng user (per-user)

```java
@VersionCache(
    entity = "order",
    userId = "#request.userId",     // SpEL lấy từ param
    extraKeys = {"#request.page", "#request.size"},
    ttl = 10,
    unit = TimeUnit.MINUTES
)
public RestPage<OrderDto> getOrders(OrderRequest request) {
    // ...
}

@BumpVersion(entity = "order", userId = "#request.userId")
public OrderDto createOrder(OrderRequest request) {
    // Chỉ invalidate cache của user đó
    return orderRepo.save(...);
}
```

#### Trường hợp 3 — Cache không có extra key (single cached result)

```java
@VersionCache(
    entity = "config",
    userId = "'SYSTEM'",
    ttl = 30,
    unit = TimeUnit.MINUTES
    // extraKeys không khai báo → mặc định "default"
)
public SystemConfig getSystemConfig() {
    return configRepo.findActive();
}

@BumpVersion(entity = "config", userId = "'SYSTEM'")
public void updateConfig(SystemConfig config) {
    configRepo.save(config);
}
```

#### Trường hợp 4 — userId lấy từ Security Context

```java
// Tạo util method trong service hoặc dùng @ContextUtils hiện có
@VersionCache(
    entity = "profile",
    userId = "@contextUtils.getCurrentUserId()",  // SpEL gọi Spring bean
    ttl = 15,
    unit = TimeUnit.MINUTES
)
public ProfileDto getMyProfile() {
    // ...
}
```

---

## 3. Cơ chế 2 — Dual Cache Service

### 3.1 Thành phần

| File | Vai trò |
|---|---|
| `DualCacheService` | Interface định nghĩa API |
| `DualCacheServiceImpl` | Impl: L1 (`ConcurrentHashMap`) + L2 (Redis) + single-flight + refresh-ahead |

**Khác biệt so với Version Cache:** Đây là programmatic API — gọi trực tiếp từ code, không qua AOP. Không có cơ chế invalidate cross-instance (không có Pub/Sub). Phù hợp cho dữ liệu ít thay đổi hoặc chấp nhận stale ngắn.

**Patterns tích hợp sẵn:**
- **Stale-while-revalidate**: khi local entry expired, trả về dữ liệu cũ ngay, đồng thời trigger refresh ngầm
- **Single-flight**: `refreshFutures` map đảm bảo chỉ có 1 DB call cho cùng 1 key tại một thời điểm

### 3.2 Flow đọc (stale-while-revalidate)

```
DualCacheService.get(cacheName, key, dbFetcher, ttl) được gọi
        │
        ▼
[1] redisKey = "{cacheName}:{key}"
        │
        ▼
[2] localCache.get(redisKey)
        │
   ┌────┴────────────┐
  HIT               MISS
   │                 │
  ┌┴──────────┐     [3] Redis GET redisKey
  │ Expired?  │          │
  └┬─────────┬┘     ┌────┴────┐
  NO        YES    HIT       MISS
   │          │     │         │
   │    return old  │        [4] dbFetcher.get() → DB
   │    + trigger   │         │
   │    refresh     │        [5] updateCachesAsync()
   │    (async) ◄───┘         │
   │                          │
   └──────────────┬───────────┘
                  │
                  ▼
              return result
```

**Single-flight trong refresh:**
```
Key "users:page-1" expired đồng thời → 100 requests cùng lúc
        │
        ▼
computeIfAbsent(key, ...) → chỉ 1 CompletableFuture được tạo
        │
        ├── 99 requests: dùng lại future đó (không tạo thêm)
        └──  1 request: thực sự gọi DB
```

### 3.3 Sequence diagram

```
Caller      DualCacheServiceImpl    LocalCache(HashMap)    Redis         DB
  │                 │                       │                │            │
  │─get(name,key)──►│                       │                │            │
  │                 │──get(redisKey)────────►│                │            │
  │                 │◄── entry (expired) ───│                │            │
  │                 │                       │                │            │
  │                 │   [return stale immediately]           │            │
  │◄── stale data ─│                       │                │            │
  │                 │                       │                │            │
  │                 │   [async refresh]     │                │            │
  │                 │──────────────────────────────────────────────────►  │
  │                 │◄─────────────────────────────── newValue ─────────  │
  │                 │──put(redisKey, newValue)──────►│        │            │
  │                 │──SET redisKey ────────────────────────►│            │
```

### 3.4 Cách sử dụng

#### Inject và gọi cơ bản

```java
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final DualCacheService dualCacheService;
    private final ProductRepo productRepo;

    public ProductDto getProduct(Long id) {
        return dualCacheService.get(
            "product",                       // cacheName
            String.valueOf(id),              // key
            () -> productRepo.findById(id)   // dbFetcher (Supplier)
                   .map(productConverter::toDto)
                   .orElse(null),
            Duration.ofMinutes(10)           // ttl
        );
    }

    public void updateProduct(Long id, UpdateRequest req) {
        productRepo.save(...);
        dualCacheService.evict("product", String.valueOf(id)); // xóa 1 entry
    }

    public void clearAll() {
        dualCacheService.evict("product"); // xóa toàn bộ
    }
}
```

---

## 4. Cơ chế 3 — Spring @Cacheable

Được cấu hình trong `CacheConfig.java`. Dùng Spring's `@Cacheable`, `@CacheEvict`, `@CachePut` annotations.

- Primary `CacheManager` (bean `cacheManager`): `CompositeCacheManager` → delegate tới `redisCacheManager`
- TTL mặc định: **2 phút**
- Không có invalidation cross-instance (không tích hợp Pub/Sub)

```java
@Cacheable(cacheNames = "roles", key = "#userId")
public List<String> getRoles(Long userId) { ... }

@CacheEvict(cacheNames = "roles", key = "#userId")
public void revokeRoles(Long userId) { ... }
```

> Dùng cơ chế này khi muốn cache đơn giản, không cần phân trang phức tạp, không cần multi-instance consistency.

---

## 5. So sánh ba cơ chế

| Tiêu chí | Version Cache | Dual Cache Service | Spring @Cacheable |
|---|---|---|---|
| **Cách dùng** | Annotation AOP | Programmatic inject | Annotation Spring |
| **L1 (local)** | Caffeine (Jitter TTL 30-39s) | ConcurrentHashMap (manual TTL) | Caffeine (2 phút) |
| **L2 (distributed)** | Redis (StringRedisTemplate) | Redis (RedisTemplate) | Redis (RedisCacheManager) |
| **Invalidation strategy** | Version bump → stale key vô hiệu hóa | Explicit `evict()` | `@CacheEvict` |
| **Cross-instance sync** | Redis Pub/Sub (broadcast ngay lập tức) | Không có | Không có |
| **Stale-while-revalidate** | Không | Có (built-in) | Không |
| **Single-flight** | Không | Có (built-in) | Không |
| **Phù hợp cho** | Read-heavy, multi-instance, có phân trang | Dữ liệu ít thay đổi, cần refresh-ahead | Cache đơn giản, single-instance |
| **Extra key (pagination)** | Có (`extraKeys` SpEL array) | Có (tự ghép vào `key`) | Có (`key` SpEL) |

---

## 6. Best practice patterns

### Pattern 1 — Tách entity name thành constant

```java
// Tránh magic string, dễ refactor và tránh typo
public class UserServiceImpl {
    private static final String ENTITY = "users";

    @VersionCache(entity = ENTITY, userId = "'GLOBAL'", ...)
    public RestPage<UserDto> getUsers(...) { ... }

    @BumpVersion(entity = ENTITY, userId = "'GLOBAL'")
    public void invalidate() {}
}
```

### Pattern 2 — Empty body @BumpVersion

Không đặt business logic trong method có `@BumpVersion` có body rỗng. AOP `@AfterReturning` chỉ fire khi method **return bình thường** — nếu bạn throw exception, cache sẽ không bị bump (đúng behavior).

```java
// ĐÚNG: body rỗng, bump là side-effect của write thật
@BumpVersion(entity = "order", userId = "#req.userId")
public OrderDto createOrder(CreateOrderRequest req) {
    return orderRepo.save(fromRequest(req));
}

// ĐÚNG: dùng như "manual cache clear" endpoint
@BumpVersion(entity = "users", userId = "'GLOBAL'")
public void clearUsersCache() {}   // body rỗng có chủ ý

// SAI: đừng dùng @BumpVersion trên method có thể throw tùy tiện
// vì @AfterReturning sẽ không fire khi có exception
```

### Pattern 3 — TTL data ngắn hơn TTL version key

```java
// Version key sống 3 ngày (VERSION_KEY_TTL_DAYS = 3)
// Data key nên sống ngắn hơn nhiều
@VersionCache(entity = "report", userId = "'GLOBAL'", ttl = 30, unit = TimeUnit.MINUTES)

// Nếu data TTL > version key TTL → sau khi version key hết hạn,
// hệ thống tạo version "0" mới → data key cũ version:v5 vẫn còn trong Redis
// nhưng key mới sẽ là v0 → không bao giờ trỏ tới data cũ → OK (tự expire)
```

### Pattern 4 — Granular invalidation (per-user thay vì GLOBAL)

```java
// BAD: bump GLOBAL → invalidate cache của TẤT CẢ users
@BumpVersion(entity = "cart", userId = "'GLOBAL'")
public CartDto addToCart(String userId, ...) { ... }

// GOOD: bump per-user → chỉ invalidate cache của đúng user đó
@BumpVersion(entity = "cart", userId = "#userId")
public CartDto addToCart(String userId, ...) { ... }
```

### Pattern 5 — Kết hợp DualCacheService cho hot path không cần invalidation tức thì

```java
// Cấu hình hệ thống — chỉ thay đổi khi deploy → TTL dài, chấp nhận stale ngắn
public SystemConfig getConfig() {
    return dualCacheService.get("sysconfig", "active",
        () -> configRepo.findActive(),
        Duration.ofHours(1)   // TTL dài, stale-while-revalidate xử lý
    );
}

// Dữ liệu user cụ thể — cần consistency → dùng VersionCache + Pub/Sub
@VersionCache(entity = "profile", userId = "#userId", ttl = 10, unit = TimeUnit.MINUTES)
public ProfileDto getProfile(String userId) { ... }
```

### Pattern 6 — Không dùng redis.keys() trong production (evict toàn bộ cache name)

`DualCacheService.evict(cacheName)` hiện tại gọi `redis.keys(pattern)` — đây là lệnh **O(N)**, block Redis trong production. Thay thế bằng Redis Sets hoặc naming convention kết hợp với Lua script khi dataset lớn.

```java
// Thay thế an toàn hơn cho evict(cacheName):
// Track keys bằng Redis Set
redis.opsForSet().add("__index__:" + cacheName, key);

// Khi evict:
Set<Object> keys = redis.opsForSet().members("__index__:" + cacheName);
redis.delete(keys.stream().map(k -> cacheName + ":" + k).collect(toList()));
redis.delete("__index__:" + cacheName);
```

### Pattern 7 — Pub/Sub là fire-and-forget, cần fallback

Redis Pub/Sub **không có persistence**. Nếu instance B đang restart đúng lúc nhận event, message sẽ bị mất. Fallback tự nhiên là TTL của `VersionLocalCache` (30-39s) — sau thời gian đó, local cache sẽ tự fetch từ Redis và lấy version mới. Đây là acceptable tradeoff — không cần implement lại persistence layer cho Pub/Sub.

```
Instance B restart → miss Pub/Sub event
        │
        ▼
Sau tối đa 39 giây: VersionLocalCache tự expire
        │
        ▼
Lần đọc tiếp → fetch version từ Redis → version mới → data mới
```

### Pattern 8 — Monitor cache health

Các Redis key cần theo dõi:

```bash
# Xem tất cả version key hiện tại
redis-cli KEYS "version:*"

# Xem giá trị của một version key
redis-cli GET "version:users:GLOBAL"

# Theo dõi Pub/Sub traffic
redis-cli SUBSCRIBE "version:invalidate"

# Đếm data key đang tồn tại của một entity
redis-cli KEYS "users:data:*" | wc -l

# Xem TTL còn lại của version key
redis-cli TTL "version:users:GLOBAL"
```
