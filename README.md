# 屏学助手 ScreenCoach（DeepSeek 单机版）

一个 Android 11+ 的**学习辅助**原型：按可配置的 API 调用频率（默认 3 秒）截取当前页面，直接调用 **DeepSeek 官方多模态 API**（`deepseek-flash`）答题或翻译；支持本机题库/翻译词库、本地优先匹配、默认/翻译双模式，答案写入 SQLite 临时库，以“颜色块 + 超大文字”的悬浮窗或常驻通知显示。

> 完全单机：不需要电脑、不需要 MCP 桥接、不请求任何非 DeepSeek 的模型接口。
> 这不是自动答题器：不会点击其他应用、不会模拟输入、不会绕过平台限制。答案可能出错，请自行判断。仅用于个人学习、错题复盘，并遵守你所用平台/课程的规则。

## 一、功能与需求对应

| 需求 | 实现 |
|---|---|
| 完全单机 | 所有识别、缓存、悬浮窗逻辑在 APK 内完成；不依赖外部服务 |
| 只接 DeepSeek API | 固定 `https://api.deepseek.com`，固定识图模型 `deepseek-flash`，无自定义 Endpoint |
| SQLite 临时答案库 | `AnswerCache`，成功答案立即落库，24 小时后清理；绿色只给已落库答案 |
| 启动时清空答案库 | 首页复选框“启动悬浮窗时清空答案库（SQLite）”，在成功授权并启动识别前清空 |
| 悬浮窗极限显示文字 | 大悬浮窗最大 520dp 宽；答案 24–72sp 自动缩放、最多 3 行；解析文字最多 6 行，可自动缩放 |
| 下一题大热区 | 大悬浮窗底部 82dp 高、全宽的“下一题 ▶”按钮；按下震动 + 缩放反馈 |
| 全局 FLAG_SECURE 开关 | 首页开关，默认开启；立即作用于应用窗口、对话框和悬浮窗，也可在识别中切换 |
| 小型像素浮窗 | 首页选择“小型像素悬浮窗”；白色底板，窗口约 60×44 px，字号 ≤16 px，内容只有“题号+答案 + 1px 状态色点”（无 L）；默认点击整个浮窗循环已缓存答案 |
| 位置调节与预览 | 首页“小悬浮窗位置”用四个方向按钮每次移动 1px，可重置；点击“预览位置”会在真实屏幕坐标显示白色样窗（不可触摸），再点关闭 |
| 覆盖状态栏 | 首页开关，默认关闭；关闭时小窗最低到状态栏下方，开启后可移动到屏幕顶部/刘海区域（受 ROM 策略限制） |
| 思考强度 | 首页 Off / Low / High，默认 Off；Low/High 发送 DeepSeek thinking 参数和对应 reasoning_effort |
| 模式切换 | 默认模式答题；翻译模式输出译文；翻译模式有二级菜单选择语言，默认“中 ⇄ 英”双向 |
| 题库与翻译词库 | 本机 SQLite `screencoach_knowledge.db`；首页独立导入对话框；题库存题目/答案，词库存原文/译文 |
| 题库导入来源 | 拍照、相册照片、文件；照片由 DeepSeek 识图解析，文件本地解析 |
| 本地匹配开关 | “相似题目优先使用题库”和“匹配翻译优先使用词库”分别控制默认模式/翻译模式是否先查本地 |
| 用户搜题模式 | 仅小型浮窗可用；开启后点击整个浮窗会立即请求当前页面并绕过缓存，只显示当前页面答案，不再循环旧答案 |
| 常驻通知模式 | 首页选择“常驻通知（无悬浮窗）”；不创建悬浮窗，前台通知每 4 秒轮换一次答案，只显示题号+答案+状态色块 |
| 仅答案输出 | 小浮窗和通知模式不显示解析、过程或额外内容；多选题/长答案会短暂拉宽小浮窗，4 秒后收回 |
| MediaProjection / 无障碍截图 | 两种启动方式；MediaProjection 失败自动回退无障碍截图，反之亦可 |
| 无障碍保活 | 30 秒心跳 + 每 15 分钟系统 Alarm 检查 + 异常通知；不绕过系统、不强行自启 |
| 可配置调用频率 | 首页填写 API 调用频率，单位秒，默认 3、范围 1–300；自动截屏与失败重试都使用该间隔 |
| 颜色语义 | 黄=截屏/请求中；绿=答案已落库；红=网络/DeepSeek/JSON 协议错误 |

颜色块示例：`2C` + 黄色表示“正在尝试截屏第 3 题，上一题答案是 2C”；成功后变成绿色；网络或协议错误变红并按设定频率重试。

## 二、DeepSeek API 配置

应用内只需要填一个 **DeepSeek API Key**。

- 官方接口：`https://api.deepseek.com/chat/completions`
- 识图模型：`deepseek-flash`
- 请求方式：OpenAI-compatible `image_url` + base64 data URL，并启用 `response_format: json_object`
- 思考强度：Off 发送 `thinking.type=disabled`；Low/High 发送 `thinking.type=enabled` 与 `reasoning_effort=low/high`（默认 Off）
- 翻译模式：使用同一 `deepseek-flash` 视觉接口，返回 `translation` JSON；默认中英双向，也可在首页二级菜单选择中→英、英→中、中→日、中→韩、中→法、中→德、中→西等方向
- 本地题库/词库：命中且对应开关开启时不发起 API 请求；未命中才调用 DeepSeek
- 测试连接：调用 `GET https://api.deepseek.com/models`，检查 Key 和 `deepseek-flash` 是否可见
- API Key 存储：Android Keystore AES-GCM 加密后的 SharedPreferences

> DeepSeek 的图像输入、`deepseek-flash` 模型名及 JSON Output 能力以官方文档为准：
> [图像理解](https://api-docs.deepseek.com/zh-cn/guides/vision/) ·
> [JSON Output](https://api-docs.deepseek.com/zh-cn/guides/json_mode/) ·
> [获取模型列表](https://api-docs.deepseek.com/zh-cn/api/list-models/)

## 三、构建与安装

```bash
export ANDROID_HOME="$HOME/tools/android-sdk"
./scripts/build_apk.sh
# 产物：dist/ScreenCoach-1.0.0-debug.apk
```

也可以用 Android Studio 打开项目运行 `assembleDebug`。工程无第三方 Android 依赖，compileSdk 33 / minSdk 30 / targetSdk 33。

## 四、使用步骤

1. 安装 APK，打开“屏学助手”。
2. 授予**悬浮窗权限**和**通知权限**。
3. 在系统无障碍设置中启用“屏学助手读屏”（Android 13 侧载应用可能还需要在应用信息中允许“受限设置”）。
4. 在首页填入 **DeepSeek API Key**，设置 **API 调用频率（秒）**（默认 3，范围 1–300），点“保存设置”，再点“测试 DeepSeek”。
5. 需要每次开始都清空旧答案时，勾选“启动悬浮窗时清空答案库（SQLite）”。
6. 在“识别模式与本地词库”选择**默认模式**或**翻译模式**；翻译模式会显示“翻译语言”二级菜单，默认“中 ⇄ 英（默认双向）”。
7. 点“题库 / 翻译词库导入…”打开独立对话框：
   - 题库：拍照、相册、文件导入；
   - 翻译词库：文件导入；
   - 可分别查看条数或清空。
8. 需要本地优先时保持“相似题目优先使用题库”“匹配翻译优先使用词库”开启；修改后下次启动识别生效。
9. 在“显示与隐私”中选择显示方式，必要时勾选“小型浮窗：用户搜题模式”：
   - **大悬浮窗**：详细解析 + 大“下一题”按钮；
   - **小型像素悬浮窗**：白色底板，只显示题号+答案+1px 状态色点（无 L），默认点击整个浮窗循环缓存答案；
   - **用户搜题模式**：仅小型浮窗生效；点击整个浮窗立即请求当前页面并绕过缓存，只显示当前页面答案。自动轮询仍按 API 频率执行。
   - **常驻通知**：无悬浮窗，通知每 4 秒轮换答案。
10. 需要时选择“思考强度”：Off（默认）/ Low / High；Low/High 会增加 DeepSeek 思考耗时和费用，修改后下次启动识别生效。
11. 需要时关闭“全局 FLAG_SECURE”（默认开启）；关闭后本应用和悬浮窗可能出现在截图/录屏中。
12. 回到题目页面，点“开始识别”或“仅用无障碍截图启动”。
13. 需要调整小窗位置时，在“小悬浮窗位置”区域用方向按钮逐像素移动；可点“预览位置”在真实位置查看白色样窗，调整后下次启动识别生效。
14. “允许小悬浮窗覆盖状态栏”默认关闭；关闭时坐标最低到状态栏下方，开启后可移动到屏幕顶部/刘海区域。修改后下次启动生效，预览立即更新。
15. 颜色含义：
   - 绿色：当前答案已落库；
   - 黄色：正在截屏/请求下一题（小模式/通知保留上一条答案）；
   - 红色：请求或 JSON 协议失败，按 API 调用频率自动重试；大悬浮窗点色块可立即重试。
16. 大悬浮窗上方区域可拖动；底部“下一题”按钮立即推进题号并截屏；翻译模式下该按钮变为“重新翻译”。小浮窗没有额外按钮，点击它循环已缓存答案；开启用户搜题模式后点击会请求当前页面。

## 五、题库 / 翻译词库格式

文件选择器支持 JSON / CSV / TSV / TXT。题库前两列为“题目、答案”，词库前两列为“原文、译文”；首行表头会自动跳过。

题库 JSON：

```json
[
  {"question": "1+1=?", "answer": "2"},
  {"question": "法国的首都是哪里？", "answer": "巴黎"}
]
```

词库 JSON：

```json
[
  {"source": "hello", "target": "你好"},
  {"source": "good morning", "target": "早上好"}
]
```

题库照片由 DeepSeek 识图解析成上述 Q&A 条目。题库/词库保存在本机 SQLite；答案缓存清空不会删除题库/词库。

## 六、DeepSeek 返回协议

App 要求模型返回严格 JSON：

```json
{
  "status": "ok",
  "question_no": 2,
  "answer": "C",
  "answer_text": "简要解析/选项内容",
  "confidence": 0.93,
  "all_known": true
}
```

- 选择题 `answer` 建议只给字母，多选如 `"AC"`；填空/解答给简洁答案字符串。
- `status` 为 `unknown`/`error`、缺少 `answer`、JSON 不合法、API 返回空内容，都会显示红色错误并按 API 调用频率重试。
- `answer_text` 会显示在悬浮窗中，最多 6 行，自动缩放。
- App 对返回 JSON 做校验，不依赖模型返回 Markdown。

翻译模式要求返回：

```json
{
  "status": "ok",
  "translation": "最终译文",
  "source_language": "zh",
  "target_language": "en",
  "confidence": 0.95
}
```

翻译模式只展示 `translation`，不展示思考过程或额外解析。

## 七、本地匹配规则与限制

- 题库匹配基于无障碍读屏文本：先做归一化，再用整句包含和字符二元组相似度匹配；同题但页面文本缺失时不会本地命中。
- 翻译词库按原文/译文的包含关系匹配；双向模式会同时检查两个方向，方向模式只检查原文列。
- 本地匹配最多加载各 2,000 条最近导入记录；更多条目仍保存在数据库中，但不会参与内存匹配。
- 照片题库解析需要 DeepSeek API Key，且会把图片发送给 DeepSeek；文件导入完全本地解析。
- PDF、Excel、Word 不直接解析；请先导出为 CSV/TSV/TXT/JSON。
- 本地匹配可能误命中短题干或短词条；可在首页关闭对应开关或清空词库。
- 修改题库/词库、匹配开关、模式或翻译语言后，正在运行的识别需要停止再启动才能重新加载。

## 八、验证记录

在 Waydroid（Android 13 / API 33）上验证了：

- 用本地 DeepSeek 兼容 Mock 服务验证直连链路：`/models` 测试通过，`/chat/completions` 收到图片并返回绿色 `1B`；
- 空 API Key / 请求失败时显示红色并按配置频率重试；有效 Key 时显示绿色；
- 新增 API 调用频率输入、用户搜题模式开关和对应持久化逻辑；最终 APK 重新编译通过。
- 点“下一题”大按钮后成功推进题号并重新请求（此前版本验证显示 `2C`，UI/引擎路径未变）；
- 勾选“启动悬浮窗时清空答案库”后，日志显示启动前清除了 1 条旧答案，新答案重新落库后显示绿色；
- 小悬浮窗为白色底板、无 L，状态色点缩为 1px；窗口尺寸和像素字渲染已编译核对，位置按钮/预览层使用同一套 `CompactGeometry` 与 `CompactPixelView`；
- 关闭 FLAG_SECURE 后小悬浮窗可被 `adb screencap` 捕获（此前版本已实测）；白板与 1px 状态点为本次改动，已完成编译核对；
- 用户搜题模式仅改变小型浮窗热区：点击时调用 `userSearch()`，跳过缓存并只展示当前页；自动频率仍独立生效；
- 新增小悬浮窗坐标系（与旧大悬浮窗位置相互独立）、1px 方向按钮和位置预览窗口；
- 新增“允许小悬浮窗覆盖状态栏”开关，默认关闭；通过 `FLAG_LAYOUT_NO_LIMITS`、fit-insets 0 和 cutout ALWAYS 实现，预览同步；系统可能仍限制覆盖；
- 思考强度 Off/Low/High 已接入 DeepSeek 请求体，默认 Off；首页选择与持久化逻辑已编译通过。
- 新增“允许小悬浮窗覆盖状态栏”开关，默认关闭；窗口使用 `FLAG_LAYOUT_NO_LIMITS` / fit-insets 0 / cutout ALWAYS，并与位置预览共用同一套坐标与钳制逻辑。部分 ROM 仍可能强制限制。
- 在 Waydroid 上实测：默认/翻译模式切换正常，翻译模式显示语言二级菜单（15 个方向），默认中英双向；
- 在 Waydroid 上实测：独立导入对话框可打开；导入 `questions.csv` 后题库 2 条，导入 `glossary.csv` 后翻译词库 2 条，计数正确；追加导入后为 2 题库 + 3 词库；
- 在 Waydroid 上实测：翻译模式的大悬浮窗显示“翻译”标题和“重新翻译”按钮；为避免把助手自身界面当题目，引擎会跳过屏学助手前台（红色提示），这是预期保护；
- 关闭 FLAG_SECURE 后截图可见悬浮窗；重新打开后截图恢复黑屏/不可见；
- 照片题库导入、真实翻译 API 和真实页面的本地命中链路未在本次真实 Key 下验证；需要真实 DeepSeek Key 与可读屏页面。
- 实测把频率设为 60 秒后，小型浮窗用户搜题模式点击立即产生 `DeepSeek API Key 无效` 的红色错误（绕过已有缓存），6 秒内没有自动重试，证明手动请求立即执行且自动间隔读取配置值；
- 常驻通知模式实测不创建应用悬浮窗，通知内容为 `1B 🟩`，并按 4 秒刷新；
- FLAG_SECURE 开关实测可在窗口属性中添加/移除 `SECURE`；
- MediaProjection 授权、采集、悬浮窗显示正常；
- API Key 经 Android Keystore 加密后可跨进程重启读取；
- 手工构建成功；新增显示模式后重新编译通过。Gradle lint 因本次环境下载 Gradle 卡住未重跑（此前版本 Lint 为 0 errors）。

> 由于没有可用的真实 DeepSeek Key，直连链路使用本地 OpenAI-compatible Mock 验证，未发起真实计费请求。真实 DeepSeek 请求格式遵循其[图像理解文档](https://api-docs.deepseek.com/zh-cn/guides/vision/)。

*由于悬浮窗设置了 FLAG_SECURE，`adb screencap` 不会包含悬浮窗内容。*

## 九、限制与安全

- 需要 Android 11+。
- 只支持 DeepSeek 官方 API；如果 DeepSeek 账号没有 `deepseek-flash` 权限、余额不足或模型下线，会显示红色错误。
- 图片与题面会发送给 DeepSeek；请勿识别包含密码、支付信息等敏感内容的页面。
- API Key 在静态存储时加密，但传输安全依赖 DeepSeek 官方 HTTPS；请勿把 Key 填入非官方客户端。
- 本应用所有窗口使用 FLAG_SECURE，但 root、系统级录屏、外接采集卡等不受此保证。
- 无障碍服务必须由用户手动启用；保活模块只能提醒，不能规避 Android 的权限模型。
- 部分页面（例如系统设置）会设置 `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`，此时悬浮窗会被系统临时隐藏。
- 关闭 FLAG_SECURE 后，小悬浮窗/大悬浮窗会被系统截图收录；这由用户主动选择，敏感场景请保持开启。
- 小悬浮窗的物理字号仅约 16 px，在高 DPI 手机上非常小；可点击区域通过透明边缘扩展，若仍难以点击请使用大悬浮窗或通知模式。
- 小悬浮窗位置按钮每次移动 1px，调整的是保存坐标；正在运行的识别需停止再启动后应用新位置，预览会即时更新。
- 位置预览是系统悬浮窗，需要悬浮窗权限；识别期间不提供预览，以免与实际小窗重叠。
- 覆盖状态栏依赖 ROM 对 `TYPE_APPLICATION_OVERLAY` 的策略；部分系统会强制把窗口限制在状态栏下方，即使开关已开启。
- 思考模式会增加模型耗时和费用，且思考内容的处理由 DeepSeek 官方 API 负责；本应用只读取最终 `content` JSON。
- 常驻通知模式依赖通知权限和通知渠道未被关闭；部分系统会折叠较长通知。
- MediaProjection 虚拟显示在启动时按当前方向创建；极端旋转/折叠屏切换可能需要重启识别。
- 项目 targetSdk 为 33，以兼容前台服务行为；Android 14+ 的部分 ROM 若对无障碍模式启动有限制，请改用 MediaProjection 模式。
- SQLite 答案库位于应用私有目录且禁用了备份；答案本身未额外加密，API Key 使用 Android Keystore 加密。

## 十、目录

```
screencoach/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/moiclathy/screencoach/
│   │   ├── MainActivity.java
│   │   ├── data/            # SQLite 缓存、题库/词库、设置、匹配、导入解析
│   │   ├── net/             # DeepSeekClient（唯一的模型出口，含翻译/图片题库）
│   │   ├── service/         # 前台服务、MediaProjection、无障碍、悬浮窗、相机文件 Provider
│   │   └── util/            # Keystore、Bitmap、UI 工具
│   └── res/
├── scripts/build_apk.sh
├── dist/
└── README.md
```

## 十一、许可

仅作个人学习用途的示例工程。DeepSeek API 的服务条款、计费与数据处理规则另行适用。
