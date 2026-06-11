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
- Switch between **light and dark** themes at runtime.

It speaks the two-plane protocol: a work/data plane (`in` / `interrupt` / `out`)
and a dedicated control plane (`control/in` / `control/out`). See
[`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the exact MQTT contract.

## Requirements

- Java 25 (or newer)
- Maven 3.9+
- An MQTT broker the swarm uses (e.g. Mosquitto on `127.0.0.1:1883`)

## Configuration

On first run a config file is created at
`~/.config/pi-swarm-gui/config.json` (override with the first CLI argument or
`PISWARM_GUI_CONFIG`). See [`config.example.json`](config.example.json):

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

## Build & run

```bash
mvn clean package
mvn exec:java                 # uses default config path
mvn exec:java -Dexec.args="/path/to/config.json"
```

## Test

```bash
mvn test
```

`TopicsTest` is a pure unit test. `SwarmClientRoundTripTest` exercises the MQTT
plumbing against a broker on `127.0.0.1:1883` and **skips itself** if none is
reachable.

## Usage

- **Agent list (left):** double-click an agent to open its monitor; right-click
  for monitor / controls / stop / toggle model / set model / reset.
- **Message board:** type in *Broadcast* and press Enter/Post; tick *urgent* to
  interrupt all agents.
- **Agent monitor:** type in *Message* to queue work (or tick *urgent* to inject
  a steering message); use *Stop* (abort) / *Toggle model* / *Controls…* in the
  toolbar.
- **Controls / details dialog:** pick a model, tick/untick extensions and tools
  to enable/disable them, or *Request status*.
- **Window menu:** Tile / Cascade the open monitors; show/hide the board.
- **View ▸ Theme:** switch light/dark.
