# Troubleshooting

| Symptom | What to inspect and fix |
| --- | --- |
| Missing JAR / bridge not built | Configure absolute LOGISIM_JAR, install JDK 21+, run npm run build. GUI app launchers and terminal shells can have different environments. |
| Wrong Java version | Logisim-evolution 3.9.0 needs Java 21+. Set LOGISIM_JAVA and LOGISIM_JAVAC explicitly if PATH selects another version. |
| Autosave/recovery error | Resolve it in Logisim, or copy the saved project and relative dependencies to a clean folder. Do not delete user recovery data. |
| Project cannot be replaced | Save dirty structure to a working copy. open/new never discard edits. |
| Set-input selector is ambiguous | Prefer a unique label. A Pin and Splitter can share an anchor, so x,y may match two components. |
| Decimal value does not fit | Input values are unsigned bit patterns. Encode negative operands in the configured two's-complement width. |
| Count never advances | Distinguish Clock components from input Pins. Check actual clock edges and check reset is not accidentally on the clock net. |
| Child values disagree with parent | Use instancePath. Selecting a child definition reads a standalone simulation, not that parent's child state. |
| Result never leaves busy value | Check run, reset, cycle count and the actual named output port connected to the clock. |
| Negative results wrong | Check sign bits, absolute-value conversion and restoration. A clock shorted to sign inputs can corrupt both. |
| Red/E values | Read error ports with check; inspect multiple drivers, widths and unintended junctions. |
| Unknown values | Check reset/preset/enable inputs, uninitialized data and disconnected pins. Unknown is diagnostic, not always an error. |
| Displays count in the wrong order | Verify output bit mapping. A NOR for the high bit is different from an OR. A Splitter only groups bits. |
| State disappears after reopen | .circ stores structure, not the current simulation. Reapply inputs and reset/load. |
| Screenshot incomplete | snapshot captures the visible GUI canvas. Zoom/pan in the controlled window, or use a separate diagram renderer. Headless mode has no snapshot. |
| Batch partially fails | Read completed/failedIndex. Earlier successful calls remain applied; batches do not roll back. |

## Constant zero and clock wiring

For a one-bit signal, x XOR x is always zero. Replacing that gate with Ground is
logically equivalent IF Ground is connected only to the old output net. The two
old inputs may both be clock wires: grounding them prevents counting or produces
driver conflicts. A component's anchor and input/output ports are different.

## Sequential formula example

For a synchronous four-bit up-counter, C0 is the low Q bit:

```
D0 = NOT C0
D1 = C1 XOR C0
D2 = C2 XOR (C1 AND C0)
D3 = C3 XOR (C2 AND C1 AND C0)
```

All DFFs use the same clock. Stopping at 8 can use run=NOT C3 only when the
reachable sequence is constrained to 0..8. Do not apply that shortcut to a
counter that must continue through 9..15. Reset initializes the counter; it is
not an extra clock. Derived clocks require careful timing; general hardware
designs should prefer synchronous clock enables when available.

## Boundaries

The wrapper does not prove arithmetic correctness, autoroute diagrams, attach
to existing processes, sandbox external project libraries, edit RAM contents,
export full timing diagrams, or record videos. Report those limitations instead
of claiming an unsupported deliverable. Use test traces for correctness and
the host/client's approved recording tools when a video is required.
