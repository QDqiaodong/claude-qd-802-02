#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
独立验证收送单日汇总 / 配送看板的聚合口径（不依赖 Spring/MySQL，可独立运行）。

数据源：schema.sql 中 pickup_order 种子单（含 -013 跨日送净、-014 无司机车牌）。
SQLite 中用字符串时间实现与后端 MySQL SQL 完全等价的语义：
  收脏件数/收脏单量按 pickup_time 所在自然日；
  送净件数/短少/赔付/送净单量按 deliver_time 所在自然日；
  当日收当日送两边各计一次、order_count 只算一单；未送净单只进收脏口径。
"""
import sqlite3

# (id, pickup偏移天, pickup时刻, deliver偏移天, deliver时刻, 脏, 净, 司机, 车牌, 短少, 赔付, 片区)
ORDERS = [
    (1,  0, '05:40',  0, '10:10', 620, 620, '马海涛', '浙A7K521', 0,   0,   '滨江区'),
    (2,  0, '06:10',  0, '10:40', 880, 872, '徐国庆', '浙A9M308', 8,   168, '上城区'),
    (3,  0, '06:50',  0, '11:20', 1180,1156,'朱小明', '浙A2L617', 24,  690, '上城区'),
    (4,  0, '07:20',  None, None, 310, 0,  '范建华', '浙A5N902', 0,   0,   '余杭区'),
    (5,  0, '08:00',  0, '12:00', 430, 430, '马海涛', '浙A7K521', 0,   0,   '西湖区'),
    (6,  0, '08:40',  0, '12:40', 940, 933, '徐国庆', '浙A9M308', 7,   210, '江干区'),
    (7,  0, '09:10',  None, None, 705, 0,  '朱小明', '浙A2L617', 0,   0,   '拱墅区'),
    (8,  0, '09:40',  0, '13:20', 512, 502, '范建华', '浙A5N902', 10,  320, '滨江区'),
    (9,  -1,'15:30', -1,'20:10', 268, 268, '马海涛', '浙A7K521', 0,   0,   '萧山区'),
    (10, -1,'16:20', -1,'21:00', 176, 176, '徐国庆', '浙A9M308', 0,   0,   '西湖区'),
    (11, -1,'17:10', -1,'21:40', 390, 386, '朱小明', '浙A2L617', 4,   96,  '临平区'),
    (12, -2,'09:20',  None, None, 240, 0,  '范建华', '浙A5N902', 0,   0,   '钱塘区'),
    # -013: 昨日收脏 150，今日送净 145（跨日）
    (13, -1,'18:30',  0, '08:30', 150, 145, '马海涛', '浙A7K521', 5,   110, '滨江区'),
    # -014: 当日收当日送，但无司机车牌
    (14, 0, '06:40',  0, '09:50', 90,  90,  None,     None,       0,   0,   '萧山区'),
]

TODAY = '2026-10-03'

def day_of(offset):
    y, m, d = map(int, TODAY.split('-'))
    import datetime
    return (datetime.date(y, m, d) + datetime.timedelta(days=offset)).isoformat()

con = sqlite3.connect(':memory:')
cur = con.cursor()
cur.execute("""CREATE TABLE pickup_order (id INTEGER, pickup_time TEXT, deliver_time TEXT,
    dirty_qty INT, clean_qty INT, driver TEXT, plate TEXT, shortage_qty INT, compensation REAL)""")
cur.execute("CREATE TABLE outlet (id INTEGER, district TEXT)")
for oid, po, pt, doff, dt, dq, cq, dr, pl, sh, comp, dist in ORDERS:
    cur.execute("INSERT INTO outlet VALUES (?,?)", (oid, dist))
    ptime = f"{day_of(po)} {pt}:00"
    dtime = f"{day_of(doff)} {dt}:00" if doff is not None else None
    cur.execute("INSERT INTO pickup_order VALUES (?,?,?,?,?,?,?,?,?)",
                (oid, ptime, dtime, dq, cq, dr, pl, sh, comp))

# 等价于 mapper.daySummary：按自然日 [day, day+1) 半开区间，条件聚合
def day_summary(day):
    sql = """
    SELECT COUNT(CASE WHEN substr(pickup_time,1,10)=?  THEN 1 END)  AS pickup_order_count,
           COUNT(CASE WHEN substr(deliver_time,1,10)=? THEN 1 END) AS deliver_order_count,
           IFNULL(SUM(CASE WHEN substr(pickup_time,1,10)=?  THEN dirty_qty END),0)  AS dirty_total,
           IFNULL(SUM(CASE WHEN substr(deliver_time,1,10)=? THEN clean_qty END),0)  AS clean_total,
           IFNULL(SUM(CASE WHEN substr(deliver_time,1,10)=? THEN shortage_qty END),0) AS shortage_total,
           IFNULL(SUM(CASE WHEN substr(deliver_time,1,10)=? THEN compensation END),0) AS compensation_total
      FROM pickup_order
     WHERE substr(pickup_time,1,10)=? OR substr(deliver_time,1,10)=?
    """
    cur.execute(sql, (day,)*6 + (day, day))
    return dict(zip([d[0] for d in cur.description], cur.fetchone()))

# 等价于 mapper.dispatchBoard：未登记司机/车牌归入兜底分组；DISTINCT 防当日收送双计
def dispatch_board(day):
    driver = "COALESCE(NULLIF(driver,''), '未登记司机')"
    plate = "COALESCE(NULLIF(plate,''), '未登记车牌')"
    pin = "substr(p.pickup_time,1,10)=?"
    din = "substr(p.deliver_time,1,10)=?"
    sql = f"""
    SELECT {driver} driver, {plate} plate,
           COUNT(DISTINCT CASE WHEN {pin} THEN p.id END) pickup_order_count,
           COUNT(DISTINCT CASE WHEN {din} THEN p.id END) deliver_order_count,
           COUNT(DISTINCT CASE WHEN {pin} OR {din} THEN p.id END) order_count,
           IFNULL(SUM(CASE WHEN {pin} THEN p.dirty_qty END),0) dirty_qty,
           IFNULL(SUM(CASE WHEN {din} THEN p.clean_qty END),0) clean_qty,
           IFNULL(SUM(CASE WHEN {din} THEN p.shortage_qty END),0) shortage_qty,
           IFNULL(SUM(CASE WHEN {din} THEN p.compensation END),0) compensation
      FROM pickup_order p
     WHERE {pin} OR {din}
     GROUP BY {driver}, {plate}
     ORDER BY dirty_qty DESC, clean_qty DESC
    """
    cur.execute(sql, (day,)*10)
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, r)) for r in cur.fetchall()]

failures = []
def check(name, actual, expected):
    ok = actual == expected
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}: 实际={actual} 期望={expected}")
    if not ok:
        failures.append(name)

print("== 今日（2026-10-03）日汇总 ==")
s = day_summary(TODAY)
# 收脏: 1..8 + 14（13 是昨日收脏，不能进今日收脏）
check('收脏单量', s['pickup_order_count'], 9)
check('收脏件数', s['dirty_total'], 620+880+1180+310+430+940+705+512+90)  # 5667
# 送净: 1,2,3,5,6,8（原有）+ 13（跨日昨日收今日送）+ 14（无司机当日送）
check('送净单量', s['deliver_order_count'], 8)
check('送净件数', s['clean_total'], 620+872+1156+430+933+502+145+90)       # 4748
check('短少件数', s['shortage_total'], 8+24+7+10+5)                        # 54
check('赔付金额', s['compensation_total'], 168+690+210+320+110)            # 1498

print("== 昨日（2026-10-02）日汇总：13 号单的脏件应在此，净/短少/赔付不应在此 ==")
y = day_summary('2026-10-02')
check('收脏单量', y['pickup_order_count'], 4)        # 9,10,11,13
check('收脏件数', y['dirty_total'], 268+176+390+150) # 984
check('送净单量', y['deliver_order_count'], 3)       # 9,10,11（13 今日才送）
check('送净件数', y['clean_total'], 268+176+386)     # 830
check('短少件数', y['shortage_total'], 4)
check('赔付金额', y['compensation_total'], 96)

print("== 前日（2026-10-01）：仅未送净的 12 号单 ==")
q = day_summary('2026-10-01')
check('收脏件数', q['dirty_total'], 240)
check('送净件数(未送净不进)', q['clean_total'], 0)
check('短少(未送净不进)', q['shortage_total'], 0)
check('赔付(未送净不进)', q['compensation_total'], 0)
check('收脏单量', q['pickup_order_count'], 1)
check('送净单量', q['deliver_order_count'], 0)

print("== 今日配送看板：逐司机 + 未登记分组 ==")
board = { (r['driver'], r['plate']): r for r in dispatch_board(TODAY) }
def row(driver, plate):
    return board[(driver, plate)]
mht = row('马海涛', '浙A7K521')   # 1,5（当日收送）+13（今日送净/昨日收脏）
check('马海涛 收脏单', mht['pickup_order_count'], 2)
check('马海涛 送净单(含跨日13)', mht['deliver_order_count'], 3)
check('马海涛 单量(当日收送不双计)', mht['order_count'], 3)
check('马海涛 收脏件(13号脏件归昨日)', mht['dirty_qty'], 620+430)   # 1050
check('马海涛 送净件(含13号145)', mht['clean_qty'], 620+430+145)   # 1195
check('马海涛 短少(仅13号5件)', mht['shortage_qty'], 5)
check('马海涛 赔付(仅110)', mht['compensation'], 110)

x = row('徐国庆', '浙A9M308')
check('徐国庆 收脏件', x['dirty_qty'], 880+940)       # 1820
check('徐国庆 送净件', x['clean_qty'], 872+933)       # 1805
check('徐国庆 短少', x['shortage_qty'], 15)
check('徐国庆 赔付', x['compensation'], 378)

z = row('朱小明', '浙A2L617')
check('朱小明 收脏(含未送净7)', z['dirty_qty'], 1180+705)  # 1885
check('朱小明 送净(未送净7不算)', z['clean_qty'], 1156)
check('朱小明 送净单量', z['deliver_order_count'], 1)

fjh = row('范建华', '浙A5N902')
check('范建华 收脏(含未送净4)', fjh['dirty_qty'], 310+512)  # 822
check('范建华 送净(只有8)', fjh['clean_qty'], 502)
check('范建华 收脏单量', fjh['pickup_order_count'], 2)
check('范建华 送净单量', fjh['deliver_order_count'], 1)

nodr = row('未登记司机', '未登记车牌')
check('无司机 收脏件', nodr['dirty_qty'], 90)
check('无司机 送净件(不丢失)', nodr['clean_qty'], 90)
check('无司机 单量(当日收送算一单)', nodr['order_count'], 1)

print("== 看板合计必须等于今日日汇总（跨端一致） ==")
check('看板收脏件合计', sum(r['dirty_qty'] for r in board.values()), s['dirty_total'])
check('看板送净件合计', sum(r['clean_qty'] for r in board.values()), s['clean_total'])
check('看板短少合计', sum(r['shortage_qty'] for r in board.values()), s['shortage_total'])
check('看板赔付合计', sum(r['compensation'] for r in board.values()), s['compensation_total'])

print("== 昨日看板：13 号单归马海涛，但只算送净，不算他昨日收脏?（实际是昨日收脏，应算收脏） ==")
by = { (r['driver'], r['plate']): r for r in dispatch_board('2026-10-02') }
m = by[('马海涛', '浙A7K521')]
check('昨日 马海涛 收脏单(9+13)', m['pickup_order_count'], 2)
check('昨日 马海涛 收脏件(150在内)', m['dirty_qty'], 268+150)  # 418
check('昨日 马海涛 送净单(13未送)', m['deliver_order_count'], 1)
check('昨日 马海涛 送净件(不含145)', m['clean_qty'], 268)

print()
if failures:
    print(f"!!! {len(failures)} 项未通过: {failures}")
    raise SystemExit(1)
print("全部验证通过 ✓ （跨日 / 未送净 / 当日收当日送 / 无司机车牌 四类场景）")
