# 自动账本

Android 本地优先自动记账 App。第一版支持通知监听、短信账单识别、已安装消费来源扫描、手动记账、待确认账单、分类统计、本地 Room 数据库、Supabase 同步和 CSV 备份。

## 当前 APK

构建产物：

```bash
dist/auto-ledger-debug.apk
```

安装命令：

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r dist/auto-ledger-debug.apk
```

## 手机权限

首次打开后进入 `来源` 页：

1. 点 `去开启`，在系统通知使用权页面允许 `自动账本`。
2. 点短信 `授权`，允许读取和接收短信。
3. 回到 App 点 `重新扫描`，查看手机上可能产生消费的来源 App。

自动识别并不读取其他 App 的私有数据。它只处理 Android 通知内容和短信内容。若某个支付来源没有在通知或短信里展示金额，App 无法绕过系统限制读取。

## Supabase 设置

App 已内置：

- Project URL: `https://juttwaujeylfspinawzk.supabase.co`
- Publishable key: `sb_publishable_Gf4WK-3HkJxexTeoRztArA_u50zo-gf`

同步前需要在 Supabase 控制台执行一次：

```sql
-- 复制并执行 supabase/schema.sql
```

执行后，在 App `设置` 页点击 `立即同步`。App 会生成一个换机同步密钥。换手机时，在新手机安装 App，填入同一个同步密钥，再点击同步即可拉取同一批账本数据。

## 本地数据

所有账单先写入手机本地 Room/SQLite 数据库。Supabase 只是同步和换机恢复层。即使网络不可用或 Supabase 未建表，本地账本仍可继续使用。

## 开发命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

