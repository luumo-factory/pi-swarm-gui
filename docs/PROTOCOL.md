# MQTT protocol contract

This GUI is a read/write client of the [`pi-mqtt-swarm`](https://github.com/luumo-factory/pi-mqtt-swarm)
extension. There are two planes:

- **work / data plane** — `in` / `interrupt` / `out`
- **control plane** — a dedicated `control/in` / `control/out` pair

`NS` = namespace (config `mqtt.namespace`, default `swarm`).
`ID` = slugified agent id.

## Topics consumed (agent → GUI)

| Topic | Retained | Payload | Used for |
|-------|----------|---------|----------|
| `NS/registry/ID` | ✅ + LWT | `{ id, name, status, group, model, availableModels, extensions, tools, pid, cwd, startedAt, ts }` | Agent list, busy/idle, group, current + available models, extension/tool state |
| `NS/agents/ID/out` | – | work events `{ type, ts, ... }` | Per-agent monitor feed |
| `NS/agents/ID/control/out` | – | control replies `{ id, type, ts, ... }` | Merged into the monitor feed; drives the controls dialog |
| `NS/board` | ✅ | `{ seq, from:{id,name}, text, urgent, ts }` | Default (`red`) group board |
| `NS/board/<group>` | ✅ | `{ seq, from:{id,name}, text, urgent, ts }` | Per-group board (orange…pink) |

> **Colour-coded group boards:** agents are organised into eight colour-coded
> groups (`red`, `orange`, `yellow`, `green`, `cyan`, `blue`, `purple`, `pink`)
> that share a per-group board. The default `red` group keeps the legacy
> `NS/board` topic; every other group uses `NS/board/<group>`. The GUI subscribes
> to `NS/board` **and** `NS/board/+` so all eight boards are cached in the
> background. The group is derived from the topic, not the payload.

`status` ∈ `online | busy | idle | offline`. `group` ∈ `red | orange | yellow |
green | cyan | blue | purple | pink` (default `red`); it determines which board
topic the agent binds to. A `set_group` control reply is `{ type: "group", ok,
group, changed }`.

`model` is `{ provider, id, name }`. `availableModels` is `[{ provider, id, name }]`
(models the agent can actually switch to). `extensions` is
`[{ id, source, scope, origin, tools[], commands[], active }]`. `tools` is
`{ active: string[], available: string[] }`.

**Work `out` event types** rendered: `agent_start`, `turn_end` (`{ tools:[] }`),
`agent_end` (`{ text }`), `session_reset`, `reloading`.

**Control `out` reply types** rendered: `pong`, `status`, `set_model_result`
(`{ ok, model, error }`), `model_select` (`{ model, source }`), `abort`
(`{ ok, wasBusy }`), `extensions` / `extension_toggle` (`{ ok, enabled, matched }`),
`tools` (`{ action, tools }`), `ack` (`{ action }`), `error` (`{ error }`).
Unknown types render as a muted note, so new ones are safe to add.

> **Topic disambiguation:** `NS/agents/+/out` (work) and `NS/agents/+/control/out`
> are different MQTT levels and never collide; `Topics.agentIdFromOut` also
> rejects multi-level ids so `control/out` is never misread as a work topic.

## Topics published (GUI → agent)

| Topic | Payload | Trigger |
|-------|---------|---------|
| `NS/board` / `NS/board/<group>` | `{ seq, from:{id,name}, text, urgent, ts }` | Board "Post" box (topic = selected group's board) |
| `NS/agents/ID/in` | `{ text }` | Monitor "Send" (normal) |
| `NS/agents/ID/interrupt` | `{ text }` | Monitor "Send" with *urgent* (injects/steers) |
| `NS/agents/ID/control/in` | `{ action, ... }` | Buttons / context menu / controls dialog |

Control actions published by the GUI:

```jsonc
{ "action": "abort" }                                           // Stop button — cancels the running turn
{ "action": "set_model", "provider": "...", "modelId": "..." }  // Toggle model / Set model / dialog
{ "action": "reset" }                                           // Reset context
{ "action": "set_group", "group": "blue" }                      // Move agent to a colour-coded group (re-binds its board topic)
{ "action": "ping" }                                            // (programmatic)
{ "action": "status" }                                          // controls dialog "Request status"
{ "action": "enable_extension",  "extension": "<id|path|name>" }
{ "action": "disable_extension", "extension": "<id|path|name>" }
{ "action": "enable_tools",  "tools": ["..."] }
{ "action": "disable_tools", "tools": ["..."] }
```

### Two kinds of "interrupt"

- `NS/agents/ID/interrupt` (work plane) — *injects* an urgent message into the
  running turn (steering). Used by the monitor's **Send + urgent**.
- `{ "action": "abort" }` on `control/in` — *cancels* the running turn entirely
  (`ctx.abort()`). Used by the **Stop** button. (`interrupt` is an alias the
  extension accepts for `abort`.)

The board `from` identity comes from config `orchestrator.{id,name}` (default
`gui` / `monitor`); the GUI maintains its own `seq` counter.

## Mapping to the UI

| UI surface | Reads | Writes |
|------------|-------|--------|
| Agent list (status dot, model) | `registry` | — |
| Right-click menu | `registry` (available models) | `abort`, `set_model`, `reset` |
| Message board | `board` | `board` |
| Agent monitor feed | `out` + `control/out` | `in`, `interrupt`, `abort`, `set_model` |
| Controls / details dialog | `registry` (model/extensions/tools) | `set_model`, `enable/disable_extension`, `enable/disable_tools`, `status` |

The model-switch, extension-toggle and tool-toggle results come back on
`control/out` and the agent re-publishes its `registry`, so the agent list and
the controls dialog refresh automatically.
