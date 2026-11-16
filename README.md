# Logisim Control MCP

让 AI agent 通过 MCP **检查、搭建、修改并验证 Logisim-evolution 电路**。
使用本地 Java API 操作一个独立窗口，不依赖鼠标坐标识别，也不会接管你手动打开的窗口。

**面向 agent 的改进：**22 个工具、可读的端口名称与实时值、按引脚标签输入、嵌套实例检查、批量操作、内置使用教程，以及可运行的通用示例。任何支持 stdio MCP 的客户端都可接入；不要求 agent 预先知道这次聊天的内容。

> An agent-friendly local stdio MCP server for Logisim-evolution 3.9.0.
> Start with `logisim_help({"topic":"guide"})`. English operating instructions,
> verified recipes and troubleshooting are bundled as MCP resources and Markdown.

## 安装

需要 **Node.js 18+、JDK 21+**，以及单独下载的
[Logisim-evolution 3.9.0 JAR](https://github.com/logisim-evolution/logisim-evolution/releases/tag/v3.9.0)。
3.9.0 本身要求 Java 21 或更新版本，见[官方要求](https://github.com/logisim-evolution/logisim-evolution/blob/v3.9.0/README.md#requirements)。本项目不捆绑该 JAR，没有 npm 第三方依赖，也不执行安装时下载。

```powershell
git clone https://github.com/EtherBond/logisim-control-mcp.git
cd logisim-control-mcp
$env:LOGISIM_JAR = 'C:/tools/logisim/logisim-evolution-3.9.0-all.jar'
npm run build
npm test
npm run test:integration
```

macOS / Linux：

```sh
export LOGISIM_JAR=/opt/logisim/logisim-evolution-3.9.0-all.jar
npm run build
npm test
npm run test:integration
```

如果 Java 不在 PATH，可分别设置 `LOGISIM_JAVA` 和 `LOGISIM_JAVAC`。其他 Logisim 版本没有保证兼容；先在对应版本重新编译并跑集成测试。

## 接入 MCP 客户端

把示例路径改成自己的绝对路径：

```json
{
  "mcpServers": {
    "logisim-control": {
      "command": "node",
      "args": ["C:/tools/logisim-control-mcp/server/index.mjs"],
      "env": {
        "LOGISIM_JAR": "C:/tools/logisim/logisim-evolution-3.9.0-all.jar"
      }
    }
  }
}
```

需要无界面运行时加上 `"LOGISIM_HEADLESS": "true"`，此时截图工具不可用。客户端退出时，服务关闭自己启动的 Java 进程和窗口。图形模式需要桌面会话；不要同时在受控窗口里启动自动时钟。

Codex CLI 可以用本地脚本注册（不会覆盖已有的同名配置）：

```powershell
.\install.ps1 -JarPath 'C:/tools/logisim/logisim-evolution-3.9.0-all.jar'
```

其他客户端直接使用上面的 stdio 配置。仓库也保留 `plugin.json`、`mcp.json` 和 Codex 兼容清单；`${PLUGIN_ROOT}` 由支持该清单的宿主替换。若宿主不支持这些变量，请用绝对路径配置，并显式传入 `LOGISIM_JAR`。

## 让其他 agent 正确使用

把这段话交给一个刚接入的 agent：

> 使用 logisim-control MCP。先调用 logisim_help，topic=guide；按教程检查工具和端口，再进行操作。按用户要求限制元件，不要自行使用隧道或现成运算器件。先保存工作副本，再修改。用预期输入输出验证功能，并检查复位和结束后的行为。

教程也可以通过标准 MCP `resources/list`、`resources/read` 读取：

| 内容 | MCP URI | 仓库文档 |
| --- | --- | --- |
| agent 操作流程 | `logisim://docs/guide` | [AGENT_GUIDE](docs/AGENT_GUIDE.md) |
| 可执行调用示例 | `logisim://docs/recipes` | [RECIPES](docs/RECIPES.md) |
| 接线、仿真故障排查 | `logisim://docs/troubleshooting` | [TROUBLESHOOTING](docs/TROUBLESHOOTING.md) |
| 全部参数和行为 | `logisim://docs/tools` | [TOOLS](docs/TOOLS.md) |

**几个容易出错的区别：**

- 元件锚点不等于引脚坐标；先看 `ports[].name/location/width` 再接线。
- `Pin.input=true` 表示外部输入引脚；它在电路内部的端口是信号驱动端。
- `tick(1)` 是半周期；外部 `clk/CP` 输入引脚需要用 `set_input` 明确切换。
- `select_circuit` 进入独立电路仿真；`instancePath` 才能读父电路里正在运行的实例。
- `logisim_reset` 重置模拟器状态，不等于按下电路的 reset，也不会替你装入操作数。
- `.circ` 保存电路结构，不保存本轮输入、计数器状态或测试波形。

## 工具

| 类别 | 工具，均以 `logisim_` 开头 |
| --- | --- |
| 教程 | `help` |
| 工程 | `status`、`new_project`、`open_project`、`save_project` |
| 检查 | `catalog`、`inspect`、`get_component`、`check` |
| 编辑 | `select_circuit`、`add_component`、`set_attribute`、`remove_component`、`wire`、`wire_path`、`undo` |
| 仿真 | `set_input`、`tick`、`read_signals`、`reset` |
| 输出与批量 | `snapshot`、`batch` |

每个工具都返回 `structuredContent.result`，并保留同一结果的 JSON 文本，兼容只读取文本的客户端。`batch` 先验证全部参数，再顺序执行；遇到运行错误会报告完成数量和失败位置，**不会回滚已经完成的操作**。

## 示例和测试

`examples/half-adder.circ` 是新建的门电路半加器，包含一个可用于实例检查的子电路。
`examples/d-flip-flop.circ` 是外部时钟输入控制的D触发器。它们都不含个人作业、手册内容或本机路径。

```sh
npm test                    # 协议、参数、教程资源测试，不需要 JAR
npm run test:integration    # 实际 Java/Logisim 仿真，需要已构建的桥接和 JAR
python scripts/privacy_check.py
python scripts/package_release.py --with-build
```

集成测试使用临时目录，覆盖真值表、按标签与进制输入、时钟与复位、实例状态读取、端口错误诊断、批量部分失败、连线原子验证、撤销和保存重开。运行时不会修改你的已有电路。

## 开源与数据范围

仓库只发布此 MCP 的源码、文档、通用示例和测试。发布脚本采用文件白名单，不打包本地作业、手册、截图、视频、日志、缓存、凭据、Git 历史或第三方 JAR；源码不带开发者的本机默认安装路径。详细说明见 [SECURITY.md](SECURITY.md)。

MCP 返回的电路信息会被你的 MCP 客户端接收；如果客户端连接云端 AI，相关信息仍可能发送给该服务。本地运行不等于对客户端隐瞒工具结果。

适配器采用 [MIT](LICENSE)；Logisim-evolution 保留其自身许可，见 [第三方说明](THIRD_PARTY_NOTICES.md)。
