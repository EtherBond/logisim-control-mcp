# Verified tool recipes

Each block names an MCP tool and its JSON arguments; it is not JavaScript to run
inside Logisim. Successful results appear in structuredContent.result and as
equivalent JSON text. Parent directories for file outputs must already exist.

## Inspect and test the bundled half adder

1. `logisim_open_project`:
   `{"path":"/absolute/path/to/examples/half-adder.circ"}`
2. `logisim_inspect`: `{}`. The root has inputs a,b and an instance labelled U1.
3. `logisim_batch`:

```json
{"operations":[
  {"tool":"logisim_set_input","arguments":{"label":"a","value":"1"}},
  {"tool":"logisim_set_input","arguments":{"label":"b","value":"1"}},
  {"tool":"logisim_read_signals","arguments":{}},
  {"tool":"logisim_read_signals","arguments":{"instancePath":["U1"]}},
  {"tool":"logisim_check","arguments":{"instancePath":["U1"]}}
]}
```

Expected sum=0, carry=1 in both root outputs and the U1 instance. The complete
truth table for (a,b)->(sum,carry) is 00->00, 01->10, 10->10, 11->01.
`get_component({"label":"U1"})` names a,b,sum,carry on its ports.

## Create a NOT-gate circuit

Create a project, then add three components:

```json
{"operations":[
  {"tool":"logisim_add_component","arguments":{"type":"Pin","x":100,"y":100,"attributes":{"label":"X"}}},
  {"tool":"logisim_add_component","arguments":{"type":"NOT Gate","x":300,"y":100}},
  {"tool":"logisim_add_component","arguments":{"type":"Pin","x":500,"y":100,"attributes":{"label":"Y","output":"true","facing":"west"}}}
]}
```

Inspect. For the default NOT Gate at (300,100), the input is (270,100) and output
is (300,100). Verify this in your result rather than treating it as a universal
coordinate rule. Wire (100,100)->(270,100) and (300,100)->(500,100).
Set X=0 then X=1; expected Y=1 then Y=0. Save to a new absolute .circ path.

## Draw one path with two bends

After verifying both port endpoints and clear routing space:

```json
{"points":[{"x":100,"y":100},{"x":200,"y":100},{"x":200,"y":200},{"x":300,"y":200}]}
```

Call `logisim_wire_path` with this object. All three segments are one undoable
edit. Any diagonal segment rejects the whole path. This does not autoroute
around other wires or decide which crossings should be junctions.

## Test a D flip-flop using an external CP pin

Open examples/d-flip-flop.circ. Its pins are D, CP, RESET and Q. Reset and then
sample one value using `logisim_batch`:

```json
{"operations":[
  {"tool":"logisim_set_input","arguments":{"label":"CP","value":"0"}},
  {"tool":"logisim_set_input","arguments":{"label":"RESET","value":"1"}},
  {"tool":"logisim_set_input","arguments":{"label":"RESET","value":"0"}},
  {"tool":"logisim_set_input","arguments":{"label":"D","value":"1"}},
  {"tool":"logisim_set_input","arguments":{"label":"CP","value":"1"}},
  {"tool":"logisim_read_signals","arguments":{}},
  {"tool":"logisim_set_input","arguments":{"label":"CP","value":"0"}}
]}
```

Expected Q=1 after the rising edge. Changing D to 0 without another rising edge
must leave Q=1. Setting RESET=1 must clear Q immediately. `tick` does not toggle
this CP pin. Read a DFF with `get_component` to see its D/clock/Q/notQ/clear/preset.

## Build and connect a subcircuit

- `select_circuit({"name":"my_module","create":true})`.
- Add uniquely labelled input and output Pins; set data widths and facing.
- Implement and test the module using its standalone selected circuit.
- `select_circuit({"name":"main"})`.
- `add_component({"type":"my_module","x":600,"y":200,"attributes":{"label":"U1"}})`.
- Use the returned named ports; do not assume pin order or block width.
- When its interface changes, inspect every parent instance again.

## Debug a sequential arithmetic circuit

Keep operands stable; drive clock low, assert the circuit reset/load, issue any
required loading edge, release reset, then advance the required cycle count.
Read child instances with instancePath after each edge. Check busy/ready, count,
partial results, carry and sign. On completion, extra clocks must preserve the
result if the user requires a stop. Test reset again after completion.

For overflow, derive the expected result from the assignment's explicit policy.
Do not assume wrapping two's-complement low bits if examples specify truncating
magnitude and then restoring sign. Validate against independently calculated
expected values, not just against your own implementation's next-state formulas.

## Batch failures

Schemas for every operation are checked before execution. A runtime failure,
such as a missing component, leaves successful earlier operations applied.
Inspect the returned completed count, failedIndex and results; repair or undo
those edits intentionally. Do not blindly resend the entire batch. Open/new/save
and screenshot operations must be separate calls.
