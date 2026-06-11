# Pi Swarm GUI

A Swing desktop monitor for a [`pi-mqtt-swarm`](https://github.com/luumo-factory/pi-mqtt-swarm)
fleet. It connects to the swarm's MQTT broker to:

- **List agents** with their model and live `busy` / `idle` / `offline` status.
- Show the **shared message board** and broadcast posts to every agent.
- **Open a tileable monitor window per agent** (MDI internal frames) showing a
  pi-style activity feed (work events **and** control-plane replies), with the
  ability to send messages, stop (abort) the current turn, toggle the model, and
  reset context.
- **Control / details dialog per agent** to switch model and enable/disable
  extensions and tools over the dedicated control plane, and request a status
  snapshot.
- **Launch new agents** on a running swarm **console** (spawn host) via named
  **profiles** (agent name, model, extra extensions); pick the host when more
  than one console is running.
- **Rename** agents over the control plane (right-click an agent or use the
  monitor toolbar).
- **Purge stale (offline) agents** by clearing their retained MQTT registry
  topic with an empty `retain=true` payload; only offered for offline agents.
- Mirror **user input typed directly into an agent's TUI** in its monitor feed.
- Switch between **light and dark** themes at runtime.
- **Debug window** (toggle in *View ▸ Debug (MQTT messages)*) that shows the raw
  MQTT traffic: a topic tree on the left, the messages for the selected topic in
  the middle, and the decoded body (pretty-printed when it is JSON) of the
  selected message on the right.

It speaks the two-plane protocol: a work/data plane (`in` / `interrupt` / `out`)
and a dedicated control plane (`control/in` / `control/out`). See
[`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the exact MQTT contract.

## Requirements

- Java 25 (or newer)
- Maven 3.9+
- An MQTT broker the swarm uses (e.g. Mosquitto on `127.0.0.1:1883`)

## Configuration

On first run a config file is created at
`~/.config/pi-swarm-gui/app-config.json` (override with the first CLI argument or
`PISWARM_GUI_CONFIG`; a directory may be given, in which case `app-config.json`
is used inside it). A pre-rename `config.json` sitting beside it is migrated
forward automatically, so existing broker settings are preserved. See
[`config.example.json`](config.example.json):

```json
{
  "mqtt": { "host": "127.0.0.1", "port": 1883, "namespace": "swarm" },
  "ui": { "theme": "dark" },
  "orchestrator": { "id": "gui", "name": "monitor" },
  "fallbackModels": []
}
```

- `mqtt` — broker connection and the topic `namespace` (matches the extension's
  `PI_SWARM_NS`). Optional `username` / `password` / `tls`.
- `ui.theme` — `dark` or `light`.
- `orchestrator` — identity used when this GUI posts to the board.
- `fallbackModels` — models offered by "Toggle model" until agents advertise
  their own `availableModels`.

### Launch profiles

Launch profiles are stored next to the app config in **`profiles-config.json`**.
A profile bundles an optional **agent name** (used as the spawned agent's
`--name`), **model** (e.g. `anthropic/claude-sonnet-4-5` or a fuzzy query like
`sonnet`), a **working directory** (the directory the agent is spawned in —
defaults to your home directory when left blank) and extra **extensions**. Edit
them in **Swarm ▸ Manage profiles…** (or *Manage profiles…* in the launch
dropdown). All fields are optional — launching with `<defaults>` spawns a plain
agent in your home directory.

Agents are spawned through a swarm **console** (`pi-mqtt-swarm`'s `console.ts`,
one per host). The GUI discovers consoles from their retained registry and, when
more than one is running, asks which host to launch on.

### Persisted state

On exit the GUI writes two JSON files next to each other in the config
directory:

- **`app-config.json`** — the running configuration. It is always saved on
  close, so even a session started from built-in defaults is persisted (and
  runtime changes such as the chosen theme are remembered).
- **`session-config.json`** — the UI session: the main window's size and
  location plus the geometry of every child window (board, MQTT debug,
  per-agent monitors and control dialogs), keyed by a stable id so each window
  reopens where you last left it, plus the list of which windows were open. On
  launch the debug window reopens immediately; per-agent monitor/controls
  windows reopen automatically as soon as their agent is rediscovered over MQTT.
  The retained-message buffer is unaffected.

## Build & run

```bash
mvn clean package
mvn compile exec:java         # compile + run (uses default config path)
mvn compile exec:java -Dexec.args="/path/to/app-config.json"
```

> Use `mvn compile exec:java` (or `mvn package` first): bare `mvn exec:java`
> only runs whatever is already in `target/classes` and will **not** pick up
> source changes. On connect the console logs `[swarm] connected to tcp://…`;
> repeated `[swarm] connect … failed` lines mean the broker host/port in your
> config is wrong or the broker is down.

## Test

```bash
mvn test
```

`TopicsTest` is a pure unit test. `SwarmClientRoundTripTest` exercises the MQTT
plumbing against a broker on `127.0.0.1:1883` and **skips itself** if none is
reachable.

## Usage

- **Left panel:** split into three sections — **Named Agents**, **Auto Agents**
  and **Message Boards**.
- **Named Agents:** the manually-launched agents. The **＋ Launch agent ▾**
  button sits at the **top** of this section and spawns a new agent from a chosen
  profile (or `<defaults>`), with *Manage profiles…* below a separator.
  Double-click an agent to open its monitor; right-click for monitor / controls /
  stop / **rename** / toggle model / set model / reset / **shut down (/quit)** /
  **purge stale agent**. Only agents we've received a real status update for are
  shown (stale/retained topics are ignored, and a deregistered agent is dropped);
  offline agents are listed last. **Purge stale agent** is only enabled for
  offline agents and clears the agent's retained MQTT registry topic (an empty
  `retain=true` payload) so a dead agent is removed for every connected client.
- **Agent groups:** every agent belongs to one of eight colour-coded groups
  (default **red**), shown as a chain icon on each agent row and in the monitor
  window header. Click the header chain icon to pick a group; the agent's
  extension re-binds to that group's board topic and the change is reflected
  everywhere (row icon, header, registry).
- **Auto Agents:** reserved for auto-spawned agents (empty for now).
- **Message Boards:** the eight colour-coded group boards (Red, Orange, Yellow,
  Green, Cyan, Blue, Purple, Pink), each with a chain icon in its group colour.
  Double-click a board to open (or focus) it as its own window. Each group has
  its own board topic — the default **red** group keeps the legacy `NS/board`
  topic, the others use `NS/board/<group>` — and every board is cached in the
  background so any can be displayed instantly.
- **Message board window:** type in *Broadcast* and press Enter/Post to post to
  that group's board; tick *urgent* to interrupt the group's agents.
- **Agent monitor:** the header shows the group chain icon, a colour-coded status
  dot next to busy/idle, the model, and the working directory path. Type in
  *Message* to queue work (or tick *urgent* to inject a steering message); use
  *Stop* (abort) / *Model* / *Rename…* / *Controls…* in the toolbar.
- **Message input:** focusing an agent or board window puts the cursor straight
  in its input box. **Enter** sends; **Shift+Enter** starts a new line. The box
  grows up to three rows whenever the text spans multiple lines — from explicit
  newlines or soft word-wrap — and shrinks back to one row as the text shortens.
- **Window navigation:** **Ctrl+Arrow** moves selection to the open window
  immediately in that direction (left/right/up/down by on-screen position).
- **Controls / details dialog:** pick a model, tick/untick extensions and tools
  to enable/disable them, or *Request status*.
- **Snapping & reflow:** dragging/resizing a monitor snaps its edges to the
  desktop edges and to other frames (no gap once snapped). When the main window
  is resized, frames anchored to a desktop edge follow it — a frame stretched
  between both edges of an axis (e.g. a tiled 20:60:20 row) grows/shrinks
  proportionally on that axis, one docked only to the far edge translates, and
  unsnapped frames stay put.
- **Window menu:** Tile / Cascade the open monitors; show/hide the board.
- **View ▸ Theme:** switch light/dark.
- **Swarm menu:** *Launch new agent* (defaults + profiles) and *Manage
  profiles…*.
- **Agent monitor toolbar:** *Rename…* renames the agent.
- **View ▸ Debug (MQTT messages):** open/close the raw-traffic debug window
  (includes the `console/in` and `console/out` topics).
  Select a topic (or a parent node to aggregate everything beneath it) to list
  its messages, then click a message to see its full payload. *Follow* keeps the
  newest message selected; *Clear view* empties the current list.

The per-topic raw-message buffer is bounded by `ui.debugBufferSize` (default
500).
