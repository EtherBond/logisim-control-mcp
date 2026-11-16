# Tool reference (1.1.0)

All calls use a JSON object. Unknown arguments are rejected. Results are exposed
as structuredContent.result and as the same JSON in content[0].text. On a tool
failure isError=true and result.error explains the failure; JSON-RPC protocol
errors use error.code/message instead. Check errors rather than treating any
returned text as success.

Coordinates are integer multiples of 10 in 0..10000. File paths are absolute;
parent directories must exist. Attribute values are strings. Tool schemas are
available from tools/list and are the authoritative parameter definitions.

| Tool | Arguments | Result/notes |
| --- | --- | --- |
| logisim_help | topic?: guide/recipes/troubleshooting/tools | Bundled Markdown, no Java needed |
| logisim_status | none | Selected project/circuit, dirty/gui/oscillating |
| logisim_new_project | none | Creates project; rejects dirty replacement |
| logisim_open_project | path | .circ only; recovery conflict rejected |
| logisim_save_project | path, overwrite?: boolean | .circ; backup before explicit overwrite |
| logisim_catalog | type?: string | Factory names or default attribute strings |
| logisim_inspect | instancePath?: string[] | Components, named live ports, wires |
| logisim_get_component | label OR x,y; instancePath? | One component description |
| logisim_check | instancePath?, includeUnknown?: boolean | Error/unknown port values and duplicate labels |
| logisim_select_circuit | name, create?: boolean | Select a standalone circuit definition |
| logisim_add_component | type,x,y,attributes?: object | Exact ports returned; overlaps rejected |
| logisim_set_attribute | x,y,attribute,value | One undoable edit; re-inspect ports |
| logisim_remove_component | x,y | Removes component, leaves wires |
| logisim_wire | x1,y1,x2,y2,remove?: boolean | One orthogonal segment |
| logisim_wire_path | points: [{x,y},...],remove? | 2..200 points; validate all, one undo action |
| logisim_set_input | label OR x,y; value: string | Unsigned decimal, 0x hex, or 0b binary |
| logisim_tick | count?: integer | 1..10000 half-cycle advances |
| logisim_read_signals | instancePath? | Input/output pin values |
| logisim_reset | none | New selected simulation state; not reset/load pulse |
| logisim_undo | none | Last supported structure edit, not time/input values |
| logisim_snapshot | path | GUI visible canvas PNG, returned as an image too |
| logisim_batch | operations: [{tool,arguments?},...] | 1..100 calls; prevalidate; partial completion on runtime error |

## Component descriptions

`id` is an anchor selector such as @400,140. `label`, `type`, `location` and
`attributes` describe the component. Each `ports` entry includes its numeric
index, human-readable name, location, width, input/output direction flags and
live value with binary/hex/defined/error. Names for subcircuit ports come from
actual interface pins. Common DFF port names are D,clock,Q,notQ,clear,preset.
Other factory tooltips may be localized.

`instancePath` follows labels or @x,y selectors from the selected root. Empty
path means the root itself. Only read tools accept it; writes still target the
selected standalone circuit. `get_component` and `set_input` accept either a
unique label or the complete x,y pair, never both. Coordinate ambiguity is an
error; setting an output Pin is an error. A label beginning with @ is safely
used through the explicit label argument, not as a path coordinate selector.

## Checking and limits

`check.ok=true` means no observed electrical error values/oscillation in that
requested circuit instance. It does not recursively prove child circuits,
detect all floating controls, prove wiring isolation, or validate the truth
table. Query deeper instance paths and run expected vectors. Width errors and
multiple drivers often appear as E values, but unknown alone can be intentional.

Calls execute sequentially on the Swing event thread with automatic ticking
disabled. Each Java command has a 60-second timeout. Batch is not atomic;
successful earlier operations remain when a later runtime operation fails.
Invalid schemas anywhere in a batch reject it before the first operation.
Use chunks of about 10..30 operations to keep results manageable.

Save/open/new/snapshot/help/batch cannot appear inside a batch. Snapshot writes
a file and therefore is not marked read-only. Read tools can propagate pending
simulation state, but do not intentionally edit circuit structure.
