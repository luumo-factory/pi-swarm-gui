# MQTT protocol contract

This GUI is a read/write client of the [`pi-mqtt-swarm`](https://github.com/luumo-factory/pi-mqtt-swarm)
extension. It speaks the topic map documented in that project, plus two
**additions** the GUI depends on that must be implemented on the extension side.

`NS` = namespace (config `mqtt.namespace`, default `swarm`).
`ID` = slugified agent id.

## Topics consumed (agent → GUI)

| Topic | Retained | Payload | Used for |
|-------|----------|---------|----------|
| `NS/registry/ID` | ✅ + LWT | `{ id, name, status, model, availableModels?, pid, cwd, startedAt, ts }` | Agent list, busy/idle, current + available models |
| `NS/agents/ID/out` | – | `{ type, ts, ... }` event stream | Per-agent monitor feed |
| `NS/board` | – | `{ seq, from:{id,name}, text, urgent, ts }` | Shared board |

`status` ∈ `online | busy | idle | offline`.

`out` event `type`s rendered: `agent_start`, `turn_end` (`{ tools:[] }`),
`agent_end` (`{ text }`), `model_select` / `set_model_result` (`{ model, ok }`),
`session_reset`, `reloading`, `pong`, `ack` (`{ action }`), `error` (`{ error }`).
Unknown types render as a muted note, so new event types are safe to add.

## Topics published (GUI → agent)

| Topic | Payload | Trigger |
|-------|---------|---------|
| `NS/board` | `{ seq, from:{id,name}, text, urgent, ts }` | Board "Post" box |
| `NS/agents/ID/in` | `{ text }` | Monitor "Send" (normal) |
| `NS/agents/ID/interrupt` | `{ text }` | Monitor "Send" with *urgent* |
| `NS/agents/ID/control` | `{ action, ... }` | Buttons / context menu |

Control actions published:

```jsonc
{ "action": "stop" }                                   // NEW — see below
{ "action": "set_model", "provider": "...", "modelId": "..." }
{ "action": "reset" }
{ "action": "ping" }
```

The board `from` identity comes from config `orchestrator.{id,name}` (default
`gui` / `monitor`). The GUI maintains its own `seq` counter.

---

## Required extension additions

The GUI is already coded against these; they need implementing in
`pi-mqtt-swarm` for the corresponding buttons to function.

### 1. `stop` control action — hard-cancel the current turn

The existing `/interrupt` channel only *steers* a running turn (injects text the
model sees). The GUI's **Stop** button instead publishes:

```json
{ "action": "stop" }
```

to `NS/agents/ID/control`, and expects the extension to abort the in-flight turn
(equivalent to the user pressing the interrupt/escape key in the TUI). An
acknowledgement on `NS/agents/ID/out` (e.g. `{ "type":"ack","action":"stop" }`)
is recommended but not required.

> If/when a richer per-agent control channel is added, only the topic/payload in
> `SwarmClient.stopAgent(...)` needs to change.

### 2. `availableModels` in the registry payload

To populate the model picker and the **Toggle model** action, the retained
`NS/registry/ID` message should include the list of models the agent can switch
to:

```jsonc
{
  "id": "coder-1",
  "name": "Coder One",
  "status": "idle",
  "model":  { "provider": "anthropic", "id": "claude-sonnet-4-5", "name": "Sonnet 4.5" },
  "availableModels": [
    { "provider": "anthropic", "id": "claude-sonnet-4-5", "name": "Sonnet 4.5" },
    { "provider": "openai",    "id": "gpt-4o",            "name": "GPT-4o" }
  ],
  "pid": 111, "cwd": "/work", "startedAt": 0, "ts": 0
}
```

"Toggle model" cycles to the next entry in `availableModels` and publishes a
`set_model` control message. Until the extension advertises `availableModels`,
the GUI falls back to `fallbackModels` from its own config; if that is also
empty, toggling shows an explanatory dialog instead of guessing.
