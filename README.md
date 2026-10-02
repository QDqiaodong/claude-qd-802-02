# 布草洗涤厂 · 收送与洗涤批次管理

面向酒店 / 医院布草的收送、分拣、洗涤、烘干烫平、配送闭环管理系统。

## 技术栈

- 后端：Spring Boot 3.3.0 + Java 17 + **MyBatis 注解方式**（主）+ Spring Data JPA（仅操作日志辅助）+ MySQL 8 + Redis
- 前端：Vue 3 + Element Plus + Vite
- 端口：前端 8282 / 后端 8382 / MySQL 3582 / Redis 6582
- 库名：`linen_laundry`

## 启动

```bash
./start.sh            # 镜像已存在时直接起
./start.sh --build    # 首次或代码变更后构建并启动
./stop.sh             # 暂停（保留容器）
./resume.sh           # 恢复
./clean.sh            # 收摊（down -v，含匿名卷）
```

## 业务主线

客户网点 → 收送单 → 洗涤批次 → 工序流转

1. **客户网点**：网点名称、联系人、结算方式、月结额度、脏布草待收量预警
2. **布草品类台账**：床单 / 被套 / 枕套 / 浴巾 / 台布 / 工服 / 手术衣 / 病号服，含规格、单价、在库数、报废率、最低周转量
3. **收送单**：收发时间、脏件数、净件数、司机、车牌、异常短少记录与赔付
4. **洗涤批次与工序流转**：预洗 → 主洗 → 漂洗 → 中和 → 脱水 → 烘干 → 烫平 → 折叠，含洗涤剂用量与水温
5. **洗涤配方版本台**：按品类维护八道工序目标水温 / 洗涤剂用量 / 适用设备类型；草稿可反复调整，发布前校验八道工序完整、参数合法、至少一台可用设备可承接；发布后不可原地改写，只能复制生成新版本；每个品类同时只有一个当前版本（并发发布只生效一份，另一份得到明确冲突结果）；停用当前版本必须先选定接替版本；建批时冻结批次配方快照，在产批次不受后来发布 / 停用影响；排产同时受设备故障、设备容量、版本设备类型与最大装载比例限制；推进工序实际值超出快照容差转待复核，主管填原因后按本批次放行或退回重做
6. **水洗机 / 烘干机设备**：型号、容量、状态（运行 / 待机 / 故障）、运行时长、故障报修与维修单

## 页面与操作范式（顶部双排 Tab）

| 分组 | 子功能 | 操作范式 |
| --- | --- | --- |
| 收送 | 网点地图热区 | SVG 平面示意图按网点摆色块，颜色深浅 = 当日待收脏布草量，点色块右侧抽屉登记收发件数 |
| 收送 | 收送单台账 | 表格 + 明细行展开 + 送净登记 |
| 分拣 | 布草品类盘点 | 折叠台账 + 卡片上 −/+ 步进器改在库数，底部即时显示差异数与损益金额 |
| 洗涤 | 批次隧道流转 | 批次卡片按工序横排成隧道，卡片底部唯一「下一工序」按钮，推进后整体左移动画；卡片显示配方版本，推进弹窗录入实际水温 / 洗涤剂并对照快照标准，超差转待复核由主管放行或退回重做 |
| 洗涤 | 洗涤配方版本台 | 按品类列出版本时间线（草稿 / 当前生效 / 已停用），行展开看八道工序标准；草稿表格化编辑八道工序参数，发布 / 复制 / 停用（停用必须选接替版本） |
| 洗涤 | 医院布草污染追溯 | 登记污染事件（设备 / 发现时间 / 影响起止 / 类型）→ 确认按设备占用时段命中批次并隔离 → 逐批复洗（生成可追溯新批次）→ 全部完成后复核放行；事件详情逐条解释命中原因 |
| 后整理 | 设备状态灯墙 | 深底圆灯墙（运行绿 / 待机灰 / 故障红），点灯轮转状态，故障灯弹维修单表单；有未放行污染事件的设备亮 ☣ 角标并列出污染来源与待处置数量 |
| 配送 | 配送调度看板 | 按司机 / 车牌聚合收送量与送达率 |

## 数据访问

- MyBatis 注解方式：`@Select` / `@Insert` / `@Update` / `@Delete` 手写 SQL（含 join、聚合统计、条件判断）
- 嵌套映射：`@Results` + `@ResultMap` + `@Many`（收送单 → 明细行、批次 → 工序流转记录、设备 → 维修单、网点 → 最近收送单、品类 → 近期批次）与 `@One`（收送单 → 网点、批次 → 品类 / 设备）
- 动态条件：`@SelectProvider`（网点检索、收送单检索、批次隧道视图）
- Spring Data JPA：`OperationLogRepository` 负责车间操作日志落库

## 业务规则（service 层抛 BizException）

- 净件数不得大于脏件数；短少必须登记赔付金额
- 批次推进工序必须按顺序，不能跳步；最后一道折叠完成后批次收口并回补在库
- 设备故障状态不允许排产；批次重量不得超过设备容量，且不得超过配方版本最大装载比例；设备类型必须在配方版本可承接类型内
- 排产必须引用品类当前生效配方版本；建批时冻结配方快照（wash_batch.recipe_snapshot），在产批次按快照执行，不受后来发布 / 停用影响
- 配方版本：仅草稿可编辑 / 删除；发布前校验八道工序完整、参数范围合法、至少一台非故障设备类型匹配；发布后不可原地改写，只能复制生成新版本；每品类同时只有一个当前版本（uk_recipe_current 唯一索引 + 品类行锁 + 草稿基线比对，并发发布只生效一份，另一份报冲突）；停用当前版本必须先选定接替版本（草稿接替须先通过发布校验）
- 推进工序时实际水温 / 洗涤剂超出批次快照容差：批次转 REVIEW 待复核并落复核记录，不直接前进；主管填写原因后按本批次放行（按原推进意图继续）或退回重做（回到在产重新执行），结论只影响该批次
- 在库数低于最低周转量给出缺口数；在库数不得为负数
- 污染隔离批次（QUARANTINED）无论停在哪道工序都不能推进或按正常完工处理，也不能删除；必须发起复洗
- 复洗必须生成可追溯新批次（wash_batch.rewash_order_id 关联复洗单，复洗单关联原批次）；同一隔离批次至多一份有效复洗
- 污染事件仅在全部关联复洗批次完成（无在产 / 失败 / 删除复洗）后才能复核放行；放行解除批次隔离
- 污染命中判定：批次在 `batch_step.machine_id` 上的工序占用区间与事件影响时段重叠（进行中工序按持续到当前）；批次仅绑定设备无工序重叠时兜底命中

## 接口

统一 `/api/xxx` 前缀，返回 `{ok, message, data}`。

- `GET /api/dashboard/overview`、`GET /api/dashboard/logs`
- `GET /api/outlets`、`GET /api/outlets/{id}`、`POST /api/outlets/{id}/pickup`、`GET /api/outlets/{id}/orders`
- `GET /api/categories`、`GET /api/categories/shortage`、`PUT /api/categories/{id}/stock?delta=10`、`POST /api/categories/count`
- `GET /api/orders`、`POST /api/orders`、`POST /api/orders/{id}/deliver`、`GET /api/orders/dispatch-board`
- `GET /api/batches`、`GET /api/batches/steps`、`GET /api/batches/step-stats`、`POST /api/batches`、`POST /api/batches/{id}/advance`、`POST /api/batches/reviews/{reviewId}/resolve`（偏差复核：放行 / 退回重做）
- 洗涤配方版本台（`/api/recipes`）：`GET /recipes?categoryId=`、`GET /recipes/current?categoryId=`、`GET /recipes/{id}`、`POST /recipes/drafts`、`PUT /recipes/drafts/{id}`、`DELETE /recipes/drafts/{id}`、`POST /recipes/{id}/copy`、`POST /recipes/{id}/publish`、`POST /recipes/{id}/disable`
- `GET /api/machines`、`POST /api/machines/{id}/next-status`、`POST /api/machines/{id}/repairs`、`POST /api/machines/repairs/{id}/finish`
- 医院布草污染追溯闭环（`/api/contamination`）：
  - `GET /contamination/types`、`GET /contamination/events`、`GET /contamination/events/{id}`
  - `POST /contamination/events`（登记）、`POST /contamination/events/{id}/confirm`（确认命中并隔离）
  - `POST /contamination/events/{eventId}/isolations/{isolationId}/rewash`（发起复洗，生成新批次）
  - `POST /contamination/rewash/{rewashId}/fail`、`POST /contamination/rewash/{rewashId}/delete`
  - `POST /contamination/events/{id}/release`（复核放行）

## 污染追溯并发一致性

不靠前端隐藏按钮，状态一致性由数据库 + 行锁保证：

1. 确认事件 / 发起复洗 / 复核放行均先 `SELECT … FOR UPDATE` 锁定事件（或隔离记录），串行化同资源并发请求；
2. 事件状态机 DRAFT→CONFIRMED→RELEASED 用 `UPDATE … WHERE status=?` CAS 守卫，重复请求走幂等返回；
3. `batch_isolation UNIQUE(event_id, batch_id)` 兜底不产生重复隔离记录；
4. `rewash_order` 用存储生成列 `active_flag`（ACTIVE/DONE 为 1，否则 NULL）+ `UNIQUE(isolation_id, active_flag)`，保证同一隔离批次至多一份有效复洗；
5. 批次推进先锁批次行并校验隔离态；放行前逐批复核复洗结果，任一在产 / 失败 / 删除都带原因拒绝。
