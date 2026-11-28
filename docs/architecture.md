# 实现与调用链

## 原生组件定位

当前模块复用手机横屏与平板共用的原生按钮组装流程，同时沿用 `EdgeToolbarPhone` 和 `LocationBarPhone` 的地址输入及页面联动。完整平板工具栏使用 `EdgeToolbarTablet → ToolbarTablet`，其初始化绑定独立的主页、后退、刷新、收藏按钮和 `toolbar_tablet_layout`，测量时按按钮队列分配宽度。完整复用该布局涉及工具栏资源、控件类型和测量链路的整体接入。

`EdgeEntry.onPackageReady` 取得已安装 Edge 的 `ApplicationInfo` 和类加载器。工具栏和新标签页的原生类可由类加载器解析后，模块才通过 `NativeDex` 读取主 APK 与 split APK。Chrome 分包尚未就绪时，模块在 Activity 启动期间用对应的类加载器重试。

反射提供已知类型的成员访问；`NativeDex` 补齐已安装 APK 内部类型及调用关系的查询。它读取 DEX 元数据和相关方法的调用指令，`NativeRoles` 按以下原生职责定位。`NativeBindings` 将解析结果转成反射成员，交给现有挂钩流程；DEX 索引随后交由垃圾回收。

- 标签协调器：原生 `TabStackButton` 构造参数、点击监听器和 `ActivityTabProvider` 绑定签名。
- 菜单协调器：原生 `EdgeToolBarOverflowButton` 构造参数，以及点击、长按、标签供应器绑定签名。
- 顶栏组装：`ToolbarLayout` / `ToolbarControlContainer` 字段，以及创建 `edge_toolbar_tab_stack_button_stub`、`edge_toolbar_plus_button_stub` 的原生流程。
- 左右按钮列表：原生 `setLeftIconCoordinators` / `setRightIconCoordinators` 写入的列表字段。
- 底栏协调器：原生 `EdgeBottomNavBarLayout`、资源容器类型，以及读取设备形态后更新可见性的调用。Edge 158 在同一流程写入布尔状态，因此定位不依赖整数状态字段。
- 主题刷新：原生页面控件对 `ImageView.setImageTintList`、相机图标资源等的调用关系。
- 布局资源：按原生资源字段名称读取实际 ID，包括标签按钮、菜单按钮和图标内边距资源。
- 地址对齐：从 `LocationBarPhone` 的 `(boolean) → void` 方法对 `View.getLayoutParams`、`setPaddingRelative` 和 `setLayoutParams` 的调用定位原生约束更新入口，其零参数调用者承担地址对齐刷新。沿刷新流程及其本类布尔调用者定位 `(int) → boolean` 页面对齐分类方法；刷新及 `onMeasure` 在当前线程标记所属地址栏，分类挂钩在已适配网页的这段调用期间返回 `false`。原生流程据此统一维护对齐状态、约束和文字测量，居中选项继续由现有文字呈现挂钩处理。地址对齐刷新、布尔调用者和测量入口执行去优化。文字呈现方法按 `TextView.setGravity` 和滚动设置定位；原生文字测量入口由其对文字呈现方法的调用关系定位。地址栏内边距资源与工具栏的其他资源一样，按已安装 APK 的资源字段名解析。
- 原生转场：按 `(int, HubContainerView, float, float, Interpolator, DoubleConsumer)` 参数定位淡入淡出工厂，并通过原生标签面板的低内存分支调用关系定位进入动画及其父类实现。`EdgeEntry → invokeSpecial 调用原生父类进入工厂 → 起始值取结束值 → 原生 AnimatorSet 时长 0 → 原生结束回调` 直接采用结束态。前台新标签页按写入 `NewForegroundTabAnimationHostView` 动画字段的 Runnable 定位启动任务，经原生任务持有的布局控制器取得宿主、展开动画和淡出动画，执行 `end()` 完成原生选择、移除与回调。现有 `setTabSwitcherMode` 回调同时结束工具栏状态 ValueAnimator。新标签页 URL、页面、搜索框、用户模式与页面设置由 Edge 原生流程负责。
- 导航状态：按 `ToolbarLayout` 数据接口的标签与网址返回类型定位地址数据提供器，按 `LocationBarModel` 的 `Tab` 与 `GURL` 字段类型取得当前标签和已有网址；从原生 `TabImpl.getUrl` 的调用关系取得 `WebContents` 当前可见网址入口。
- 配置生命周期：从原生 `onConfigurationChanged(Configuration)` 对图标方向更新和 `EdgeToolbarPhone.setOrientation` 的调用关系定位工具栏配置观察者，并定位这个回调采用的原生设备形态判断。

定位规则使用原生资源名称、保留的控件类型、参数类型及调用关系。内部类名、方法名和字段名均从当前 APK 解析。

## 布局与调用链

1. `EdgeEntry` 在原生顶栏组装期间选择 Edge 已有的紧凑按钮分支，并捕获协调器、原生绑定方法和原始参数，交给 `NativeActions.Binding`。
2. `NativeBar.update(config) → arrange(roles)` 使用横屏布局的原生 `toolbar_left_buttons` 和 `toolbar_buttons` 槽位。默认标签按钮和菜单留在原位置。自定义按钮跨槽位或改变同槽位顺序时才移动，继续使用原生测量、点击监听器和计数模型。`NativeToolbar.regularNewTabPage` 先读取原生地址数据提供器的 `isIncognito()`，普通模式从 `WebContents` 原生可见网址识别新标签页，初始化期间读取地址模型已有的网址。普通新标签页采用页面内按钮，其余页面采用顶栏。
3. `LocationBarPhone.onMeasure → 原生布局助手 → 原生地址栏约束更新 → 原生文字测量 → 原生文字呈现 → ConstraintLayout.onMeasure` 构成地址栏测量链路。网页采用原生左侧约束分支，`StatusView` 和原生操作按钮按可见性确定文字边界。左对齐执行原生文字呈现；居中通过同一入口传入居中选择、文字是否容纳及 `MATCH_CONSTRAINT`，由原生约束分配宽度。内边距、状态视图与操作按钮保留原生布局值。文字是否容纳根据文本宽度、实测区域和原生内边距判断，区域宽度变化时请求原生布局。原生文字测量入口执行去优化，让原生呈现调用在运行时采用当前对齐选项。输入态继续执行原生测量。
4. `LocationBarPhone.onMeasure → NativeToolbar.updateAddressStart(browsing) → 原生约束测量` 在使用单行工具栏浏览时，将 `location_bar_icon_starting_padding` 作为地址栏的起始内边距下限。若 Edge 已应用更大的原生内边距，则保留现有值。原生图标槽宽度和内部约束按布局资源测量，输入态、原生新标签页和关闭模块时恢复地址栏原有内边距。
5. 新标签页通过原生按钮布局和协调器创建按钮，复用原生绑定参数与顶栏样式对象。原生页面主题刷新后，读取页面按钮或文字已有的 `ColorStateList`，交给原生按钮使用。
6. 底部协调器更新期间，选用它已有的设备形态隐藏分支；原生流程同步底栏、场景与浏览器控件栈。退出适配时，按钮回到原位置和原布局参数。
7. `ConfigBridge.save(context, enabled, order, ntpPosition, addressAlignment)` 保存设置并通过 Xposed 服务写入远程偏好，Edge 主进程收到变化后执行 `EdgeEntry.reload → NativeBar.update`。对齐选项变化时请求原生地址栏重新布局。
8. 扩展设置独立于工具栏挂钩。`ExtensionSettings` 把入口加到 Edge 的设置列表，通过 Edge 原生导航打开带专用参数的设置页面，复用原生设置标题、列表和 Dewey 设置项。内置目录通过原生 Tab 导航打开，安装由 Edge 处理。商店入口使用原生设置项的 Intent 跳转。
9. 原生工具栏配置观察者收到旋转、窗口尺寸等配置变化时，`NativeAssembly.enterConfiguration → 原生配置回调 → leaveConfiguration` 选择原生紧凑按钮分支。`takeConfigurationFormFactor` 在该回调选择按钮行形态时消费一次标记，随后嵌套控件继续读取实际设备形态。原生菜单和图标协调器完成可见性更新，`setOrientation` 继续接收实际屏幕方向；合并按钮按视图身份关联原生协调器。
10. `ToolbarPhone.onMeasure → NativeBar.nativeStateChanged → 原生 onMeasure` 在测量前同步当前页面所需的按钮布局，原生布局过程测得按钮区域的实际尺寸。原生创建、供应器绑定、配置回调和销毁入口继续负责协调器生命周期；`NativeActions` 保留网页按钮原有协调器，新标签页按钮副本随原生页面销毁释放。

