# 构建与检查

Windows PowerShell，JDK 21，Android SDK Platform 36 与 Build Tools。配置 JAVA_HOME 与 ANDROID_SDK_ROOT，或传参数：

```powershell
cd android
./build.ps1 -SdkRoot 'C:/Android/Sdk' -JavaHome 'C:/Program Files/Java/jdk-21'
```

脚本兼容 Android 官方 SDK 的 platforms 布局和此前独立工具包的 platform 布局；输出 build/Comfy-Mobile-版本号.apk，版本读取 AndroidManifest.xml。首次构建会在本机生成私有签名配置。仓库不含原签名密钥，新生成密钥的 APK 不能覆盖安装原签名版本。

```powershell
cd android
npm install
npm test
npm run test:layout
./test-native.ps1
python -m unittest test_computer_batches.py test_desktop_workflows.py
```

Node 依赖 linkedom；布局检查使用独立无界面 Edge，或通过 COMFY_TEST_CHROMIUM 指定 Chromium。原生 JVM 测试的 org.json JAR 仅用于检查，不进入 APK。Python 单元测试不启动真实推理；HTTP 集成测试是模拟推理服务。

`prepare-workflows.cjs` 依据本机 `/object_info` 重新生成固定映射。默认从仓库 workflows 读取；可配置 COMFYUI_WORKFLOWS 和 COMFYUI_URL。改节点结构后必须验证映射，单纯保存模型参数不用重新打 APK。

已进行 API36 编译、签名、界面事件／Chromium 布局与原生状态机检查；未以手机点击操作代替真实设备适配验证。公开前可用自己的手机检查 ROM 相册、手势、IPv6 网络条件。
