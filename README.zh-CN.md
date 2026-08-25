# Inlay Hints Extension（内联提示扩展）

Inlay Hints Extension 是一款 IntelliJ IDEA 插件，用于把本地逐行笔记显示为源码行末可点击的 inline inlay。笔记不写入源码，插件也不包含任何 AI 功能。

## 文件映射

规范布局将 `inlay-hints` 放在项目根目录。IHM 完整保留源文件相对于项目根目录的路径，并在完整源文件名后追加 `.ihm`。

```text
project/
├── main.go
├── src/
│   ├── main/java/com/example/Demo.java
│   └── main/kotlin/com/example/Worker.kt
└── inlay-hints/
    ├── main.go.ihm
    └── src/
        ├── main/java/com/example/Demo.java.ihm
        └── main/kotlin/com/example/Worker.kt.ihm
```

对于已有 IHM 文件，插件采用与 Node.js 查找模块相似的逐级向上搜索。以 `project/module/src/Demo.java` 为例，插件依次检查以下路径，并使用第一个实际存在的文件：

```text
project/module/src/Demo.java.ihm
project/module/src/inlay-hints/Demo.java.ihm
project/module/inlay-hints/src/Demo.java.ihm
project/inlay-hints/module/src/Demo.java.ihm
```

每个 `inlay-hints` 目录都会保留源码相对于该目录父级的路径。因此，同级的 `Demo.java.ihm` 优先级最高，适合只测试单个源码文件；批量生成 IHM 时仍推荐使用项目根目录下的规范布局。

`Demo.java.ihm` 第 12 行会显示在 `Demo.java` 第 12 行行末；空白 IHM 行不显示。按住 `Ctrl` 单击提示会打开对应 IHM 并定位到同一行，普通单击不跳转。

如需直接在源码编辑器中修改笔记，请在目标源码行末输入激活关键字。默认关键字为 ` ^^ `，其中同时包含开头和结尾空格。插件会立即从源码中移除该关键字，并在行末打开黑色字体的内联文本编辑框；按 `Enter` 保存后，源码光标会回到当前行末；按 `Esc` 取消。焦点离开编辑框时会立即保存当前内容，但不会把焦点抢回源码。已有笔记会自动填入编辑框，单击或双击提示均不会进入编辑模式。

内联编辑框使用复合字体，即使从空白提示开始输入，中文及其他需要字体回退的字符也能正常显示。

输入激活关键字后，插件会优先编辑搜索到的最高优先级 IHM 文件。如果完全没有映射文件，插件会向项目根逐级搜索，并在最近一个已经存在的 `inlay-hints` 目录中按相对源码路径创建文件；如果各层都没有 `inlay-hints` 目录，则在项目根目录创建规范目录和映射。自动创建永远不会把 IHM 裸文件直接放在源码同级。新文件会用空白行补齐到源码行数，从而保持逐行对齐。插件不会增加编辑器快捷按钮，也不包含任何 AI 功能。

## 使用 AI 生成解释

插件本身不包含 AI 功能。项目根目录提供了可以交给 AI 编程助手的通用多语言中文范文：[AI_PROMPT_TEMPLATE.md](AI_PROMPT_TEMPLATE.md)。范文会要求 AI 根据每种语言的语义识别命名空间、依赖导入和代码块结束语句，不依赖 Java 固定关键词。

使用步骤：

1. 先将 `/inlay-hints/` 加入仓库本地的 `.git/info/exclude`。
2. 在项目根目录启动能够读写工作区的 AI 编程助手。
3. 打开提示词范文；默认覆盖整个项目，如果只需要某个模块，先修改范文中的扫描范围。
4. 将代码块内的完整提示词发送给 AI，并允许它在项目根目录的 `inlay-hints` 中创建 `.ihm` 文件。提示词已经完整定义 IHM 的纯文本格式、路径映射和逐行对齐协议，AI 无需预先了解或安装本插件。
5. AI 执行完成后，确认每个 IHM 路径映射正确，且与对应源文件行数一致。
6. 回到 IDEA 打开对应源码。插件检测到 IHM 后会显示行末提示，按住 `Ctrl` 单击可跳转到对应 IHM 行继续编辑。

生成前建议备份已有的人工笔记。AI 必须严格保持“一行源码对应一行 IHM”；任何额外标题或换行都会导致后续提示错位。

## 单向行同步

无论是人工编辑，还是 `git pull` 等外部操作导致源码行增加、修改或删除，已有 IHM 都会按行级差异执行对应同步。同步方向严格为“源码 → IHM”，编辑 IHM 不会修改源码。

源码新增行会在 IHM 中插入空行，删除行会删除对应笔记。修改行默认保留原笔记。

在源码编辑器保持打开期间，删除整行后使用 `Ctrl+Z` 会恢复该行原有的 IHM 笔记，重做操作也会恢复删除后的状态。插件为每个打开的源文件保留最近 50 次同步历史；如果期间手工修改了 IHM，历史恢复不会覆盖手工内容。

## 设置

打开 **Settings | Tools | Inlay Hints Extension** 可以：

- 显示或隐藏 Inlay Hints Extension；
- 选择在源码行被修改时，是否清空对应 IHM 行；
- 选择在内联编辑删除最后一个非空提示时，是否自动删除对应 IHM 文件（默认关闭）；
- 开启自动删除 IHM 文件后，可选择是否递归删除空的父目录（默认关闭）；
- 自定义在源码行末唤起内联提示编辑框的激活关键字。

## Git 本地排除

建议在仓库本地的 `.git/info/exclude` 中加入：

```gitignore
/inlay-hints/
```

插件首次发现镜像 IHM 时会在当前项目中提示一次。

## 兼容性

- IntelliJ IDEA 2023.1 及以上版本
- Java 17 字节码

## 构建

本项目使用 JDK 21 和 Gradle 8.14.3 构建并完成验证：

```shell
gradle test buildPlugin
```

若要使用已安装的 IDE 构建，可传入其安装目录：

```shell
gradle test buildPlugin -PlocalIdePath=/path/to/IntelliJ-IDEA
```

生成的 ZIP 位于 `build/distributions/`。
