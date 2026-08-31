@echo off
chcp 65001 >nul
echo ========================================
echo  农批履约中台 — 接口冒烟测试
echo ========================================
echo.
set BASE_URL=http://localhost:8080

echo === 1. 登录获取 JWT Token ===
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "$r = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body '{\"username\":\"admin\",\"password\":\"admin123\"}'; $r.token"`) do set TOKEN=%%t
if "%TOKEN%"=="" (
    echo [失败] 登录失败，请确认后端已启动在 %BASE_URL%
    goto :end
)
echo [成功] Token 已获取（长度 %TOKEN:~0,8%...）

echo.
echo === 2. 入库（FRESH，100kg） ===
curl -s -X POST %BASE_URL%/api/lots/inbound ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"skuId\":1001,\"tempZone\":\"FRESH\",\"produceDate\":\"2026-07-18\",\"expireDate\":\"2026-08-18\",\"qty\":100,\"supplierId\":1}"
echo.

echo === 3. 出库（FRESH，30kg → A-01） ===
curl -s -X POST %BASE_URL%/api/lots/outbound ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"skuId\":1001,\"tempZone\":\"FRESH\",\"qty\":30,\"toLocation\":\"A-01\"}"
echo.

echo === 4. 批次列表（第1页，每页10条） ===
curl -s -H "Authorization: Bearer %TOKEN%" "%BASE_URL%/api/lots/list?page=1&size=10"
echo.

REM 从列表响应中提取第一个 lotNo（需要 jq，若无则提示手动替换）
echo === 5. 批次详情（请从步骤4的响应中复制 lotNo 替换） ===
echo curl -s -H "Authorization: Bearer %TOKEN%" %BASE_URL%/api/lots/LOT20260718001
echo.

echo === 6. 预警检查 ===
curl -s -X POST %BASE_URL%/api/alerts/check ^
  -H "Authorization: Bearer %TOKEN%"
echo.

echo === 7. 创建预警规则 ===
curl -s -X POST %BASE_URL%/api/alerts/rules ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"skuId\":null,\"tempZone\":\"FRESH\",\"thresholdDays\":3,\"alertLevel\":\"WARNING\"}"
echo.

:end
echo ========================================
echo  冒烟测试完成
echo  前提：后端已启动在 %BASE_URL%
echo ========================================
