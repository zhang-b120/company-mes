# company-mes（AI-MES 智造云工单）

公司内部自研 MES，覆盖销售、采购、生产、库存、质量、财务、设备、外协、报工等全流程，并提供 AI 助手与车间大屏。

 实例（库名、接口前缀、上传目录均带 ``）。换客户部署时，改后端 `application.yml` 与前端 `.env` / `vite.config.js` 中的对应前缀即可。

---

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.6、MySQL 8、JWT、Knife4j、Caffeine、Hutool、Apache POI / PDFBox |
| 前端 | Vue 3.4、Vite 5、Element Plus、Pinia、Vue Router、Axios、ECharts、Vue Flow、hiprint |
| AI | DeepSeek Chat API（订单解析、排产建议、自然语言查询） |

---

## 仓库结构

```
company-mes/
├── README.md
├── ai-mes-server/          # 后端（Spring Boot）
└── ai-mes-web/             # 前端（Vue 3 + Vite）
```

前后端独立工程，无父级 Maven / npm workspace。本地开发时分别启动即可。

---

## 后端 `ai-mes-server`

```
ai-mes-server/
├── pom.xml
└── src/main/
    ├── java/com/aimes/
    │   ├── AiMesApplication.java          # 启动类（含定时任务、Mapper 扫描）
    │   ├── common/                        # 公共能力
    │   │   ├── annotation/                # 数据权限注解 @DataScope
    │   │   ├── aspect/                    # 数据权限切面
    │   │   ├── cache/                     # 名称缓存
    │   │   ├── columnconfig/              # 表格列配置
    │   │   ├── config/                    # WebMvc / CORS / JWT / MyBatis / Swagger / 缓存
    │   │   ├── controller/                # 通用接口（上传等）
    │   │   ├── entity/                    # BaseEntity
    │   │   ├── event/                     # 审批完成 / 驳回事件
    │   │   ├── exception/                 # 业务异常与全局处理
    │   │   ├── extfield/                  # 业务扩展字段
    │   │   ├── interceptor/               # JWT 认证、数据权限 SQL 拦截
    │   │   ├── result/                    # 统一返回 R / PageResult
    │   │   └── utils/                     # JWT、编码生成、Excel、单位换算等
    │   └── modules/                       # 业务模块（controller / entity / mapper / service）
    └── resources/
        ├── application.yml                # 端口、数据库、JWT、AI、上传目录
        ├── schema.sql                     # 全量表结构（biz_ + sys_）
        ├── data.sql                       # 系统初始化数据（菜单、角色、默认管理员等）
        ├── biz_material.sql               # 物料相关补丁
        ├── material_dedup_fix.sql         # 物料去重修复
        └── gen-qc-task-by-workorder.sql   # 按工单补质检任务
```

### 业务模块

| 目录 | 说明 |
| --- | --- |
| `auth` | 登录、刷卡登录、Token 刷新、改密 |
| `system` | 用户 / 角色 / 菜单 / 部门 / 仓库 / 业务配置 / 打印模板 / 通知 |
| `master` | 客户、供应商、物料、工序、计量单位 |
| `bom` | BOM 版本、工艺路线 |
| `order` | 销售订单、发货、退货、发票、订单变更 |
| `purchase` | 采购申请、采购订单、收料、退货、分析 |
| `production` | 工单、排产、工序进度、返修 |
| `mrp` | MRP 运算、主计划、生产建议 |
| `inventory` | 库存台账、入出库申请、任务、调拨、盘点、标签 |
| `quality` | 质检项目 / 模板 / 方案 / 任务 / 记录 / 缺陷 / 追溯 / 废料 |
| `workreport` | 报工、异常、计件工资 |
| `team` | 班组 |
| `outsource` | 外协申请、加工单、发料、收料 |
| `finance` | 应收应付、往来现金、发票、成本、凭证 |
| `eam` | 设备、点检、保养、维修 |
| `aftersale` | 售后工单 |
| `approval` | 审批流程、实例、记录 |
| `dashboard` | 经营看板、TV 大屏 |
| `analysis` | 客户看板、销售看板 |
| `ai` | DeepSeek 对话、接单解析、排产 |
| `dataimport` | Excel 导入、演示数据模拟 |

接口统一前缀 `/api/**`，JWT 拦截白名单：登录、刷卡登录、刷新 Token、TV 大屏、Knife4j 文档。

---

## 前端 `ai-mes-web`

```
ai-mes-web/
├── .env                    # 接口 / 上传资源前缀
├── vite.config.js          # 开发代理、部署 base 路径
├── package.json
├── index.html
└── src/
    ├── api/                # 按模块封装的 Axios 请求
    ├── assets/             # 全局样式
    ├── components/         # 公共组件（审批进度、附件、列配置等）
    ├── composables/        # 组合式函数
    ├── layout/             # 主布局、侧栏、页签
    ├── router/             # 静态路由 + 按菜单动态挂载
    ├── store/              # Pinia（用户、页签、工位机、AI 会话）
    ├── utils/              # 请求封装、权限指令、格式化、打印
    ├── views/              # 页面（与后端模块一一对应）
    ├── App.vue
    └── main.js
```

### 页面目录

| 目录 | 说明 |
| --- | --- |
| `login` | 管理端登录 |
| `reportLogin` / `workstation` | 工位机刷卡 / 账号报工 |
| `system` | 系统管理、业务配置、数据导入、打印模板 |
| `master` / `bom` | 基础数据、BOM、工艺路线 |
| `order` / `purchase` / `production` / `mrp` | 销售、采购、生产、计划 |
| `inventory` / `quality` / `workreport` / `team` | 库存、质量、报工、班组 |
| `outsource` / `finance` / `eam` / `aftersale` | 外协、财务、设备、售后 |
| `dashboard` / `analysis` / `approval` / `ai` | 看板、分析、审批、AI 工作台 |
| `tv` / `tv2` / `tvModules` | 车间 / 模块大屏（免登录） |

菜单由后端 `sys_menu` 下发，前端按 `component` 路径动态加载 `views/**/index.vue`。

---

## 环境要求

| 项 | 版本 / 说明 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（建议 20） |
| MySQL | 8.0+，字符集 `utf8mb4` |
| 操作系统 | 开发常见 Windows；上传目录需本机可写 |

---

## 账号与密码

### 系统登录

初始化脚本 `data.sql` 会写入超级管理员：

| 项 | 值 |
| --- | --- |
| 账号 | `admin` |
| 密码 | `123456` |
| 角色 | 超级管理员（`admin`，数据权限 `all`） |
| 手机 | `13800000001` |

说明：

- 新建用户未填密码时，默认也是 `123456`。
- 员工管理里「重置密码」会改回 `123456`。
- Excel 导入用户时，默认密码同样是 `123456`，登录后自行修改。
- 密码使用 BCrypt 存储，不在库中明文保存。

预置角色：`admin` / `boss` / `production_manager` / `sales` / `warehouse` / `finance` / `quality` / `worker` / `teamleader`。

工位机可通过 IC 卡号或账号走 `/api/auth/card-login` 免密登录（需先在员工档案绑定卡号）。

### 数据库

当前 `application.yml` 连接的是库：

本地自建库时，改 `spring.datasource.*`，不要直接用生产库做破坏性测试。

### JWT

| 项 | 值 | 说明 |
| --- | --- | --- |
| `jwt.secret` | `ai-mes-secret-key-must-be-at-least-32-characters-long` | 签名密钥，至少 32 位；生产务必更换 |
| `jwt.access-token-expire` | `7200000` | Access Token 2 小时 |
| `jwt.refresh-token-expire` | `604800000` | Refresh Token 7 天 |

请求头：`Authorization: Bearer <token>`。


未配有效 Key 时，AI 接单 / 对话 / 排产不可用，其余业务不受影响。

---

## 配置说明

### 后端 `ai-mes-server/src/main/resources/application.yml`

```yaml
server.port: 8083                          # 后端端口，无 context-path

spring.datasource.url                      # MySQL JDBC
spring.datasource.username / password      # 库账号

jwt.secret / access-token-expire / refresh-token-expire

ai.deepseek.api-key / base-url / model / timeout / max-tokens

mrp.schedule.enabled: false                # 夜间 MRP 定时，默认关
mrp.schedule.cron: "0 0 2 * * ?"           # 每天 02:00

file.upload.path: E:/produce/file/uploads-   # 本机上传目录，必须存在且可写
file.upload.url-prefix: /uploads-            # 对外访问前缀
```

换客户时通常要改 4 处：库名、上传目录、`url-prefix`，以及前端对应前缀。

其它约定：

- 逻辑删除字段 `deleted`（1 删除 / 0 正常），主键自增。
- 上传单文件上限 50MB，整次请求 100MB。
- CORS 已放开，开发可直接跨域调 `8083`。
- Knife4j 文档：<http://localhost:8083/doc.html>

### 前端 `ai-mes-web/.env`

```env
VITE_API_BASE=/api-
VITE_UPLOADS_BASE=/uploads-
```

开发代理在 `vite.config.js`：

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `VITE_API_BASE` | `/api` | 浏览器访问的接口前缀；代理时会剥掉客户后缀，转到后端 `/api` |
| `VITE_UPLOADS_BASE` | `/uploads` | 上传文件访问前缀 |
| `VITE_BACKEND_TARGET` | `http://localhost:8083` | 代理目标，可在本地 `.env` 覆盖 |

当前部署路径：

| 项 | 值 |
| --- | --- |
| 前端 `base` | `/mes-front-/` |
| 开发端口 | `5173`（`0.0.0.0`，局域网可访问） |
| 接口代理 | `/api-` → `http://localhost:8083/api` |
| 上传代理 | `/uploads-` → 后端上传目录 |

生产一般由 Nginx 做同样的前缀剥离：浏览器走 `/api-`、`/uploads-`、`/mes-front-/`，后端只认 `/api` 和 `/uploads`。

---

## 数据库初始化

1. 创建库（示例）：

```sql
CREATE DATABASE ai_mes_
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

2. 建表：执行 `ai-mes-server/src/main/resources/schema.sql`。
3. 初始化菜单、角色、管理员：执行 `data.sql`。
4. 按需执行同目录下的补丁 SQL（物料、质检任务等）。

`schema.sql` 含 `DROP TABLE`，会对空库或可重建库使用；已有业务数据的库不要直接全量跑。

---

## 本地启动

### 1. 后端

确认 JDK 17、MySQL 可达，并已建好库。Windows 下先建上传目录，例如 `E:\produce\file\uploads-`（与 `file.upload.path` 一致）。

```bash
cd ai-mes-server
mvn spring-boot:run
```

启动后：

- API：<http://localhost:8083/api>
- 文档：<http://localhost:8083/doc.html>

### 2. 前端

```bash
cd ai-mes-web
npm install
npm run dev
```

浏览器打开：<http://localhost:5173/mes-front-/>

账号 `admin` / 密码 `123456`。

其它命令：

```bash
npm run build      # 产出 dist，部署到 Nginx 的 /mes-front-/
npm run preview    # 本地预览构建结果
```

### 3. 启动顺序

先后端 `8083`，再前端 `5173`。前端只通过 Vite 代理访问后端，不直接写死 IP。

---

## 多客户前缀约定

同一套代码可按客户拆库、拆上传目录、拆前端路径：

| 角色 | 前端 base | 接口前缀 | 上传前缀 | 库名示例 |
| --- | --- | --- | --- | --- |
| （当前仓库） | `/mes-front-/` | `/api-` | `/uploads-` | `ai_mes_` |

换客户时同步改：

1. 后端 `spring.datasource.url`、`file.upload.path`、`file.upload.url-prefix`
2. 前端 `.env` 的 `VITE_API_BASE`、`VITE_UPLOADS_BASE`
3. 前端 `vite.config.js` 的 `base`

后端 Controller 始终使用 `/api/**`，不要把客户名写进 Java 代码。

---

## 常见问题

**连不上库**  
检查 `application.yml` 的 IP、库名、账号密码，以及本机到 `3306` 是否放行。

**上传 404 或失败**  
`file.upload.path` 必须是真实存在的目录；开发环境还要保证 `VITE_UPLOADS_BASE` 与 `url-prefix` 一致。

**登录 401 / Token 无效**  
确认走的是 `/api-` → `/api` 代理，且请求头带 `Bearer`。改过 `jwt.secret` 后，旧 Token 会全部失效。

**AI 功能报错**  
检查 `DEEPSEEK_API_KEY` 或配置里的 `ai.deepseek.api-key` 是否有效。
