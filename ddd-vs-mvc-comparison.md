# DDD 分层 vs 传统 MVC 分层：深度对比分析

本文档将以你熟悉的**传统 MVC 框架**（苍穹外卖结构）作为切入点，对比本项目采用的**DDD（领域驱动设计）分层**模式，帮你快速理解 DDD 的核心优势。

---

## 1. 架构结构对比

### 🛠️ 传统 MVC 架构 (苍穹外卖)
这是一种典型的**“按技术分层”**的架构模式，将代码按“Controller -> Service -> Mapper”纵向切开。

```text
项目模块划分：
├── sky-common      # 通用工具包（常量、异常、JWT、Result等）
├── sky-pojo        # 数据结构定义包（DTO、VO、Entity）
└── sky-server      # 核心业务模块
    ├── config      # 配置类（拦截器、Redis、MQ等）
    ├── controller  # 控制层（接收请求）
    ├── service     # 业务层（核心逻辑所在地，极其臃肿！）
    │   └── impl    # Service实现类
    └── mapper      # 数据访问层（继承BaseMapper）
```

### 🏗️ DDD 分层架构 (本项目)
这是一种典型的**“按业务领域分层”**的架构模式，将代码按“限界上下文 -> 应用层 -> 领域层 -> 基础设施层”横向切开。

```text
项目模块划分：
├── bootstrap       # 启动引导模块
├── common          # 通用内核（常量、异常、认证等）
├── lot             # 批次管理限界上下文 (Bounded Context)
├── inventory       # 库存管理限界上下文
└── alert           # 预警管理限界上下文

【每个限界上下文内部严格遵循四层架构】：
├── api             # 接口层 (对外暴露REST API)
├── application     # 应用层 (业务用例编排)
├── domain          # 领域层 (核心业务规则，纯Java POJO)
└── infrastructure  # 基础设施层 (技术实现细节，如MyBatis、Redis操作)
```

---

## 2. 核心概念对比

| 对比维度 | 传统 MVC (苍穹外卖) | DDD 分层 (本项目) | 核心区别 |
| :--- | :--- | :--- | :--- |
| **核心对象** | `Entity` (贫血模型) | `Aggregate Root` (聚合根/充血模型) | MVC的Entity只是数据库表的映射；DDD的聚合根**封装了所有业务规则**。 |
| **业务逻辑归属** | `ServiceImpl` (大杂烩) | `Application` + `Domain` 层 | MVC的Service包罗万象；DDD将业务规则内聚在领域对象中。 |
| **数据状态管理** | 任意时刻修改Entity字段 | 只能通过聚合根方法修改 | DDD强制通过`lot.outbound()`修改状态，防止非法操作。 |
| **依赖方向** | Controller依赖Service，Service依赖Mapper | 依赖倒置：Domain层定义接口，Infrastructure层实现 | DDD的领域层**零框架依赖**，可以不启动Spring直接测试。 |

---

## 3. 逐点优势解析 (从MVC视角看DDD)

### 优势一：告别“上帝 Service”，业务逻辑从此内聚

#### 🚨 MVC 的痛点
在苍穹外卖中，`EmployeeServiceImpl` 可能长这样：
```java
// 一个方法里可能包含200行代码
public void login(EmployeeLoginDTO dto) {
    // 1. 调用Mapper查询数据库
    Employee emp = employeeMapper.getByUsername(dto.getUsername());
    // 2. 一堆if-else逻辑校验
    if (emp == null) throw new BaseException("用户不存在");
    if (!dto.getPassword().equals(emp.getPassword())) throw new BaseException("密码错误");
    // 3. 调用JWT工具包生成Token
    String token = JwtUtil.createToken(emp.getId());
    // 4. 转换VO返回给前端
    EmployeeLoginVO vo = new EmployeeLoginVO();
    // ... 更多逻辑
}
```
**问题**：随着业务发展，`ServiceImpl` 会变成一个“大泥球”，任何业务逻辑都往里面塞，难以维护。

#### ✅ DDD 的解法
在 DDD 中，业务规则被封装在**聚合根（Aggregate Root）**内部。

**1. 领域层 (`Lot.java`)**
```java
// 出库的所有规则都在聚合根内部，外部无法绕过
public void canOutbound(BigDecimal qty, TempZone targetZone) {
    // 规则1: 温区必须匹配
    if (this.tempZone != targetZone) {
        throw new TempZoneMismatchException(...);
    }
    // 规则2: 库存必须充足
    if (qty.compareTo(this.remainingQty) > 0) {
        throw new InsufficientQtyException(...);
    }
    // 规则3: 批次不能过期
    if (LocalDate.now().isAfter(this.expireDate)) {
        throw new ExpiredException(...);
    }
}
```

**2. 应用层 (`LotAppService.java`)**
```java
// 应用层只负责“编排”，不包含具体规则
public LotOutboundResult outbound(OutboundCommand cmd) {
    // 1. 从数据库加载聚合根
    Lot lot = lotRepository.findById(cmd.skuId(), cmd.tempZone())...;
    // 2. 调用聚合根方法 (规则已封装好)
    lot.canOutbound(cmd.qty(), cmd.tempZone());
    LotTransfer transfer = lot.outbound(cmd.qty(), cmd.toLocation());
    // 3. 保存聚合根
    lotRepository.save(lot);
    // 4. 发送事件
    return new LotOutboundResult(...);
}
```
**优势**：修改业务规则只需改领域层的聚合根方法，应用层代码不用动，**开闭原则**得到践行。

---

### 优势二：彻底分离“变”与“不变”，领域层零框架依赖

#### 🚨 MVC 的痛点
在苍穹外卖中，Entity 充满了框架注解：
```java
@TableName("employee")
public class Employee {
    @TableId
    private Long id;
    
    @TableField("name")
    private String name;
    
    // 混杂了MyBatis注解、Lombok注解、业务逻辑...
}
```
**问题**：如果某天项目要从 MyBatis-Plus 切换到 JPA，或者要把核心业务逻辑抽出来做成独立 SDK，那么整个 Entity 类都要重写。

#### ✅ DDD 的解法
DDD 严格分离了**“领域对象”**和**“持久化对象”**。

**领域层的聚合根 (`Lot.java`)**
```java
// 纯Java代码，没有任何Spring、MyBatis注解
public class Lot {
    private final LotNo lotNo;  // 自定义类型，不是基础类型
    private final TempZone tempZone;
    
    // 业务逻辑
    public void canOutbound(...) { ... }
}
```

**基础设施层的持久化对象 (`LotPO.java`)**
```java
// 只有基础设施层才依赖框架
@Data
@TableName("t_lot")
public class LotPO {
    @TableId
    private String lotNo;
    
    @TableField("temp_zone")
    private String tempZone;
}
```

**优势**：领域层的代码可以**不启动 Spring 容器**，直接用 JUnit 进行单元测试。核心业务逻辑与技术框架（Spring、MyBatis、Redis）完全解耦。

---

### 优势三：清晰的对象转换边界，告别“到处转”

#### 🚨 MVC 的痛点
在苍穹外卖中，DTO -> Entity -> VO 的转换可能发生在**任何地方**：
```java
// 可能出现在Controller
@PostMapping
public void save(@RequestBody EmployeeDTO dto) {
    Employee emp = new Employee();
    BeanUtils.copyProperties(dto, emp); // 在Controller转了一次
    employeeService.save(emp);
}

// 可能出现在Service
public void save(Employee emp) {
    EmployeeVO vo = new EmployeeVO();
    BeanUtils.copyProperties(emp, vo); // 在Service又转了一次
}
```
**问题**：转换逻辑分散，容易漏转字段、写错字段，也不利于维护。

#### ✅ DDD 的解法
DDD 定义了非常清晰的转换边界，每个层的对象类型严格隔离。

| 层 | 对象类型 | 职责 |
| :--- | :--- | :--- |
| `api` 层 | `DTO` (Command) | 接收前端请求，只包含所需字段 |
| `application` 层 | `Command` | 应用层内部使用的命令对象 |
| `domain` 层 | `Entity` / `Aggregate` | 领域对象，核心业务载体 |
| `infrastructure` 层 | `PO` (Persistence Object) | 数据库表映射对象 |

**转换规则**：
1.  `DTO` -> `Command`：由 `Controller` 在方法内部转换。
2.  `Command` -> `Aggregate`：由 `Application` 层的工厂方法转换。
3.  `Aggregate` -> `PO`：由 `Repository` 实现类（在 `infrastructure` 层）转换。
4.  `PO` -> `Response`：由 `QueryService` 或 `Controller` 转换。

**优势**：每一种转换只有一个明确的地点，代码可读性和可维护性大幅提升。

---

### 优势四：按业务模块隔离，实现真正的“高内聚低耦合”

#### 🚨 MVC 的痛点
MVC 按“技术职责”分包，所有业务的代码揉在一起：
```text
sky-server
├── controller
│   ├── EmployeeController.java (员工管理)
│   ├── DishController.java (菜品管理)
│   └── OrderController.java (订单管理)
├── service
│   ├── impl
│   │   ├── EmployeeServiceImpl.java
│   │   ├── DishServiceImpl.java
│   │   └── OrderServiceImpl.java
│   └── ...
└── mapper
    ├── EmployeeMapper.java
    ├── DishMapper.java
    └── OrderMapper.java
```
**问题**：
1.  修改“订单”功能，可能会不小心影响到“菜品”功能（比如共用了一个工具类）。
2.  随着业务增多，包会越来越大，新人很难快速定位。

#### ✅ DDD 的解法
DDD 按**“业务限界上下文”**划分子模块，每个模块独立封装。

```text
fulfillment-platform
├── lot                 # 批次管理限界上下文 (独立模块)
│   ├── api             # 批次的API
│   ├── application     # 批次的应用服务
│   ├── domain          # 批次的聚合根 (Lot.java)
│   └── infrastructure  # 批次的持久化实现
├── inventory           # 库存管理限界上下文 (独立模块)
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── alert               # 预警管理限界上下文 (独立模块)
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
└── common              # 通用域 (独立模块)
```

**优势**：
1.  **彻底解耦**：`lot` 模块的改动**绝对不会**影响 `inventory` 模块。
2.  **团队协作**：多个小组可以同时开发不同的限界上下文（例如 A 组开发批次，B 组开发预警），互不干扰。
3.  **模块化部署**：未来甚至可以将每个限界上下文拆分为独立的微服务。

---

## 4. 总结：什么时候用 DDD？

DDD 并不是“银弹”，它是为**复杂业务场景**量身定制的架构。

| 场景 | 推荐架构 | 原因 |
| :--- | :--- | :--- |
| **CRUD 简单管理系统** (如后台管理) | 传统 MVC | 业务逻辑简单，DDD 会显得过度设计。 |
| **高度复杂的业务系统** (如本项目：农批履约、订单交易、ERP) | **DDD 分层** | 业务规则复杂，状态流转多，需要通过 DDD 保证业务的正确性和可维护性。 |

本项目选择 DDD，正是因为“农批履约”场景涉及到批次管理、库存锁定、并发防超卖、可靠事件投递等**复杂业务规则**，传统 MVC 的“Service 大泥球”模式将无法胜任长期迭代。
