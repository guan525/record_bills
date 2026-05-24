# 自动账本 0.1.0

## 已交付

- Android App 名称：自动账本
- 包名：`com.autobook.ledger`
- APK：`dist/auto-ledger-debug.apk`
- 本地 Room/SQLite 账本
- 首页、明细、统计、来源、设置五个主页面
- 手动记账、待确认、确认、忽略、删除
- 支付通知自动识别服务
- 银行/信用卡短信识别接收器
- 手机已安装消费来源扫描
- 细分类规则和解析器单元测试
- Supabase REST 同步客户端
- Supabase SQL 建表脚本：`supabase/schema.sql`
- CSV 本地导出

## 使用前需要操作

1. 在手机系统设置里允许 `自动账本` 的通知使用权。
2. 在 App 来源页授权短信读取/接收。
3. 在 Supabase SQL Editor 执行 `supabase/schema.sql` 后再点设置页的立即同步。

## 已知限制

- 只要来源 App 的通知或短信没有金额，第一版无法强行读取该笔消费。
- 自动识别的账单默认进入待确认，需要用户确认后才计入正式支出统计。
- 当前同步使用换机密钥，不是邮箱/手机号登录。
- 当前 APK 是 debug 签名，适合本机安装测试，不适合作为应用商店发布包。

