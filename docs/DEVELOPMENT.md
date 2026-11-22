# Copy Bar 详细开发文档

文档对应 0.4-clarity，最后整理于 2026-11-22。下文区分现有代码、实机观察与待开发方案。

## 1. 项目目标及边界

当前目标是在系统包管理查询返回时过滤 ACTION_PROCESS_TEXT 扩展，让用户管理长按文字菜单中的第三方和系统应用扩展。作用域为系统框架，代码运行于 system_server。当前不修改复制、剪切等基础项目，不冻结或禁用提供扩展的应用，不尝试在所有 App 进程中统一过滤任意菜单。

## 2. 工程结构

| 文件 | 职责 |
| --- | --- |
| app/src/main/assets/xposed_init | 传统 Xposed 模块入口 MenuHook |
| MenuHook.kt | 系统查询 Hook、共享配置缓存、返回结果过滤及诊断项 |
| ExtensionProtocol.kt | 管理查询标记、诊断伪组件标识；与 Xposed 类分离，避免设置页加载仅运行时提供的类 |
| Rules.kt | MenuRule JSON 编解码、包名及组件匹配、SharedPreferences 存储 |
| MainActivity.kt | 全局开关、分组列表、包名编辑、框架状态、多内容预览 |
| RuleProvider.kt | 早期方案保留的只读配置 Provider，当前系统 Hook 不使用它 |
| MenuFilter.kt | 早期 App 内菜单过滤辅助代码，当前 MenuHook 不调用它 |
| res/values/arrays.xml | 推荐作用域 android |
| src/test/.../MenuRulesTest.kt | JSON 往返、默认规则、包名/组件过滤的单元测试 |
| src/androidTest | 早期菜单函数及 Provider 测试，不代表当前系统 Hook 端到端覆盖 |

类名中的包路径均为 com.example.copy_bar，当前仍使用示例命名空间，后续更名需要同步入口、authority 和协议常量。

## 3. 运行链路

1. 设置页查询 PROCESS_TEXT + text/plain，通过 MATCH_ALL 包含非默认匹配项，并添加 INVENTORY 标记。
2. system_server 的 MenuHook 只在 packageName 和 processName 均为 android 时安装拦截。
3. Hook 覆盖 ComputerEngine、PackageManagerService、PackageManagerService$IPackageManagerImpl、ResolveIntentHelper 中存在的 queryIntentActivities / queryIntentActivitiesInternal 重载。
4. ThreadLocal 深度只让最外层被拦截的查询处理结果；有异常或非 PROCESS_TEXT 请求直接跳过。
5. 从返回的 List 或包装对象 getList() 获取结果；普通查询按规则复制、筛选；包装类型通过构造器重新封装，不原地修改包管理缓存。
6. 管理查询返回完整查询结果，并追加不可启动的诊断伪组件，展示框架加载和配置读取状态。设置页移除该诊断项后按系统标志分组。

过滤结果只影响经过这些入口的后续查询。已经被应用缓存的结果不会被主动撤销；应用可通过其他接口或直接组件调用继续使用功能。

## 4. 配置结构与优先级

存储文件为 menu_rules。enabled 为总开关，config_ready 为可读性标记，rule: 为当前全局规则的 JSON。JSON 字段：

| 字段 | 类型 | 当前系统模式用途 |
| --- | --- | --- |
| allProcessors | Boolean | 隐藏全部文字处理扩展 |
| packages | 字符串数组 | 完整包名匹配 |
| components | 字符串数组 | ComponentName.flattenToString() 完整匹配 |
| actions | 字符串数组 | 早期遗留；当前不使用 |
| titles | 字符串数组 | 早期遗留；当前不使用 |

满足 allProcessors、包名或组件任一条件即被屏蔽。只有总开关启用时过滤。旧版 rule:包名 独立规则虽保留于存储模型，当前 Hook 只读取全局 rule:，UI 不再提供独立规则入口。

## 5. 跨进程读取与故障处理

Manifest 声明 xposedminversion=93、xposedsharedprefs=true，设置页尝试 MODE_WORLD_READABLE 以使用 LSPosed 的共享偏好重定向；未提供支持时捕获 SecurityException 并回退到私有偏好，框架端可能无法读取，诊断会提示。不能自行 chmod 或假定私有目录在所有 ROM 可访问。

system_server 使用 XSharedPreferences，每次查询检查内存缓存，距上次读取达到 1000ms 才 reload；读取失败时关闭过滤，保留宿主原始结果。同步锁保护缓存，首次过滤异常记录一次日志。查询路径不使用 ContentProvider 反向唤醒设置应用，避免包管理查询、应用启动和 Binder 调用相互等待。

仍需改进：配置重载有文件 I/O；应测量 system_server 路径耗时；诊断对 preferences 的读取与异常策略需要进一步统一。框架内部类名及返回类型不是稳定 API，厂商兼容需持续验证。

## 6. 界面与预览

页面包含全局控制、真实菜单预览、扩展管理和使用提示。预览为 EditText，样例涵盖中文、英文、中英混合、网址、数字、自定义。重建预览销毁旧输入框并重新创建；不会自动模拟长按，也不保证清除所有系统或宿主缓存。

列表以 FLAG_SYSTEM / FLAG_UPDATED_SYSTEM_APP 分类。扩展开关为单项屏蔽状态，已屏蔽/未屏蔽不等同于最终全局生效状态。总开关、隐藏全部和包名规则可能改变实际结果，UI 已提示这一点。刷新仅更新列表和诊断，不重置用户输入。

## 7. 小米 HyperOS 调查记录

只读诊断环境：Android 16，HyperOS OS3.0.7.0.WOBCNXM；超级小爱 7.8.63.0017；MiuixEditor versionName 16 / versionCode 36。导出 APK 和反编译副本仅用于本地诊断，不随仓库发布。反编译存在少量错误，以下结论来自成功还原的方法与实时窗口观察。

### 7.1 已确认的机制

- 超级小爱 com.miui.voiceassist 未注册标准 ACTION_PROCESS_TEXT。
- 菜单显示时窗口 package 标记为 com.miuix.editor，但 session 与宿主 UID/PID 一致；不能据此认为菜单在独立 editor 进程运行。
- MiuixEditor 的 miuix.toolbar.TextActionManager 动态加载 Phrases、Classify、Select、AiRecognition、AskHyperXiaoai、Query、Translate 等操作类，调用 attach/invalidate。
- AskHyperXiaoai 检查超级小爱 ApplicationInfo.metadata 的 xiaoai.supportAIEditorWakeUp 与 is_super_ai，再生成入口；点击后调用 com.xiaomi.voiceassistant.VoiceService。
- AiRecognition 也读取相同 metadata 并创建 ai_recognition 菜单项，涉及 AI 写作相关逻辑。
- Translate 包含独立的小爱翻译路径，检查 com.xiaomi.aiasst.vision 并调用其 AiTranslateService，因此不能把所有小爱菜单都归于 com.miui.voiceassist。

### 7.2 为什么冻结有效、包名规则无效

冻结影响整包组件和可用性判断；现有规则只删除标准 PROCESS_TEXT 查询结果。小米直接构造的项目不在该返回列表中，故不会匹配。改变超级小爱全局 metadata 或伪造应用安装状态可能误伤其他功能，不作为当前修复实现。

### 7.3 尚未实现的方案

可以在 MiuixEditor 操作加载、生成或刷新处动态收集操作类与菜单标识，提供已发现菜单列表，再按稳定标识过滤。应优先使用操作类名与菜单 ID，标题仅用于展示；不记录选中文字。功能依赖语言、编辑状态和应用，必须允许多次触发逐步发现。

动态类加载路径、结果汇总和配置通道尚未验证，作用域仍未解决。只勾选系统框架能否覆盖这些专用项目没有结论；直接勾选 com.miuix.editor 也不保证覆盖在宿主中加载的代码。以上仅为候选开发方向，当前版本未加入任何小米专用 Hook。

## 8. 构建与签名

JDK 21；Gradle 9.4.1；AGP 9.2.1；compileSdk 36.1；targetSdk 36；minSdk 31。Kotlin 使用 AGP 内置支持。SDK 路径放入本地 local.properties，不能提交。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:assembleRelease
```

Release 默认未签名，当前 optimization.enable=false。正式发布前自行选择长期保管的密钥，使用 SDK zipalign/apksigner 工具签名和验证；升级安装必须保持签名兼容。仓库没有密钥与密码。之前个人测试 Release 由本地 Debug 密钥手动签署，不能视为正式分发密钥。若未来启用 R8，需要显式保护 Xposed 入口和反射依赖并重新验证。

## 9. 验证现状与回归建议

现有记录：7 项本地测试通过（含 1 项模板算术测试），Debug/Release 构建通过，lint 无阻塞错误但有警告，签名测试包通过 apksigner 与对齐检查。用户反馈标准扩展过滤基本可用。早期 Android 13 的菜单函数/Provider 设备测试不能替代新系统层过滤的实机验证。

后续重要回归：开关关闭不筛选、组件屏蔽不误伤同包兄弟组件、包名规则覆盖同包所有扩展、管理列表仍显示被隐藏项、配置不可读时保持原结果、无 PROCESS_TEXT 请求完全不受影响、重启后规则保留、不同返回类型与嵌套调用正常、中文/英文菜单一致性。

设备测试可能安装主包与测试包，并可能执行清理。项目默认只做静态检查；编译与设备操作分别需要任务明确授权。禁止把 connectedAndroidTest 当作无副作用的检查命令；不自动安装、卸载、清空、覆盖数据、修改作用域或重启设备。

## 10. 未完成事项

- [ ] 小米专用菜单过滤，以及按实际发现操作动态管理。
- [ ] 验证小米动态加载机制，确定最小有效作用域。
- [ ] 完整 system_server 过滤端到端测试及更多厂商/Android 版本兼容矩阵。
- [ ] 管理查询标记鉴权与诊断协议改进；目前标记不是安全边界。
- [ ] 测量配置重载和过滤耗时，评估包管理缓存失效策略。
- [ ] 迁移并清理遗留 MenuFilter、RuleProvider、独立规则字段与模板资源。
- [ ] UI 展示最终有效屏蔽原因，区分单项、包名、全隐藏和总开关。
- [ ] 搜索、规则导入导出、已失效组件清理、多用户及工作资料支持。
- [ ] 规则变化的实时反馈、加载失败的更精确诊断。
- [ ] 正式签名方案、可复现发布流程、CI 与发行包管理。

## 11. 发布与隐私边界

只发布项目自身代码与文档。diagnostics、本地日志、设备导出 APK、反编译第三方源码、构建缓存、本地 SDK 配置、IDE 状态及签名密钥均不提交。模块自身不主动记录所选文字；系统输入控件和扩展行为不由本项目控制。规则不应包含敏感文本。
