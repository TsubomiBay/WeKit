# 配置指南

## 入口

模块大部分功能均可直接通过模块设置 UI 进行配置。可通过以下方式中任意一种打开模块设置。

### 从微信设置进入

微信首页 -> 「我」-> 「设置」-> 「WeKit 设置」

### 从微信主页加号进入

微信首页 -> 右上角 `+` 号 -> 菜单中点击「WeKit 设置」

### 从主页浮动按钮进入

如果已启用「界面美化 / 主屏幕添加 FAB」且添加了「模块设置」菜单项：微信首页 -> 右下角浮动按钮 -> 「模块设置」

### 从模块应用进入

如果微信包名为默认的 `com.tencent.mm`：模块应用 -> 「打开模块设置」

## 功能设置

功能设置按功能分类排列，每个类别下是具体的功能。可配置的功能均排序于每个分类页面最底部，在名称旁有齿轮图标表示可点击配置。

各设置项及分组的详细说明请参见 [功能设置说明](module-settings.md#功能设置)。

## 通用设置

通用设置位于全部功能分类下方，可配置模块本体部分行为。

详见 [通用设置说明](module-settings.md#通用设置)。

## 模块数据目录

模块运行时的文件存放在以下位置：

```none
/sdcard/Android/data/<宿主包名>/WeKit/
├── logs/                     # 运行日志
├── scripts/                  # JavaScript 脚本（需手动创建）
├── stickers/                 # 贴纸包（需手动创建）
├── crashes/                  # 崩溃日志（需启用「调试/崩溃拦截」）
├── dex_cache/                # DEX 缓存（自动生成）
└── generated_proxy_classes/  # 新版微信设置注入辅助文件（自动生成）
```

### KV 存储位置

模块的配置数据（MMKV 文件）存放在：

- **单用户**: `/data/data/<宿主包名>/files/mmkv/{wekit_prefs,wekit_prefs.crc}`
- **多用户 (如双开微信)**: `/data/user/<宿主安卓用户 ID>/<宿主包名>/files/mmkv/{wekit_prefs,wekit_prefs.crc}`

> 如果模块功能失效或设置异常，可以尝试清除上述 MMKV 文件（会丢失所有设置）。

## 脚本目录

脚本文件存放于 `<模块数据>/scripts/` 目录下，支持 JavaScript 脚本。

详见 [脚本引擎](features/scripting_js/js-scripting-hook.md)。

## 贴纸目录

贴纸存放于 `<模块数据>/stickers/` 目录下，每个贴纸包为一个子目录。

详见 [贴纸包同步](features/chat/stickers-sync.md)。
