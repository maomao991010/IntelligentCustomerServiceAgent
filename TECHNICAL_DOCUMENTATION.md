# 票务系统技术文档

## 1. 项目概述

### 1.1 项目目的
票务系统是一个基于Spring Boot的高性能、高并发在线票务平台，旨在为用户提供安全、稳定、高效的票务购买体验。系统支持演唱会、体育赛事、剧院演出等多种票务场景。

### 1.2 项目范围
- **用户管理**：注册、登录、个人信息管理
- **场次管理**：演出场次创建、查询、管理
- **座位管理**：座位图展示、状态管理、实时更新
- **订单管理**：订单创建、支付、取消、查询
- **支付系统**：支持多种支付方式，确保交易安全
- **高并发处理**：支持大规模用户同时购票

### 1.3 技术特色
- **微服务架构**：基于Spring Cloud的分布式架构
- **高并发处理**：Redis分布式锁 + RabbitMQ消息队列
- **实时通信**：WebSocket实现座位状态实时同步
- **安全认证**：JWT + Spring Security双重安全保障

## 2. 核心功能

### 2.1 用户认证与授权

#### 功能描述
- 用户注册、登录、退出
- JWT令牌认证机制
- 权限控制与角色管理

#### 工作流程
```
用户注册 → 邮箱验证 → 登录认证 → JWT令牌生成 → 权限验证 → 访问控制
```

#### 技术实现
```java
// JWT认证过滤器
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) {
        // JWT令牌验证逻辑
        String token = getTokenFromRequest(request);
        if (token != null && jwtUtil.validateToken(token)) {
            Authentication authentication = jwtUtil.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

### 2.2 座位选择与锁定（详细实现）

#### 功能描述
座位选择与锁定是票务系统的核心功能，涉及前端交互、后端处理、并发控制和实时通信等多个技术环节。

#### 完整工作流程
```
前端加载座位图 → 用户选择座位 → 发送锁定请求 → 后端状态验证 → 分布式锁控制 → 数据库状态更新 → Redis缓存锁定信息 → WebSocket广播状态更新 → 前端状态同步
```

#### 详细步骤实现

##### 步骤1：加载座位图
**前端实现 (app.js)**
```javascript
async function loadSeatMap(sessionId) {
    const grid = document.getElementById('seatGrid');
    grid.innerHTML = '<div class="loading">加载座位图...</div>';
    selectedSeats = [];
    updateSelectedSeatsUI();
    
    try {
        const response = await api.seats.getMap(sessionId);
        if (response.code === 200 && response.data) {
            loadSeatMapData = response.data;
            renderSeatMap(response.data);
        } else {
            grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p></div>';
        }
    } catch (error) {
        grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p><button class="btn btn-primary" onclick="loadSeatMap(' + sessionId + ')">重新加载</button></div>';
    }
}

function renderSeatMap(seatData) {
    const grid = document.getElementById('seatGrid');
    if (!seatData || Object.keys(seatData).length === 0) {
        grid.innerHTML = '<div class="empty-state"><p>暂无座位数据</p></div>';
        return;
    }
    
    const rowNumbers = Object.keys(seatData).map(Number).sort(function(a, b) { return a - b; });
    let html = '';
    
    rowNumbers.forEach(function(rowNum) {
        const rowSeats = seatData[rowNum];
        if (rowSeats && rowSeats.length > 0) {
            html += '<div class="seat-row">';
            html += '<span class="seat-row-label">' + String.fromCharCode(64 + rowNum) + '</span>';
            
            rowSeats.forEach(function(seat) {
                const isSelected = selectedSeats.some(function(s) { return s.id === seat.id; });
                const normalizedStatus = seat.status ? seat.status.toLowerCase() : 'available';
                let seatStatus = isSelected ? 'selected' : normalizedStatus;
                const statusClass = getSeatStatusClass(seatStatus);
                const isClickable = normalizedStatus === 'available' || isSelected;
                const clickHandler = isClickable ? 'toggleSeat(' + seat.id + ', ' + seat.price + ', \'' + seat.seatType + '\', \'' + String.fromCharCode(64 + seat.rowNumber) + seat.seatNumber + '\')' : '';
                const titleText = seat.seatType + ' ¥' + seat.price;
                
                html += '<div class="seat ' + statusClass + '" ' + (clickHandler ? 'onclick="' + clickHandler + '"' : '') + ' title="' + titleText + '">' + seat.seatNumber + '</div>';
            });
            
            html += '</div>';
        }
    });
    
    grid.innerHTML = html || '<div class="empty-state"><p>暂无座位数据</p></div>';
}
```

**实现逻辑解释：**
- **数据获取**：通过REST API从后端获取指定场次的座位数据
- **状态处理**：将座位状态标准化为小写，便于CSS类名处理
- **渲染优化**：使用字符串拼接而非DOM操作，提高渲染性能
- **错误处理**：包含加载失败的重试机制和用户友好的错误提示

##### 步骤2：选择座位
**前端实现 (app.js)**
```javascript
function toggleSeat(seatId, price, seatType, seatLabel) {
    const index = selectedSeats.findIndex(function(s) { return s.id === seatId; });
    
    if (index > -1) {
        // 取消选择座位
        selectedSeats.splice(index, 1);
    } else {
        // 检查选择数量限制
        if (selectedSeats.length >= 5) {
            showToast('最多只能选择5个座位', 'warning');
            return;
        }
        // 添加座位到选择列表
        selectedSeats.push({ 
            id: seatId, 
            price: price, 
            seatType: seatType, 
            label: seatLabel 
        });
    }
    
    // 重新渲染座位图以更新选择状态
    if (loadSeatMapData) {
        renderSeatMap(loadSeatMapData);
    }
    
    // 更新UI显示
    updateSelectedSeatsUI();
}

function updateSelectedSeatsUI() {
    const list = document.getElementById('selectedSeatsList');
    const totalPrice = document.getElementById('totalPrice');
    const seatCount = document.getElementById('seatCount');
    const confirmBtn = document.getElementById('confirmSeatBtn');
    
    if (selectedSeats.length === 0) {
        list.innerHTML = '<p class="empty-tip">请选择座位</p>';
        confirmBtn.disabled = true;
    } else {
        list.innerHTML = selectedSeats.map(function(seat) {
            return '<div class="selected-seat-item"><span class="seat-label">' + seat.label + '</span><span class="seat-type">' + seat.seatType + '</span><span class="seat-price">¥' + seat.price + '</span></div>';
        }).join('');
        
        const total = selectedSeats.reduce(function(sum, seat) { return sum + seat.price; }, 0);
        totalPrice.textContent = '¥' + total;
        seatCount.textContent = selectedSeats.length + '个座位';
        confirmBtn.disabled = false;
    }
}
```

**实现逻辑解释：**
- **状态切换**：支持座位的选择和取消选择
- **数量限制**：每单最多选择5个座位，符合业务需求
- **实时更新**：选择状态变化后立即更新UI显示
- **数据管理**：维护选择座位的完整信息，便于后续处理

##### 步骤3：发送锁定请求
**前端实现 (app.js)**
```javascript
async function confirmSelection() {
    if (selectedSeats.length === 0) {
        showToast('请选择座位', 'warning');
        return;
    }
    
    try {
        showToast('锁定座位中...', 'success');
        
        // 准备锁定请求数据
        const seatIds = selectedSeats.map(function(s) { return s.id; });
        const response = await api.seats.lock(
            currentSession.id, 
            seatIds, 
            String(currentUser.id), 
            ''
        );
        
        if (response.code === 200) {
            const lockOrderId = response.data.orderId;
            const totalPrice = selectedSeats.reduce(function(sum, s) { return sum + s.price; }, 0);
            
            // 创建临时订单
            showToast('创建订单中...', 'success');
            const createResponse = await api.orders.create(lockOrderId, 'wechat', totalPrice);
            
            if (createResponse.code === 200) {
                const orderId = createResponse.data.orderId;
                
                // 显示支付选择模态框
                showPaymentChoiceModal(orderId, totalPrice, lockOrderId);
            } else {
                showToast('订单创建失败: ' + createResponse.message, 'error');
                loadSeatMap(currentSession.id);
            }
        } else {
            showToast(response.message || '锁定座位失败', 'error');
            loadSeatMap(currentSession.id);
        }
    } catch (error) {
        showToast(error.message || '锁定座位失败', 'error');
        loadSeatMap(currentSession.id);
    }
}
```

**实现逻辑解释：**
- **请求验证**：确保用户已选择座位
- **异步处理**：使用async/await处理异步请求
- **错误处理**：包含完整的错误捕获和用户提示
- **状态管理**：锁定成功后继续订单创建流程

##### 步骤4：执行状态验证
**后端实现 (SeatLockServiceImpl.java)**
```java
@Override
public ResponseVo lockSeats(LockSeatVo lockSeatVo) {
    Long sessionId = lockSeatVo.getSessionId();
    List<Long> seatIds = lockSeatVo.getSeatIds();
    String userId = lockSeatVo.getUserId();
    String verificationCode = lockSeatVo.getVerificationCode();

    // 检查选座数量
    if (seatIds.size() > maxSeatsPerOrder) {
        return ResponseVo.error(400, "每单最多选择5个座位");
    }

    // 检查请求频率限制
    if (!checkRateLimit(userId)) {
        return ResponseVo.error(400, "请求过于频繁，请稍后再试");
    }

    // 检查座位状态
    List<Seat> seats = new ArrayList<>();
    for (Long seatId : seatIds) {
        try {
            Seat seat = seatService.getSeatById(seatId);
            if (seat == null) {
                log.warn("座位不存在: {}", seatId);
                continue;
            }
            if (!"AVAILABLE".equals(seat.getStatus())) {
                return ResponseVo.error(400, "座位已被锁定或售出");
            }
            seats.add(seat);
        } catch (Exception e) {
            log.error("检查座位状态失败: {}", seatId, e);
            return ResponseVo.error(500, "检查座位状态失败");
        }
    }
    
    // 确保至少有一个有效的座位
    if (seats.isEmpty() && !seatIds.isEmpty()) {
        log.warn("没有找到有效的座位，但继续执行");
    }
    
    // 继续后续处理...
}
```

**实现逻辑解释：**
- **业务规则验证**：检查选座数量是否符合限制
- **频率控制**：防止恶意请求和系统过载
- **状态一致性**：确保所有选择的座位都是可售状态
- **容错处理**：对不存在的座位进行警告而非直接失败

##### 步骤5：实现分布式锁定
**后端实现 (SeatLockServiceImpl.java)**
```java
// 生成锁定订单号
String lockOrderId = "LOCK_" + UUID.randomUUID().toString().replaceAll("-", "");

// 使用分布式锁确保并发安全
String lockKey = "seat_lock_" + sessionId;
String lockValue = UUID.randomUUID().toString();

try {
    boolean locked = redisDistributedLock.tryLock(lockKey, lockValue, 3, 100);
    if (!locked) {
        return ResponseVo.error(400, "系统繁忙，请稍后再试");
    }
    
    // 在分布式锁保护下执行关键业务逻辑
    // ...
    
} finally {
    // 释放分布式锁
    redisDistributedLock.unlock(lockKey, lockValue);
}
```

**分布式锁实现 (RedisDistributedLock.java)**
```java
@Component
public class RedisDistributedLock {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    public boolean tryLock(String lockKey, String lockValue, long expireTime, long waitTime) {
        long end = System.currentTimeMillis() + waitTime;
        
        while (System.currentTimeMillis() < end) {
            // SETNX + EXPIRE 原子操作
            if (redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, TimeUnit.SECONDS)) {
                return true;
            }
            
            try {
                Thread.sleep(100); // 短暂等待后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }
    
    public void unlock(String lockKey, String lockValue) {
        try {
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.error("释放锁失败", e);
        }
    }
}
```

**实现逻辑解释：**
- **锁粒度控制**：以场次为单位加锁，平衡并发性和性能
- **超时机制**：设置合理的锁超时时间，防止死锁
- **重试策略**：在等待时间内重试获取锁
- **安全释放**：确保只有锁的持有者才能释放锁

##### 步骤6：更新座位状态
**后端实现 (SeatLockServiceImpl.java)**
```java
// 批量更新座位状态
seatService.updateSeatStatusBatch(seatIds, "LOCKED", userId);

// 更新场次剩余座位数
sessionService.updateRemainingSeats(sessionId, seatIds.size());

// 缓存锁定信息到Redis，设置过期时间
Map<String, Object> lockInfo = new HashMap<>();
lockInfo.put("lockOrderId", lockOrderId);
lockInfo.put("sessionId", sessionId);
lockInfo.put("seatIds", seatIds);
lockInfo.put("userId", userId);
lockInfo.put("lockTime", LocalDateTime.now());
lockInfo.put("expireTime", LocalDateTime.now().plusSeconds(seatLockTimeout));

redisUtil.set("lock_order_" + lockOrderId, lockInfo, seatLockTimeout);

// 记录用户锁定记录
redisUtil.set("user_lock_" + userId + "_" + lockOrderId, lockInfo, seatLockTimeout);
```

**数据库更新实现 (SeatServiceImpl.java)**
```java
@Override
public void updateSeatStatusBatch(List<Long> seatIds, String status, String userId) {
    if (seatIds == null || seatIds.isEmpty()) {
        return;
    }
    
    // 批量更新座位状态
    seatDao.updateSeatStatusBatch(seatIds, status, userId);
}
```

**MyBatis批量更新 (SeatDao.xml)**
```xml
<update id="updateSeatStatusBatch">
    UPDATE seat 
    SET status = #{status},
        lock_user_id = #{userId},
        lock_time = NOW(),
        update_time = NOW()
    WHERE id IN
    <foreach collection="seatIds" item="seatId" open="(" separator="," close=")">
        #{seatId}
    </foreach>
    AND status = 'AVAILABLE'
</update>
```

**实现逻辑解释：**
- **批量操作**：使用批量更新减少数据库交互次数
- **状态一致性**：更新时检查座位状态，防止并发问题
- **缓存策略**：将锁定信息缓存到Redis，提高查询性能
- **过期管理**：设置合理的过期时间，自动清理过期锁定

##### 步骤7：通过WebSocket广播更新
**后端实现 (SeatLockServiceImpl.java)**
```java
// 通过WebSocket广播座位状态更新
List<Map<String, Object>> seatStatuses = new ArrayList<>();
for (Seat seat : seats) {
    Map<String, Object> status = new HashMap<>();
    status.put("seatId", seat.getId());
    status.put("status", "LOCKED");
    seatStatuses.add(status);
}
seatStatusWebSocketHandler.broadcastSeatStatus(sessionId.toString(), seatStatuses);
```

**WebSocket处理器 (SeatStatusWebSocketHandler.java)**
```java
@Component
public class SeatStatusWebSocketHandler extends TextWebSocketHandler {
    
    private final Map<String, Set<WebSocketSession>> sessionMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = getSessionIdFromUri(session.getUri());
        sessionMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                  .add(session);
        log.info("WebSocket连接建立: {}", sessionId);
    }
    
    public void broadcastSeatStatus(String sessionId, List<Map<String, Object>> seatStatuses) {
        Set<WebSocketSession> sessions = sessionMap.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        String message = JSON.toJSONString(seatStatuses);
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("发送WebSocket消息失败", e);
                }
            }
        }
    }
}
```

**前端WebSocket处理 (app.js)**
```javascript
// WebSocket连接管理
let seatWebSocket = null;

function connectSeatWebSocket(sessionId) {
    if (seatWebSocket) {
        seatWebSocket.close();
    }
    
    const wsUrl = 'ws://' + window.location.host + '/ws/seat-status/' + sessionId;
    seatWebSocket = new WebSocket(wsUrl);
    
    seatWebSocket.onopen = function() {
        console.log('座位状态WebSocket连接已建立');
    };
    
    seatWebSocket.onmessage = function(event) {
        const seatStatuses = JSON.parse(event.data);
        updateSeatStatus(seatStatuses);
    };
    
    seatWebSocket.onclose = function() {
        console.log('座位状态WebSocket连接已关闭');
        // 5秒后重连
        setTimeout(function() {
            connectSeatWebSocket(sessionId);
        }, 5000);
    };
}

function updateSeatStatus(seatStatuses) {
    seatStatuses.forEach(function(status) {
        const seatElement = document.querySelector('[data-seat-id="' + status.seatId + '"]');
        if (seatElement) {
            // 更新座位状态显示
            seatElement.className = 'seat ' + getSeatStatusClass(status.status.toLowerCase());
        }
    });
}
```

**实现逻辑解释：**
- **实时通信**：WebSocket实现服务器到客户端的实时消息推送
- **连接管理**：支持自动重连，保证连接稳定性
- **状态同步**：多客户端座位状态实时同步
- **性能优化**：只推送变化的座位状态，减少网络传输

#### 技术实现总结

座位选择与锁定功能的实现体现了现代Web应用的核心技术特点：

1. **前后端分离**：RESTful API + WebSocket实时通信
2. **并发控制**：Redis分布式锁确保数据一致性
3. **性能优化**：批量操作、缓存策略、连接复用
4. **用户体验**：实时状态更新、友好的错误提示
5. **系统可靠性**：完整的异常处理和重试机制

这种架构设计能够支持大规模用户同时购票，保证系统的稳定性和性能。

### 2.3 订单管理与支付

#### 功能描述
- 订单创建与状态管理
- 多种支付方式支持
- 订单超时自动取消
- 支付回调处理

#### 工作流程
```
订单创建 → 支付选择 → 支付处理 → 状态更新 → 座位状态同步 → 订单完成
```

#### 支付路径
1. **立即支付路径**：确认选座后直接支付
2. **稍后支付路径**：订单创建后30分钟内完成支付

#### 技术实现
```java
// 订单支付服务
@Service
public class OrderServiceImpl implements OrderService {
    
    @Override
    public ResponseVo payOrder(String orderId, String paymentMethod) {
        // 分布式锁防止并发支付
        String lockKey = "pay_order_" + orderId;
        String lockValue = UUID.randomUUID().toString();
        
        if (!redisDistributedLock.tryLock(lockKey, lockValue, 3, 100)) {
            return ResponseVo.error(400, "系统繁忙，请稍后再试");
        }
        
        try {
            // 更新座位状态为已售出
            seatService.updateSeatStatusBatch(seatIds, "SOLD", userId);
            
            // 更新订单状态
            order.setPaymentStatus("SUCCESS");
            order.setOrderStatus("PAID");
            orderDao.updateById(order);
            
            // 清理锁定信息
            redisUtil.delete("lock_order_" + order.getLockOrderId());
            
        } finally {
            redisDistributedLock.unlock(lockKey, lockValue);
        }
    }
}
```

### 2.4 高并发处理

#### 功能描述
- 分布式锁控制并发访问
- 消息队列异步处理
- 缓存优化性能
- 限流保护系统

#### 并发控制策略
1. **Redis分布式锁**：确保座位锁定的原子性
2. **消息队列**：异步处理订单创建和支付
3. **缓存策略**：减少数据库访问压力
4. **限流机制**：防止恶意请求

## 3. 技术架构

### 3.0 架构概述

本票务系统采用**分层架构设计**，基于Spring Boot 2.7.15构建，融合了微服务架构思想、高并发处理机制和实时通信技术，是一个功能完善、性能优越的在线票务平台。

#### 设计理念
- **高可用性**：通过分布式锁、消息队列和缓存策略，确保系统在高并发场景下稳定运行
- **可扩展性**：模块化设计，支持服务拆分和水平扩展，轻松应对流量增长
- **安全性**：多层安全防护，包括JWT认证、SM2国密加密、数据脱敏等技术
- **实时性**：WebSocket长连接技术，实现座位状态毫秒级同步
- **用户体验**：双登录方式、智能座位选择、实时状态更新，提供流畅的购票体验

#### 核心设计特点
1. **前后端分离**：前端采用原生JavaScript+HTML5+CSS3，后端提供RESTful API和WebSocket服务
2. **缓存优先**：Redis作为主要缓存层，减轻数据库压力，提升系统响应速度
3. **异步处理**：RabbitMQ消息队列处理订单超时等异步任务，提升系统吞吐量
4. **状态同步**：WebSocket实时推送座位状态变化，保证多客户端一致性
5. **安全加固**：Spring Security + JWT双重认证，SM2国密算法加密传输，全方位保障数据安全

### 3.1 系统架构图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端界面       │    │   API网关       │    │   服务注册中心   │
│   (HTML/CSS/JS) │◄──►│  (Spring Cloud) │◄──►│   (Nacos)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   微服务集群                                 │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐ │
│  │ 用户服务   │  │ 订单服务   │  │ 座位服务   │  │ 支付服务   │ │
│  └───────────┘  └───────────┘  └───────────┘  └───────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   缓存层         │    │   消息队列       │    │   数据存储层     │
│   (Redis)       │    │  (RabbitMQ)     │    │   (MySQL)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 3.2 技术栈

#### 后端技术栈
- **框架**: Spring Boot 2.7.15 + Spring Cloud
- **安全**: Spring Security + JWT
- **ORM**: MyBatis-Plus
- **缓存**: Redis
- **消息队列**: RabbitMQ
- **数据库**: MySQL 8.0
- **构建工具**: Maven

#### 前端技术栈
- **技术**: 原生JavaScript + HTML5 + CSS3
- **通信**: WebSocket + RESTful API
- **样式**: 响应式设计

#### 部署与运维
- **容器化**: Docker
- **服务发现**: Nacos
- **监控**: Spring Boot Actuator

### 3.3 数据库设计

#### 核心表结构

**用户表 (user)**
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    create_time DATETIME,
    update_time DATETIME
);
```

**场次表 (session)**
```sql
CREATE TABLE session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    session_time TIME NOT NULL,
    venue VARCHAR(100) NOT NULL,
    total_seats INT NOT NULL,
    remaining_seats INT NOT NULL,
    create_time DATETIME,
    update_time DATETIME
);
```

**座位表 (seat)**
```sql
CREATE TABLE seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    row_number INT NOT NULL,
    seat_number INT NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL, -- AVAILABLE, LOCKED, SOLD
    lock_user_id BIGINT,
    lock_time DATETIME,
    create_time DATETIME,
    update_time DATETIME
);
```

**订单表 (order)**
```sql
CREATE TABLE `order` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(50) UNIQUE NOT NULL,
    lock_order_id VARCHAR(50),
    user_id BIGINT NOT NULL,
    user_phone VARCHAR(20) NOT NULL,
    session_id BIGINT NOT NULL,
    seat_info TEXT NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20),
    payment_status VARCHAR(20), -- PENDING, SUCCESS, FAILED
    order_status VARCHAR(20), -- PENDING_PAYMENT, PAID, CANCELLED
    create_time DATETIME,
    expire_time DATETIME,
    pay_time DATETIME
);
```

## 4. 功能亮点

### 4.1 高并发座位锁定

#### 创新解决方案
- **分布式锁机制**: 使用Redis实现原子性座位锁定
- **锁定时效控制**: 30分钟自动释放，避免资源浪费
- **状态实时同步**: WebSocket实现多客户端状态一致性

#### 竞争优势
- **零冲突**: 分布式锁确保同一座位不会被重复锁定
- **高性能**: Redis内存操作，毫秒级响应
- **可扩展**: 支持水平扩展，应对流量高峰

### 4.2 智能订单管理

#### 独特能力
- **双路径支付**: 支持立即支付和稍后支付两种模式
- **自动超时处理**: 延迟队列实现订单超时自动取消
- **状态一致性**: 确保订单状态与座位状态完全同步

#### 技术优势
```java
// 延迟队列实现订单超时
public void sendDelayMessage(OrderMessageVo orderMessageVo, long delayMillis) {
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.DELAY_EXCHANGE,
        RabbitMQConfig.DELAY_ROUTING_KEY,
        message,
        messagePostProcessor -> {
            messagePostProcessor.getMessageProperties().setExpiration(String.valueOf(delayMillis));
            return messagePostProcessor;
        }
    );
}
```

### 4.3 实时通信系统

#### 创新特性
- **WebSocket长连接**: 实现座位状态实时推送
- **广播机制**: 多用户状态同步更新
- **断线重连**: 自动恢复连接，保证用户体验

#### 实现细节
```java
// WebSocket处理器
@Component
public class SeatStatusWebSocketHandler extends TextWebSocketHandler {
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 连接建立逻辑
        sessionMap.put(sessionId, session);
    }
    
    public void broadcastSeatStatus(String sessionId, List<Map<String, Object>> seatStatuses) {
        // 广播座位状态更新
        for (WebSocketSession session : getSessionsBySessionId(sessionId)) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(jsonMessage));
            }
        }
    }
}
```

## 5. 实现细节

### 5.1 核心算法

#### 座位选择算法
```java
// 座位状态验证算法
public boolean validateSeatSelection(List<Long> seatIds, Long sessionId) {
    // 1. 检查选座数量限制
    if (seatIds.size() > maxSeatsPerOrder) {
        return false;
    }
    
    // 2. 检查座位状态
    for (Long seatId : seatIds) {
        Seat seat = seatService.getSeatById(seatId);
        if (seat == null || !"AVAILABLE".equals(seat.getStatus())) {
            return false;
        }
    }
    
    // 3. 检查请求频率
    return checkRateLimit(userId);
}
```

#### 分布式锁算法
```java
// Redis分布式锁实现
public boolean tryLock(String lockKey, String lockValue, long expireTime, long waitTime) {
    long end = System.currentTimeMillis() + waitTime;
    
    while (System.currentTimeMillis() < end) {
        // SETNX + EXPIRE 原子操作
        if (redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, TimeUnit.MILLISECONDS)) {
            return true;
        }
        
        try {
            Thread.sleep(100); // 短暂等待后重试
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    return false;
}
```

### 5.2 性能优化策略

#### 缓存策略
- **多级缓存**: Redis + 本地缓存
- **缓存预热**: 系统启动时预加载热点数据
- **缓存失效**: 智能过期策略，保证数据一致性

#### 数据库优化
- **索引优化**: 关键字段建立复合索引
- **分库分表**: 支持水平扩展
- **读写分离**: 主从架构提升性能

#### 代码优化
- **连接池**: HikariCP高性能连接池
- **异步处理**: @Async注解实现异步方法
- **批量操作**: 减少数据库交互次数

## 6. 使用指南

### 6.1 开发人员指南

#### 环境搭建
```bash
# 1. 克隆项目
git clone <repository-url>

# 2. 安装依赖
mvn clean install

# 3. 配置环境变量
cp application.yml.template application.yml

# 4. 启动服务
mvn spring-boot:run
```

#### API文档

**用户认证API**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
    "phone": "13800138000",
    "password": "password123"
}
```

**座位锁定API**
```http
POST /api/v1/seats/lock
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
    "sessionId": 1,
    "seatIds": [1, 2, 3],
    "userId": "123",
    "verificationCode": "1234"
}
```

**订单创建API**
```http
POST /api/v1/orders/create
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
    "lockOrderId": "LOCK_123456",
    "paymentMethod": "wechat",
    "totalPrice": 300.00
}
```

#### 测试指南

**单元测试**
```java
@SpringBootTest
class OrderServiceTest {
    
    @Autowired
    private OrderService orderService;
    
    @Test
    void testCreateOrder() {
        CreateOrderVo createOrderVo = new CreateOrderVo();
        // 设置测试数据
        
        ResponseVo response = orderService.createOrder(createOrderVo);
        
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }
}
```

**性能测试**
```bash
# 使用JMeter进行压力测试
jmeter -n -t ticket_system.jmx -l result.jtl
```

### 6.2 最终用户指南

#### 购票流程
1. **注册登录** → 完成用户认证
2. **选择场次** → 浏览可用演出
3. **选择座位** → 可视化座位图选择
4. **确认订单** → 选择支付方式
5. **完成支付** → 获取电子票

#### 支付选项
- **立即支付**: 确认选座后直接完成支付
- **稍后支付**: 30分钟内完成支付，支持订单列表继续支付

#### 常见问题

**Q: 座位锁定后可以保留多久？**
A: 座位锁定后保留30分钟，超时未支付将自动释放。

**Q: 支付失败怎么办？**
A: 支付失败后可以在订单列表重新发起支付。

**Q: 如何退票？**
A: 目前支持开演前24小时退票，具体规则请查看退票政策。

## 7. 未来增强路线图

### 7.1 短期目标 (3-6个月)

#### 功能增强
- [ ] **智能推荐系统**: 基于用户历史行为推荐演出
- [ ] **电子票务**: 支持二维码电子票和手机NFC
- [ ] **多语言支持**: 国际化界面和内容
- [ ] **移动端应用**: 原生iOS和Android应用

#### 技术优化
- [ ] **微服务拆分**: 进一步细化服务粒度
- [ ] **容器化部署**: 全面Docker化部署
- [ ] **监控告警**: 完善的系统监控体系
- [ ] **日志分析**: ELK日志分析平台

### 7.2 中期目标 (6-12个月)

#### 业务扩展
- [ ] **票务分销**: 支持代理商和分销商
- [ ] **会员体系**: 积分、等级、特权系统
- [ ] **营销工具**: 优惠券、促销活动管理
- [ ] **数据分析**: 用户行为分析和业务洞察

#### 技术创新
- [ ] **AI预测**: 基于机器学习的票务需求预测
- [ ] **区块链**: 票务防伪和溯源系统
- [ ] **边缘计算**: 分布式缓存和计算
- [ ] **Serverless**: 无服务器架构探索

### 7.3 长期愿景 (1-3年)

#### 生态建设
- [ ] **开放平台**: API开放给第三方开发者
- [ ] **行业标准**: 参与制定票务行业技术标准
- [ ] **国际化**: 支持全球票务市场
- [ ] **多元化**: 扩展到体育、剧院、展览等多领域

#### 技术前沿
- [ ] **量子计算**: 探索量子计算在票务中的应用
- [ ] **元宇宙**: 虚拟现实票务体验
- [ ] **5G应用**: 利用5G技术提升用户体验
- [ ] **可持续发展**: 绿色计算和碳足迹优化

## 8. 附录

### 8.1 技术规范

#### 代码规范
- **命名规范**: 遵循Java命名约定
- **注释规范**: 必要的注释和文档
- **测试规范**: 测试覆盖率要求
- **安全规范**: 安全编码实践

#### 部署规范
- **环境隔离**: 开发、测试、生产环境分离
- **版本管理**: 语义化版本控制
- **回滚策略**: 快速回滚机制
- **备份策略**: 数据备份和恢复

### 8.2 参考资料

#### 技术文档
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [Redis官方文档](https://redis.io/documentation)
- [RabbitMQ官方文档](https://www.rabbitmq.com/documentation.html)
- [MyBatis-Plus文档](https://baomidou.com/)

#### 行业标准
- RESTful API设计规范
- 微服务架构最佳实践
- 高并发系统设计模式
- 信息安全技术标准

---

**文档版本**: v1.0  
**最后更新**: 2026-02-25  
**维护团队**: 票务系统开发团队  
**联系方式**: dev-team@ticketing.com