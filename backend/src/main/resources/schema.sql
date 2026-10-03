-- ============================================================
-- 布草洗涤厂 · 收送与洗涤批次管理  数据库初始化脚本
-- 库名：linen_laundry     应用：Spring Boot 3.3 + MyBatis 注解方式
-- 说明：application.yml 中 ddl-auto: none，所有表必须由本脚本建好
-- ============================================================

CREATE DATABASE IF NOT EXISTS linen_laundry
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE linen_laundry;

SET NAMES utf8mb4;

-- ---------- 1. 客户网点（酒店 / 医院） ----------
CREATE TABLE IF NOT EXISTS outlet (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  code           VARCHAR(32)  NOT NULL                COMMENT '网点编号',
  name           VARCHAR(64)  NOT NULL                COMMENT '网点名称',
  outlet_type    VARCHAR(16)  NOT NULL                COMMENT 'HOTEL 酒店 / HOSPITAL 医院',
  district       VARCHAR(32)  NOT NULL                COMMENT '所属片区',
  contact        VARCHAR(32)  NOT NULL                COMMENT '联系人',
  phone          VARCHAR(32)  NOT NULL                COMMENT '联系电话',
  settle_type    VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY 月结 / COUNT 计件 / CASH 现结',
  credit_limit   DECIMAL(12,2) NOT NULL DEFAULT 0     COMMENT '月结额度（元）',
  dirty_pending  INT          NOT NULL DEFAULT 0      COMMENT '脏布草待收量（件）',
  warn_threshold INT          NOT NULL DEFAULT 0      COMMENT '待收预警阈值（件）',
  map_x          INT          NOT NULL DEFAULT 0      COMMENT '平面示意图横坐标（百分比）',
  map_y          INT          NOT NULL DEFAULT 0      COMMENT '平面示意图纵坐标（百分比）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 合作中 / PAUSED 暂停',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outlet_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户网点';

-- ---------- 2. 布草品类台账 ----------
CREATE TABLE IF NOT EXISTS linen_category (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(32)  NOT NULL                COMMENT '品类编号',
  name         VARCHAR(64)  NOT NULL                COMMENT '品类名称',
  spec         VARCHAR(64)  NOT NULL                COMMENT '规格',
  unit_price   DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '洗涤单价（元/件）',
  stock_qty    INT          NOT NULL DEFAULT 0      COMMENT '在库数（净布草件）',
  scrap_rate   DECIMAL(5,2) NOT NULL DEFAULT 0      COMMENT '报废率（%）',
  min_turnover INT          NOT NULL DEFAULT 0      COMMENT '最低周转量（件）',
  owner_type   VARCHAR(16)  NOT NULL DEFAULT 'COMMON' COMMENT 'HOTEL / HOSPITAL / COMMON',
  remark       VARCHAR(128)          DEFAULT NULL   COMMENT '备注（洗涤要点）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布草品类台账';

-- ---------- 3. 收送单 ----------
CREATE TABLE IF NOT EXISTS pickup_order (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(32)  NOT NULL                COMMENT '收送单号 RS+日期+序号',
  outlet_id    BIGINT       NOT NULL                COMMENT '网点 id',
  pickup_time  DATETIME              DEFAULT NULL   COMMENT '收脏时间',
  deliver_time DATETIME              DEFAULT NULL   COMMENT '送净时间',
  dirty_qty    INT          NOT NULL DEFAULT 0      COMMENT '脏件数',
  clean_qty    INT          NOT NULL DEFAULT 0      COMMENT '净件数',
  driver       VARCHAR(32)           DEFAULT NULL   COMMENT '司机',
  plate        VARCHAR(32)           DEFAULT NULL   COMMENT '车牌',
  shortage_qty INT          NOT NULL DEFAULT 0      COMMENT '短少件数',
  compensation DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '短少赔付金额（元）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PICKED' COMMENT 'PICKED 已收待送 / DELIVERED 已送达 / CLOSED 已结算',
  remark       VARCHAR(255)          DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_outlet (outlet_id),
  KEY idx_order_pickup (pickup_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收送单';

-- ---------- 4. 收送单明细行 ----------
CREATE TABLE IF NOT EXISTS pickup_order_line (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  order_id     BIGINT       NOT NULL                COMMENT '收送单 id',
  category_id  BIGINT       NOT NULL                COMMENT '布草品类 id',
  dirty_qty    INT          NOT NULL DEFAULT 0      COMMENT '该品类脏件数',
  clean_qty    INT          NOT NULL DEFAULT 0      COMMENT '该品类净件数',
  shortage_qty INT          NOT NULL DEFAULT 0      COMMENT '该品类短少件数',
  loss_amount  DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '短少赔付金额',
  PRIMARY KEY (id),
  KEY idx_line_order (order_id),
  KEY idx_line_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收送单明细';

-- ---------- 5. 洗涤批次 ----------
CREATE TABLE IF NOT EXISTS wash_batch (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  batch_no          VARCHAR(32)  NOT NULL            COMMENT '批次号 PC+日期+序号',
  category_id       BIGINT       NOT NULL            COMMENT '布草品类 id',
  weight_kg         DECIMAL(8,2) NOT NULL DEFAULT 0  COMMENT '重量 kg',
  current_step      VARCHAR(16)  NOT NULL            COMMENT '当前工序编码',
  current_step_order INT         NOT NULL DEFAULT 1  COMMENT '当前工序号 1~8',
  detergent_ml      INT          NOT NULL DEFAULT 0  COMMENT '洗涤剂用量 ml',
  water_temp        INT          NOT NULL DEFAULT 0  COMMENT '水温 ℃',
  machine_id        BIGINT                DEFAULT NULL COMMENT '水洗机 id',
  operator          VARCHAR(32)           DEFAULT NULL COMMENT '当班操作员',
  status            VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING 在产 / HOLD 挂起 / DONE 已完成 / QUARANTINED 污染隔离中 / REVIEW 参数偏差待复核',
  start_time        DATETIME              DEFAULT NULL,
  finish_time       DATETIME              DEFAULT NULL,
  rewash_order_id   BIGINT                DEFAULT NULL COMMENT '若本批次是污染复洗产生的新批次，对应复洗单 id；否则为空',
  quarantine_was_done TINYINT  NOT NULL DEFAULT 0 COMMENT '被污染命中隔离前是否已是 DONE 批次（放行时据此决定恢复 RUNNING 还是保持 DONE）',
  recipe_version_id BIGINT                DEFAULT NULL COMMENT '建批时所用洗涤配方版本 id',
  recipe_version_no INT                   DEFAULT NULL COMMENT '所用配方版本号（冗余冻结，不随版本表后续变化）',
  recipe_snapshot   JSON                  DEFAULT NULL COMMENT '批次配方快照：建批时冻结八道工序标准参数与容差，后续发布/停用版本不影响本批',
  remark            VARCHAR(255)          DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_no (batch_no),
  KEY idx_batch_category (category_id),
  KEY idx_batch_machine (machine_id),
  KEY idx_batch_status (status),
  KEY idx_batch_rewash (rewash_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗涤批次';

-- ---------- 6. 工序流转记录 ----------
CREATE TABLE IF NOT EXISTS batch_step (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id     BIGINT      NOT NULL            COMMENT '批次 id',
  step_code    VARCHAR(16) NOT NULL            COMMENT '工序编码',
  step_name    VARCHAR(16) NOT NULL            COMMENT '工序名称',
  step_order   INT         NOT NULL            COMMENT '工序号 1~8',
  water_temp   INT         NOT NULL DEFAULT 0  COMMENT '本工序水温 ℃',
  detergent_ml INT         NOT NULL DEFAULT 0  COMMENT '本工序洗涤剂 ml',
  machine_id   BIGINT               DEFAULT NULL COMMENT '本工序实际占用设备 id（污染事件按设备+时段命中批次的事实依据）',
  operator     VARCHAR(32)          DEFAULT NULL COMMENT '操作员',
  status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'DONE 已完成 / CURRENT 进行中 / PENDING 未开始',
  start_time   DATETIME             DEFAULT NULL,
  end_time     DATETIME             DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_step_batch (batch_id),
  KEY idx_step_machine_time (machine_id, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序流转记录';

-- ---------- 7. 水洗机 / 烘干机设备台账 ----------
CREATE TABLE IF NOT EXISTS machine (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  code             VARCHAR(32)  NOT NULL            COMMENT '设备编号',
  name             VARCHAR(64)  NOT NULL            COMMENT '设备名称',
  model            VARCHAR(64)  NOT NULL            COMMENT '型号',
  machine_type     VARCHAR(16)  NOT NULL            COMMENT 'WASHER 水洗机 / DRYER 烘干机 / IRONER 烫平机',
  capacity_kg      DECIMAL(8,2) NOT NULL DEFAULT 0  COMMENT '容量 kg',
  status           VARCHAR(16)  NOT NULL DEFAULT 'IDLE' COMMENT 'RUNNING 运行 / IDLE 待机 / FAULT 故障',
  run_hours        DECIMAL(10,1) NOT NULL DEFAULT 0 COMMENT '累计运行时长（小时）',
  location         VARCHAR(32)           DEFAULT NULL COMMENT '车间位置',
  last_maintain_at DATE                  DEFAULT NULL COMMENT '上次保养日期',
  PRIMARY KEY (id),
  UNIQUE KEY uk_machine_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗涤设备台账';

-- ---------- 8. 设备维修单 ----------
CREATE TABLE IF NOT EXISTS repair_order (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  machine_id  BIGINT       NOT NULL            COMMENT '设备 id',
  fault_desc  VARCHAR(255) NOT NULL            COMMENT '故障描述',
  repair_man  VARCHAR(32)  NOT NULL            COMMENT '维修人',
  cost        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '维修费用（元）',
  report_time DATETIME              DEFAULT NULL COMMENT '报修时间',
  finish_time DATETIME              DEFAULT NULL COMMENT '完工时间',
  status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 待修 / DONE 已完工',
  PRIMARY KEY (id),
  KEY idx_repair_machine (machine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备维修单';

-- ---------- 9. 车间操作日志（Spring Data JPA 辅助落库） ----------
CREATE TABLE IF NOT EXISTS operation_log (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  biz_type    VARCHAR(16)          DEFAULT NULL COMMENT 'OUTLET / CATEGORY / ORDER / BATCH / MACHINE / CONTAMINATION / REWASH',
  biz_id      BIGINT               DEFAULT NULL,
  action_name VARCHAR(64)          DEFAULT NULL,
  detail      VARCHAR(255)         DEFAULT NULL,
  created_at  DATETIME             DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车间操作日志';

-- ---------- 10. 医院布草污染事件 ----------
-- 事件由车间人员登记（DRAFT），确认（CONFIRMED）时按“设备 + 影响时段”找出经过该设备的批次并落隔离记录；
-- 全部关联复洗批次完成后才可复核放行（RELEASED）。
CREATE TABLE IF NOT EXISTS contamination_event (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  event_no         VARCHAR(32)  NOT NULL                COMMENT '污染事件号 CR+日期+序号',
  machine_id       BIGINT       NOT NULL                COMMENT '发生设备 id',
  contam_type      VARCHAR(24)  NOT NULL                COMMENT '污染类型：INFECTIOUS 感染性 / BLOOD 血渍 / CHEMICAL 化学污染 / RADIOACTIVE 放射性 / OTHER 其他',
  found_time       DATETIME     NOT NULL                COMMENT '发现时间',
  affect_start     DATETIME     NOT NULL                COMMENT '影响开始时间',
  affect_end       DATETIME     NOT NULL                COMMENT '影响结束时间',
  description      VARCHAR(255)          DEFAULT NULL   COMMENT '污染描述',
  reporter         VARCHAR(32)           DEFAULT NULL   COMMENT '登记人',
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 待确认 / CONFIRMED 已确认隔离中 / RELEASED 已复核放行',
  confirmed_by     VARCHAR(32)           DEFAULT NULL   COMMENT '确认人',
  confirmed_at     DATETIME              DEFAULT NULL   COMMENT '确认时间',
  released_by      VARCHAR(32)           DEFAULT NULL   COMMENT '放行人',
  released_at      DATETIME              DEFAULT NULL   COMMENT '放行时间',
  release_note     VARCHAR(255)          DEFAULT NULL   COMMENT '放行备注',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_no (event_no),
  KEY idx_event_machine (machine_id),
  KEY idx_event_status (status),
  KEY idx_event_affect (machine_id, affect_start, affect_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医院布草污染事件';

-- ---------- 11. 批次隔离记录 ----------
-- 一个污染事件命中一个批次只落一条：UNIQUE(event_id, batch_id) 在数据库层兜底，重复确认/并发请求都不会产生重复隔离记录。
-- 记录命中时批次停留工序、当时绑定设备与命中原因，便于事件详情逐条解释“为什么被命中”。
CREATE TABLE IF NOT EXISTS batch_isolation (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  event_id         BIGINT       NOT NULL                COMMENT '污染事件 id',
  batch_id         BIGINT       NOT NULL                COMMENT '被命中隔离的批次 id',
  hit_step_code    VARCHAR(16)           DEFAULT NULL   COMMENT '命中时批次停留工序编码',
  hit_step_name    VARCHAR(16)           DEFAULT NULL   COMMENT '命中时批次停留工序名称',
  hit_machine_id   BIGINT                DEFAULT NULL   COMMENT '命中时批次绑定设备 id',
  hit_overlap      VARCHAR(32)           DEFAULT NULL   COMMENT '命中时与影响窗口重叠时长（文案，如 25分钟 / 1小时5分钟）',
  hit_reason       VARCHAR(500) NOT NULL                COMMENT '命中原因解释：为何该批次在窗口内经过该设备',
  status           VARCHAR(16)  NOT NULL DEFAULT 'ISOLATED' COMMENT 'ISOLATED 隔离中 / RELEASED 已随事件放行',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  released_at      DATETIME              DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_isolation_event_batch (event_id, batch_id),
  KEY idx_iso_batch (batch_id),
  KEY idx_iso_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='污染批次隔离记录';

-- ---------- 12. 复洗单 ----------
-- 处置人员为隔离批次发起复洗：复洗必须新建一条可追溯批次 wash_batch（其 rewash_order_id 指向本单）并关联原批次。
-- active_flag 为存储生成列：仅“有效复洗”（ACTIVE 在产 / DONE 完成）为 1，FAILED / DELETED 为 NULL。
-- UNIQUE(isolation_id, active_flag) 保证同一隔离批次至多一份有效复洗——并发重复点击只能落一份。
-- 放行规则：隔离批次必须恰好存在一份 DONE 复洗，且没有 ACTIVE / FAILED / DELETED 复洗，否则保持隔离。
CREATE TABLE IF NOT EXISTS rewash_order (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  rewash_no        VARCHAR(32)  NOT NULL                COMMENT '复洗单号 RW+日期+序号',
  isolation_id     BIGINT       NOT NULL                COMMENT '隔离记录 id',
  event_id         BIGINT       NOT NULL                COMMENT '污染事件 id（冗余便于汇总）',
  source_batch_id  BIGINT       NOT NULL                COMMENT '原隔离批次 id',
  rewash_batch_id  BIGINT                DEFAULT NULL   COMMENT '复洗产生的新批次 id',
  machine_id       BIGINT                DEFAULT NULL   COMMENT '复洗水洗机 id',
  operator         VARCHAR(32)           DEFAULT NULL   COMMENT '发起人 / 复洗操作员',
  reason           VARCHAR(255)          DEFAULT NULL   COMMENT '复洗原因 / 处置说明',
  status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 在产 / DONE 完成 / FAILED 失败 / DELETED 已删除',
  active_flag      TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('ACTIVE','DONE') THEN 1 ELSE NULL END) STORED COMMENT '有效复洗标记，用于唯一索引',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at      DATETIME              DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rewash_no (rewash_no),
  UNIQUE KEY uk_rewash_active (isolation_id, active_flag),
  KEY idx_rewash_event (event_id),
  KEY idx_rewash_source (source_batch_id),
  KEY idx_rewash_batch (rewash_batch_id),
  KEY idx_rewash_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='污染批次复洗单';


-- ---------- 种子数据：客户网点（12 家） ----------
INSERT INTO outlet (code, name, outlet_type, district, contact, phone, settle_type,
                    credit_limit, dirty_pending, warn_threshold, map_x, map_y, status, created_at) VALUES
  ('HT-001', '滨江华辰大酒店', 'HOTEL', '滨江区', '周敏', '13805710121', 'MONTHLY', 68000.00, 620, 500, 14, 22, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 391 DAY)),
  ('HT-002', '钱江新城万怡酒店', 'HOTEL', '上城区', '李文轩', '13905718866', 'MONTHLY', 92000.00, 890, 700, 30, 14, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 382 DAY)),
  ('HT-003', '西湖国宾馆', 'HOTEL', '西湖区', '陈静', '13705715542', 'MONTHLY', 120000.00, 430, 600, 22, 44, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 373 DAY)),
  ('HT-004', '未来科技城亚朵', 'HOTEL', '余杭区', '吴凯', '13605717321', 'COUNT', 0.00, 310, 400, 46, 30, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 364 DAY)),
  ('HT-005', '运河上街全季', 'HOTEL', '拱墅区', '赵雪', '13505719002', 'MONTHLY', 45000.00, 705, 500, 58, 20, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 355 DAY)),
  ('HT-006', '萧山机场空港酒店', 'HOTEL', '萧山区', '孙立', '13405716688', 'CASH', 0.00, 268, 350, 74, 36, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 346 DAY)),
  ('HP-001', '市第一人民医院', 'HOSPITAL', '上城区', '郑雅芳', '13005711200', 'MONTHLY', 150000.00, 1180, 900, 36, 58, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 337 DAY)),
  ('HP-002', '邵逸夫医院庆春院区', 'HOSPITAL', '江干区', '冯国强', '13105714567', 'MONTHLY', 136000.00, 940, 800, 52, 62, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 328 DAY)),
  ('HP-003', '西溪社区卫生中心', 'HOSPITAL', '西湖区', '许蓓', '13205713890', 'COUNT', 0.00, 176, 300, 18, 70, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 319 DAY)),
  ('HP-004', '滨江骨科专科医院', 'HOSPITAL', '滨江区', '何俊', '13305712745', 'MONTHLY', 72000.00, 512, 500, 66, 74, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 310 DAY)),
  ('HT-007', '临平银泰开元名都', 'HOTEL', '临平区', '林芳', '15805716233', 'MONTHLY', 58000.00, 390, 450, 84, 18, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 301 DAY)),
  ('HP-005', '下沙方舱康复中心', 'HOSPITAL', '钱塘区', '顾伟', '15905718081', 'CASH', 0.00, 240, 320, 88, 56, 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 292 DAY));

-- ---------- 种子数据：布草品类（8 类） ----------
INSERT INTO linen_category (code, name, spec, unit_price, stock_qty,
                            scrap_rate, min_turnover, owner_type, remark) VALUES
  ('LC-BED', '床单', '1.5m×2.0m 全棉', 3.20, 4280, 1.80, 3600, 'COMMON', '主洗 70℃，氯漂慎用'),
  ('LC-QUI', '被套', '1.8m×2.2m 全棉', 4.50, 1960, 2.40, 2400, 'COMMON', '脱水后必须抖散再烘干'),
  ('LC-PIL', '枕套', '48cm×74cm', 1.60, 3120, 1.20, 3000, 'COMMON', '易夹带，预洗加长 3 分钟'),
  ('LC-TWL', '浴巾', '70cm×140cm 32支', 2.80, 1540, 3.60, 2200, 'HOTEL', '烘干温度不超过 80℃'),
  ('LC-TAB', '台布', '1.6m 圆台 涤棉', 6.50, 486, 5.20, 600, 'HOTEL', '重油污需预涂去渍剂'),
  ('LC-UNI', '工服', '分体套装 涤棉', 8.00, 352, 6.40, 480, 'COMMON', '按部门分色分缸洗'),
  ('LC-SUR', '手术衣', '绿色 加强型', 11.50, 268, 4.80, 420, 'HOSPITAL', '医感布草，单独缸、单独烘干'),
  ('LC-PAT', '病号服', '蓝白条 通用码', 5.40, 620, 7.50, 900, 'HOSPITAL', '高温 75℃ 主洗，加消毒中和');

-- ---------- 种子数据：洗涤设备（10 台） ----------
INSERT INTO machine (code, name, model, machine_type, capacity_kg, status,
                     run_hours, location, last_maintain_at) VALUES
  ('WS-01', '1号水洗机', 'XGQ-100F 全自动', 'WASHER', 100.00, 'RUNNING', 12680.5, '洗涤车间 A 区', '2026-08-12'),
  ('WS-02', '2号水洗机', 'XGQ-100F 全自动', 'WASHER', 100.00, 'RUNNING', 11420.0, '洗涤车间 A 区', '2026-08-12'),
  ('WS-03', '3号水洗机', 'XGQ-70F 全自动', 'WASHER', 70.00, 'IDLE', 9860.3, '洗涤车间 A 区', '2026-07-30'),
  ('WS-04', '4号水洗机', 'XGQ-50F 隔离式', 'WASHER', 50.00, 'FAULT', 8215.7, '医感专区', '2026-06-18'),
  ('WS-05', '5号水洗机', 'XGQ-30F 小型', 'WASHER', 30.00, 'IDLE', 4360.2, '医感专区', '2026-08-01'),
  ('DR-01', '1号烘干机', 'HGQ-100 蒸汽型', 'DRYER', 100.00, 'RUNNING', 10240.8, '后整理 B 区', '2026-08-05'),
  ('DR-02', '2号烘干机', 'HGQ-70 蒸汽型', 'DRYER', 70.00, 'IDLE', 8875.4, '后整理 B 区', '2026-08-05'),
  ('DR-03', '3号烘干机', 'HGQ-50 电加热', 'DRYER', 50.00, 'FAULT', 7630.9, '后整理 B 区', '2026-05-22'),
  ('IR-01', '1号烫平机', 'TPII-3000 三辊', 'IRONER', 60.00, 'RUNNING', 6540.6, '后整理 C 区', '2026-07-16'),
  ('IR-02', '2号烫平机', 'TPII-2400 双辊', 'IRONER', 40.00, 'IDLE', 5120.1, '后整理 C 区', '2026-07-16');

-- ---------- 种子数据：收送单（12 张，覆盖已收待送 / 已送达 / 已结算） ----------
INSERT INTO pickup_order (order_no, outlet_id, pickup_time, deliver_time, dirty_qty, clean_qty,
                          driver, plate, shortage_qty, compensation, status, remark) VALUES
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-001'), 1, TIMESTAMP(CURDATE(), '05:40:00'), TIMESTAMP(CURDATE(), '10:10:00'), 620, 620, '马海涛', '浙A7K521', 0, 0.00, 'DELIVERED', '床单为主，无短少'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-002'), 2, TIMESTAMP(CURDATE(), '06:10:00'), TIMESTAMP(CURDATE(), '10:40:00'), 880, 872, '徐国庆', '浙A9M308', 8, 168.00, 'DELIVERED', '浴巾短少 8 件，按单价 21 元赔付'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-003'), 7, TIMESTAMP(CURDATE(), '06:50:00'), TIMESTAMP(CURDATE(), '11:20:00'), 1180, 1156, '朱小明', '浙A2L617', 24, 690.00, 'DELIVERED', '手术衣短少 24 件，已走赔付流程'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-004'), 4, TIMESTAMP(CURDATE(), '07:20:00'), NULL, 310, 0, '范建华', '浙A5N902', 0, 0.00, 'PICKED', '收脏待洗，明日 9 点送净'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-005'), 3, TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '12:00:00'), 430, 430, '马海涛', '浙A7K521', 0, 0.00, 'DELIVERED', '国宾馆 VIP 楼层，单独装笼'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-006'), 8, TIMESTAMP(CURDATE(), '08:40:00'), TIMESTAMP(CURDATE(), '12:40:00'), 940, 933, '徐国庆', '浙A9M308', 7, 210.00, 'DELIVERED', '病号服短少 7 件'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-007'), 5, TIMESTAMP(CURDATE(), '09:10:00'), NULL, 705, 0, '朱小明', '浙A2L617', 0, 0.00, 'PICKED', '全季当日布草，待排产'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-008'), 10, TIMESTAMP(CURDATE(), '09:40:00'), TIMESTAMP(CURDATE(), '13:20:00'), 512, 502, '范建华', '浙A5N902', 10, 320.00, 'DELIVERED', '骨科医院工服破损 10 件'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-009'), 6, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '20:10:00'), 268, 268, '马海涛', '浙A7K521', 0, 0.00, 'CLOSED', '机场酒店，现结已开票'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-010'), 9, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:00:00'), 176, 176, '徐国庆', '浙A9M308', 0, 0.00, 'CLOSED', '社区中心，计件结算'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-011'), 11, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '17:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:40:00'), 390, 386, '朱小明', '浙A2L617', 4, 96.00, 'CLOSED', '开元名都枕套短少 4 件'),
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-012'), 12, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:20:00'), NULL, 240, 0, '范建华', '浙A5N902', 0, 0.00, 'PICKED', '康复中心首单，待洗涤'),
  -- 跨日送净：昨日收脏，今日才送净——脏件只算昨日，净件/短少/赔付只算今日
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-013'), 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '18:30:00'), TIMESTAMP(CURDATE(), '08:30:00'), 150, 145, '马海涛', '浙A7K521', 5, 110.00, 'DELIVERED', '跨日单：昨晚收脏今早送净，床单短少 5 件'),
  -- 送达但未登记司机 / 车牌：不参与任何司机绩效，也不能被统计丢弃
  (CONCAT('RS', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-014'), 6, TIMESTAMP(CURDATE(), '06:40:00'), TIMESTAMP(CURDATE(), '09:50:00'), 90, 90, NULL, NULL, 0, 0.00, 'DELIVERED', '临时司机代送，司机车牌后补');

-- ---------- 种子数据：收送单明细（按品类拆分收发件数与短少赔付） ----------
INSERT INTO pickup_order_line (order_id, category_id, dirty_qty, clean_qty, shortage_qty, loss_amount) VALUES
  (1, 1, 320, 320, 0, 0.00),
  (1, 2, 180, 180, 0, 0.00),
  (1, 3, 120, 120, 0, 0.00),
  (2, 4, 460, 452, 8, 168.00),
  (2, 1, 260, 260, 0, 0.00),
  (2, 5, 160, 160, 0, 0.00),
  (3, 7, 380, 356, 24, 690.00),
  (3, 8, 420, 420, 0, 0.00),
  (3, 1, 380, 380, 0, 0.00),
  (4, 1, 180, 0, 0, 0.00),
  (4, 3, 130, 0, 0, 0.00),
  (5, 2, 240, 240, 0, 0.00),
  (5, 1, 190, 190, 0, 0.00),
  (6, 8, 520, 513, 7, 210.00),
  (6, 7, 180, 180, 0, 0.00),
  (6, 2, 240, 240, 0, 0.00),
  (7, 1, 380, 0, 0, 0.00),
  (7, 4, 210, 0, 0, 0.00),
  (7, 3, 115, 0, 0, 0.00),
  (8, 6, 300, 290, 10, 320.00),
  (8, 8, 212, 212, 0, 0.00),
  (9, 1, 150, 150, 0, 0.00),
  (9, 4, 118, 118, 0, 0.00),
  (10, 8, 176, 176, 0, 0.00),
  (11, 3, 210, 206, 4, 96.00),
  (11, 2, 180, 180, 0, 0.00),
  (12, 8, 160, 0, 0, 0.00),
  (12, 1, 80, 0, 0, 0.00);

-- ---------- 种子数据：洗涤批次（11 个，覆盖在产 / 已完成） ----------
INSERT INTO wash_batch (batch_no, category_id, weight_kg, current_step, current_step_order,
                        detergent_ml, water_temp, machine_id, operator, status, start_time,
                        finish_time, remark) VALUES
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-001'), 1, 86.50, 'NEUTRALIZE', 4, 900, 40, 1, '王建国', 'RUNNING', TIMESTAMP(CURDATE(), '04:10:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-002'), 2, 72.00, 'MAIN_WASH', 2, 850, 70, 2, '刘志强', 'RUNNING', TIMESTAMP(CURDATE(), '05:00:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-003'), 3, 41.50, 'DRY', 6, 520, 75, 3, '王建国', 'RUNNING', TIMESTAMP(CURDATE(), '04:30:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-004'), 7, 28.00, 'RINSE', 3, 460, 45, 4, '孙红梅', 'RUNNING', TIMESTAMP(CURDATE(), '06:00:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-005'), 5, 55.00, 'IRON', 7, 1100, 160, 1, '刘志强', 'RUNNING', TIMESTAMP(CURDATE(), '04:00:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-006'), 6, 33.50, 'PRE_WASH', 1, 700, 35, 2, '孙红梅', 'RUNNING', TIMESTAMP(CURDATE(), '08:10:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-007'), 8, 24.00, 'EXTRACT', 5, 480, 0, 5, '孙红梅', 'RUNNING', TIMESTAMP(CURDATE(), '06:30:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-008'), 4, 48.00, 'FOLD', 8, 760, 0, 6, '王建国', 'RUNNING', TIMESTAMP(CURDATE(), '04:20:00'), NULL, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-009'), 1, 92.00, 'FOLD', 8, 960, 0, 1, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00'), TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 315 MINUTE, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-010'), 2, 64.50, 'FOLD', 8, 820, 0, 2, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00'), TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 315 MINUTE, '按工艺卡执行，水温与洗涤剂用量已复核'),
  (CONCAT('PC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-011'), 3, 38.00, 'FOLD', 8, 500, 0, 3, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00'), TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 315 MINUTE, '按工艺卡执行，水温与洗涤剂用量已复核');

-- ---------- 种子数据：工序流转记录（每批次按工序顺序铺开，已完成工序已收口） ----------
INSERT INTO batch_step (batch_id, step_code, step_name, step_order, water_temp,
                        detergent_ml, operator, status, start_time, end_time) VALUES
  (1, 'PRE_WASH', '预洗', 1, 35, 900, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:10:00'), TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 35 MINUTE),
  (1, 'MAIN_WASH', '主洗', 2, 70, 900, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 75 MINUTE),
  (1, 'RINSE', '漂洗', 3, 45, 900, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 115 MINUTE),
  (1, 'NEUTRALIZE', '中和', 4, 40, 900, '王建国', 'CURRENT', TIMESTAMP(CURDATE(), '04:10:00') + INTERVAL 120 MINUTE, NULL),
  (2, 'PRE_WASH', '预洗', 1, 35, 850, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '05:00:00'), TIMESTAMP(CURDATE(), '05:00:00') + INTERVAL 35 MINUTE),
  (2, 'MAIN_WASH', '主洗', 2, 70, 850, '刘志强', 'CURRENT', TIMESTAMP(CURDATE(), '05:00:00') + INTERVAL 40 MINUTE, NULL),
  (3, 'PRE_WASH', '预洗', 1, 35, 520, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:30:00'), TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 35 MINUTE),
  (3, 'MAIN_WASH', '主洗', 2, 70, 520, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 75 MINUTE),
  (3, 'RINSE', '漂洗', 3, 45, 520, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 115 MINUTE),
  (3, 'NEUTRALIZE', '中和', 4, 40, 520, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 155 MINUTE),
  (3, 'EXTRACT', '脱水', 5, 0, 520, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 195 MINUTE),
  (3, 'DRY', '烘干', 6, 75, 520, '王建国', 'CURRENT', TIMESTAMP(CURDATE(), '04:30:00') + INTERVAL 200 MINUTE, NULL),
  (4, 'PRE_WASH', '预洗', 1, 35, 460, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:00:00'), TIMESTAMP(CURDATE(), '06:00:00') + INTERVAL 35 MINUTE),
  (4, 'MAIN_WASH', '主洗', 2, 70, 460, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:00:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '06:00:00') + INTERVAL 75 MINUTE),
  (4, 'RINSE', '漂洗', 3, 45, 460, '孙红梅', 'CURRENT', TIMESTAMP(CURDATE(), '06:00:00') + INTERVAL 80 MINUTE, NULL),
  (5, 'PRE_WASH', '预洗', 1, 35, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00'), TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 35 MINUTE),
  (5, 'MAIN_WASH', '主洗', 2, 70, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 75 MINUTE),
  (5, 'RINSE', '漂洗', 3, 45, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 115 MINUTE),
  (5, 'NEUTRALIZE', '中和', 4, 40, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 155 MINUTE),
  (5, 'EXTRACT', '脱水', 5, 0, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 195 MINUTE),
  (5, 'DRY', '烘干', 6, 75, 1100, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 200 MINUTE, TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 235 MINUTE),
  (5, 'IRON', '烫平', 7, 160, 1100, '刘志强', 'CURRENT', TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL 240 MINUTE, NULL),
  (6, 'PRE_WASH', '预洗', 1, 35, 700, '孙红梅', 'CURRENT', TIMESTAMP(CURDATE(), '08:10:00'), NULL),
  (7, 'PRE_WASH', '预洗', 1, 35, 480, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:30:00'), TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 35 MINUTE),
  (7, 'MAIN_WASH', '主洗', 2, 70, 480, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 75 MINUTE),
  (7, 'RINSE', '漂洗', 3, 45, 480, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 115 MINUTE),
  (7, 'NEUTRALIZE', '中和', 4, 40, 480, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 155 MINUTE),
  (7, 'EXTRACT', '脱水', 5, 0, 480, '孙红梅', 'CURRENT', TIMESTAMP(CURDATE(), '06:30:00') + INTERVAL 160 MINUTE, NULL),
  (8, 'PRE_WASH', '预洗', 1, 35, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00'), TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 35 MINUTE),
  (8, 'MAIN_WASH', '主洗', 2, 70, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 75 MINUTE),
  (8, 'RINSE', '漂洗', 3, 45, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 115 MINUTE),
  (8, 'NEUTRALIZE', '中和', 4, 40, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 155 MINUTE),
  (8, 'EXTRACT', '脱水', 5, 0, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 195 MINUTE),
  (8, 'DRY', '烘干', 6, 75, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 200 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 235 MINUTE),
  (8, 'IRON', '烫平', 7, 160, 760, '王建国', 'DONE', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 240 MINUTE, TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 275 MINUTE),
  (8, 'FOLD', '折叠', 8, 0, 760, '王建国', 'CURRENT', TIMESTAMP(CURDATE(), '04:20:00') + INTERVAL 280 MINUTE, NULL),
  (9, 'PRE_WASH', '预洗', 1, 35, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00'), TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 35 MINUTE),
  (9, 'MAIN_WASH', '主洗', 2, 70, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 75 MINUTE),
  (9, 'RINSE', '漂洗', 3, 45, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 115 MINUTE),
  (9, 'NEUTRALIZE', '中和', 4, 40, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 155 MINUTE),
  (9, 'EXTRACT', '脱水', 5, 0, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 195 MINUTE),
  (9, 'DRY', '烘干', 6, 75, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 200 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 235 MINUTE),
  (9, 'IRON', '烫平', 7, 160, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 240 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 275 MINUTE),
  (9, 'FOLD', '折叠', 8, 0, 960, '刘志强', 'DONE', TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 280 MINUTE, TIMESTAMP(CURDATE(), '03:00:00') + INTERVAL 315 MINUTE),
  (10, 'PRE_WASH', '预洗', 1, 35, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00'), TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 35 MINUTE),
  (10, 'MAIN_WASH', '主洗', 2, 70, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 75 MINUTE),
  (10, 'RINSE', '漂洗', 3, 45, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 115 MINUTE),
  (10, 'NEUTRALIZE', '中和', 4, 40, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 155 MINUTE),
  (10, 'EXTRACT', '脱水', 5, 0, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 195 MINUTE),
  (10, 'DRY', '烘干', 6, 75, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 200 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 235 MINUTE),
  (10, 'IRON', '烫平', 7, 160, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 240 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 275 MINUTE),
  (10, 'FOLD', '折叠', 8, 0, 820, '王建国', 'DONE', TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 280 MINUTE, TIMESTAMP(CURDATE(), '03:20:00') + INTERVAL 315 MINUTE),
  (11, 'PRE_WASH', '预洗', 1, 35, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00'), TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 35 MINUTE),
  (11, 'MAIN_WASH', '主洗', 2, 70, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 40 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 75 MINUTE),
  (11, 'RINSE', '漂洗', 3, 45, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 80 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 115 MINUTE),
  (11, 'NEUTRALIZE', '中和', 4, 40, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 120 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 155 MINUTE),
  (11, 'EXTRACT', '脱水', 5, 0, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 160 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 195 MINUTE),
  (11, 'DRY', '烘干', 6, 75, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 200 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 235 MINUTE),
  (11, 'IRON', '烫平', 7, 160, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 240 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 275 MINUTE),
  (11, 'FOLD', '折叠', 8, 0, 500, '孙红梅', 'DONE', TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 280 MINUTE, TIMESTAMP(CURDATE(), '03:40:00') + INTERVAL 315 MINUTE);

-- 回填每道工序实际占用设备：水洗 1~5 道用批次绑定水洗机，烘干用 1 号烘干机，烫平用 1 号烫平机，折叠不占设备。
-- 这是污染事件“按设备 + 时段”反查批次的事实依据（batch_step.machine_id）。
UPDATE batch_step s JOIN wash_batch b ON b.id = s.batch_id
   SET s.machine_id = CASE
       WHEN s.step_order <= 5 THEN b.machine_id
       WHEN s.step_order = 6  THEN 6
       WHEN s.step_order = 7  THEN 9
       ELSE NULL END;

-- ---------- 种子数据：一条待确认的医院布草污染事件 ----------
-- 4 号隔离式水洗机（医感专区）晨间污染；确认后会按设备占用时段命中在该机漂洗的 PC-...-004（手术衣）。
INSERT INTO contamination_event (event_no, machine_id, contam_type, found_time, affect_start, affect_end,
                                 description, reporter, status, created_at) VALUES
  (CONCAT('CR', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-001'), 4, 'INFECTIOUS',
   TIMESTAMP(CURDATE(), '07:40:00'), TIMESTAMP(CURDATE(), '06:00:00'), TIMESTAMP(CURDATE(), '08:00:00'),
   '医感专区 4 号隔离式水洗机密封渗漏，疑似感染性布草交叉污染，需排查该机晨间全部过水批次',
   '感控员-郑雅芳', 'DRAFT', NOW());

-- ---------- 种子数据：设备维修单（7 张，含 2 张待修） ----------
INSERT INTO repair_order (machine_id, fault_desc, repair_man, cost, report_time, finish_time, status) VALUES
  (4, '主轴密封圈渗漏，脱水时异响', '张伟民', 1850.00, TIMESTAMP(CURDATE(), '05:20:00'), NULL, 'OPEN'),
  (8, '电加热管三相不平衡，温控报警 E03', '张伟民', 2600.00, TIMESTAMP(CURDATE(), '06:40:00'), NULL, 'OPEN'),
  (1, '变频器散热风扇卡死', '外协-机电班组', 680.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '18:30:00'), 'DONE'),
  (6, '蒸汽疏水阀堵塞，烘干时间延长', '张伟民', 420.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '13:10:00'), 'DONE'),
  (2, '门锁微动开关接触不良', '外协-机电班组', 260.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '11:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '15:00:00'), 'DONE'),
  (9, '烫平机输送带跑偏', '陈立新', 1350.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '16:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '19:40:00'), 'DONE'),
  (5, '排水阀气动执行器漏气', '陈立新', 390.00, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '14:20:00'), 'DONE');

-- ---------- 种子数据：车间操作日志 ----------
INSERT INTO operation_log (biz_type, biz_id, action_name, detail, created_at) VALUES
  ('BATCH', 1, '推进工序', '批次推进至中和', TIMESTAMP(CURDATE(), '07:20:00')),
  ('ORDER', 2, '送净登记', '净 872，短少 8', TIMESTAMP(CURDATE(), '10:40:00')),
  ('MACHINE', 4, '故障报修', '4号水洗机：主轴密封圈渗漏', TIMESTAMP(CURDATE(), '05:20:00')),
  ('CATEGORY', 4, '调整在库', '浴巾 -20', TIMESTAMP(CURDATE(), '09:05:00')),
  ('OUTLET', 7, '新增网点', '市第一人民医院', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:30:00')),
  ('BATCH', 9, '批次完工', '折叠完成，入库 76 件', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '22:10:00'));

-- ---------- 13. 洗涤配方版本 ----------
-- 按布草品类维护预洗至折叠八道工序的目标水温 / 洗涤剂用量 / 适用设备类型。
-- 生命周期：DRAFT 草稿（可反复调整）→ PUBLISHED 当前生效（不可原地改写，只能复制生成新版本）→ DISABLED 已停用。
-- current_flag 为存储生成列：仅 PUBLISHED 为 1；UNIQUE(category_id, current_flag) 保证每个品类
-- 同时只有一个当前配方版本——两人同时发布不同草稿时，数据库层兜底只有一份能生效，另一份报冲突。
CREATE TABLE IF NOT EXISTS wash_recipe_version (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  category_id     BIGINT       NOT NULL                COMMENT '布草品类 id',
  version_no      INT          NOT NULL                COMMENT '品类内版本号，从 1 递增',
  status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / PUBLISHED 当前生效 / DISABLED 已停用',
  machine_types   VARCHAR(64)  NOT NULL DEFAULT 'WASHER' COMMENT '可承接设备类型，逗号分隔（WASHER/DRYER/IRONER）',
  max_load_ratio  INT          NOT NULL DEFAULT 90     COMMENT '最大装载比例 %：批次重量 ≤ 设备容量 × 比例',
  base_version_id BIGINT                DEFAULT NULL   COMMENT '草稿基线版本 id（创建草稿时的当前生效版本，发布冲突检测依据）',
  remark          VARCHAR(255)          DEFAULT NULL,
  created_by      VARCHAR(32)           DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_by    VARCHAR(32)           DEFAULT NULL,
  published_at    DATETIME              DEFAULT NULL,
  disabled_by     VARCHAR(32)           DEFAULT NULL,
  disabled_at     DATETIME              DEFAULT NULL,
  current_flag    TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PUBLISHED' THEN 1 ELSE NULL END) STORED COMMENT '当前版本标记，用于唯一索引',
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_current (category_id, current_flag),
  UNIQUE KEY uk_recipe_version (category_id, version_no),
  KEY idx_recipe_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗涤配方版本';

-- ---------- 14. 配方版本工序参数 ----------
-- 每个版本固定八道工序一行：目标水温 / 水温容差 / 目标洗涤剂 / 洗涤剂容差 / 本工序适用设备类型。
CREATE TABLE IF NOT EXISTS wash_recipe_step (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  version_id          BIGINT      NOT NULL            COMMENT '配方版本 id',
  step_code           VARCHAR(16) NOT NULL            COMMENT '工序编码',
  step_name           VARCHAR(16) NOT NULL            COMMENT '工序名称',
  step_order          INT         NOT NULL            COMMENT '工序号 1~8',
  target_temp         INT         NOT NULL DEFAULT 0  COMMENT '目标水温 ℃',
  temp_tolerance      INT         NOT NULL DEFAULT 5  COMMENT '水温容差 ±℃',
  target_detergent_ml INT         NOT NULL DEFAULT 0  COMMENT '目标洗涤剂用量 ml',
  detergent_tolerance INT         NOT NULL DEFAULT 100 COMMENT '洗涤剂容差 ±ml',
  machine_types       VARCHAR(64) NOT NULL DEFAULT '' COMMENT '本工序适用设备类型，逗号分隔；空 = 不占设备',
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_step (version_id, step_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配方版本工序参数';

-- ---------- 15. 批次参数偏差复核 ----------
-- 推进工序时实际水温 / 洗涤剂超出批次快照容差：批次转 REVIEW 待复核并落本表（PENDING），
-- 主管填写原因后选择 RELEASED 按本批次放行 / REDONE 退回重做；结论只影响该批次，不回写配方。
CREATE TABLE IF NOT EXISTS batch_deviation_review (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id            BIGINT      NOT NULL            COMMENT '批次 id',
  step_code           VARCHAR(16) NOT NULL            COMMENT '超差工序编码',
  step_name           VARCHAR(16) NOT NULL            COMMENT '超差工序名称',
  step_order          INT         NOT NULL            COMMENT '工序号 1~8',
  std_water_temp      INT                  DEFAULT NULL COMMENT '快照标准水温 ℃',
  std_detergent_ml    INT                  DEFAULT NULL COMMENT '快照标准洗涤剂 ml',
  actual_water_temp   INT                  DEFAULT NULL COMMENT '实际录入水温 ℃',
  actual_detergent_ml INT                  DEFAULT NULL COMMENT '实际录入洗涤剂 ml',
  target_step         VARCHAR(16)          DEFAULT NULL COMMENT '被拦截推进的目标工序编码；NULL = 收口完工',
  next_water_temp     INT                  DEFAULT NULL COMMENT '原计划下一工序水温（放行时按此继续）',
  next_detergent_ml   INT                  DEFAULT NULL COMMENT '原计划下一工序洗涤剂（放行时按此继续）',
  operator            VARCHAR(32)          DEFAULT NULL COMMENT '当班操作员',
  status              VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待复核 / RELEASED 已放行 / REDONE 已退回重做',
  reason              VARCHAR(255)         DEFAULT NULL COMMENT '主管复核原因（必填）',
  reviewer            VARCHAR(32)          DEFAULT NULL COMMENT '复核人（主管）',
  decided_at          DATETIME             DEFAULT NULL COMMENT '复核时间',
  created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_review_batch (batch_id),
  KEY idx_review_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次参数偏差复核';

-- ---------- 种子数据：洗涤配方版本（8 个品类各一份 V1 当前生效） ----------
-- 医感布草（手术衣 / 病号服）装载比例收紧到 80%，其余 90%。
INSERT INTO wash_recipe_version (category_id, version_no, status, machine_types, max_load_ratio,
                                 remark, created_by, published_by, published_at)
SELECT c.id, 1, 'PUBLISHED', 'WASHER',
       CASE WHEN c.code IN ('LC-SUR', 'LC-PAT') THEN 80 ELSE 90 END,
       '初始发布：按工艺卡默认参数建档', '系统初始化', '系统初始化', NOW()
  FROM linen_category c;

-- ---------- 种子数据：配方工序参数（八道工序，洗涤剂目标按品类区分） ----------
INSERT INTO wash_recipe_step (version_id, step_code, step_name, step_order, target_temp,
                              temp_tolerance, target_detergent_ml, detergent_tolerance, machine_types)
SELECT v.id, s.step_code, s.step_name, s.step_order, s.target_temp,
       5, d.det, 100, s.mtypes
  FROM wash_recipe_version v
  JOIN linen_category c ON c.id = v.category_id
  JOIN (SELECT 'LC-BED' AS code, 900 AS det UNION ALL SELECT 'LC-QUI', 850
        UNION ALL SELECT 'LC-PIL', 520 UNION ALL SELECT 'LC-TWL', 760
        UNION ALL SELECT 'LC-TAB', 1100 UNION ALL SELECT 'LC-UNI', 700
        UNION ALL SELECT 'LC-SUR', 460 UNION ALL SELECT 'LC-PAT', 480) d ON d.code = c.code
  JOIN (SELECT 1 AS step_order, 'PRE_WASH' AS step_code, '预洗' AS step_name, 35 AS target_temp, 'WASHER' AS mtypes
        UNION ALL SELECT 2, 'MAIN_WASH', '主洗', 70, 'WASHER'
        UNION ALL SELECT 3, 'RINSE', '漂洗', 45, 'WASHER'
        UNION ALL SELECT 4, 'NEUTRALIZE', '中和', 40, 'WASHER'
        UNION ALL SELECT 5, 'EXTRACT', '脱水', 0, 'WASHER'
        UNION ALL SELECT 6, 'DRY', '烘干', 75, 'DRYER'
        UNION ALL SELECT 7, 'IRON', '烫平', 160, 'IRONER'
        UNION ALL SELECT 8, 'FOLD', '折叠', 0, '') s;

-- 病号服按品类台账备注：高温 75℃ 主洗
UPDATE wash_recipe_step s
  JOIN wash_recipe_version v ON v.id = s.version_id
  JOIN linen_category c ON c.id = v.category_id
   SET s.target_temp = 75
 WHERE c.code = 'LC-PAT' AND s.step_code = 'MAIN_WASH';

-- ---------- 回填：为已建批次冻结配方版本与完整批次快照 ----------
-- 快照内容 = 版本号 + 可承接设备类型 + 最大装载比例 + 八道工序标准参数；
-- 之后版本发布 / 停用都不再影响这些批次（在产批次按原快照继续执行）。
UPDATE wash_batch b
  JOIN wash_recipe_version v ON v.category_id = b.category_id AND v.status = 'PUBLISHED'
   SET b.recipe_version_id = v.id,
       b.recipe_version_no = v.version_no,
       b.recipe_snapshot = JSON_OBJECT(
           'versionId', v.id,
           'versionNo', v.version_no,
           'categoryId', v.category_id,
           'machineTypes', v.machine_types,
           'maxLoadRatio', v.max_load_ratio,
           'steps', (SELECT JSON_ARRAYAGG(t.j) FROM (
                       SELECT JSON_OBJECT(
                           'stepCode', s.step_code, 'stepName', s.step_name, 'stepOrder', s.step_order,
                           'targetTemp', s.target_temp, 'tempTolerance', s.temp_tolerance,
                           'targetDetergentMl', s.target_detergent_ml,
                           'detergentTolerance', s.detergent_tolerance,
                           'machineTypes', s.machine_types) AS j
                         FROM wash_recipe_step s
                        WHERE s.version_id = v.id
                        ORDER BY s.step_order) t));

-- 初始化完成
