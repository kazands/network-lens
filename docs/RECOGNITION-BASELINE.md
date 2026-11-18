# Recognition and performance baselines

Keep synthetic regressions, Android integration and physical compatibility
results separate. Associate measurements with the exact APK SHA-256.

## Physical compatibility checklist

1. Use only the authorized Pixel 8. Keep Network Lens awake and foreground; stop
   inspection if it leaves the foreground. Never install instrumentation on a
   phone or operate another connected device.
2. Establish expected make/model from the owner or product label. Confirm current
   network membership; a previous IP can have a different occupant.
3. Test awake devices and ordinary sleeping states separately. Include available
   phones/tablets, television, thermostat with audio services, network equipment,
   lights/plugs and multi-zone audio. Do not extrapolate to untested families.
4. Run a fresh scan. Check identity, physical category, grouping and technical
   sources. Record presence, identity request, response, parsed identity, conflict
   and displayed result separately. Unknown stages use `null`. A response-read
   failure does not prove a parser/catalogue defect.
5. Repeat, Stop/scan again, and leave/return. Check cancellation and fresh results.
   Coordinate network-disconnection checks separately because they end a scan.
6. Check actual TalkBack focus/reading order, buttons, collapsed details, both
   appearances, large text and Back/list position. Report this separately from
   emulator accessibility-node checks.

## Redacted baseline

Keep physical input in memory and pass it through standard input (`--input -`);
save only the aggregate report. Synthetic examples may use local input files.
Use arbitrary
numbered labels and public manufacturer/model/category only. Never include IP,
MAC, SSID, serials, credentials, owner/personal device names or screenshots.
The tool rejects additional fields, but cannot prove arbitrary model text is
public data. Use the same hardware label for known addresses of one unit and the
same group label only when the app grouped them together.

```json
{
  "environment": "synthetic",
  "apkSha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "devices": [{
    "id": "id-1", "hardware": "hardware-1", "group": "group-1",
    "expected": {"manufacturer": "Example", "model": "Model One", "category": "Thermostat"},
    "observed": {"manufacturer": "Example", "model": "", "category": "Thermostat"},
    "stages": {"present": true, "requested": true, "response": false, "parsed": null, "conflict": false}
  }]
}
```

For physical results replace the environment/hash, use `--input -` and supply redacted
observations. Unknown expected values use empty strings and do not enter their
field's denominator. Compare canonical model names, not a hardware code against
a retail name.

```sh
python3 scripts/assess-recognition.py --input .tools/baseline.json \
  --output artifacts/recognition-baseline/report.json
```

The aggregate report separates discovery misses, missing identity opportunities,
unavailable responses, parsing failures, conflicts and later selection/display
mismatches. Unproven stages remain unknown. It records field-match denominators,
distinct known hardware found and grouping merges/splits. These metrics apply
only to the supplied baseline, not a universal recognition-accuracy rate.
Stage labels locate unavailable information; they do not prove an application
defect. A correctly rejected malformed response or an unreported manufacturer
must not be scored as a demand to invent facts. Keep interpreted product identity
separate from device-reported fields, and leave expectations blank where the
display contract deliberately keeps an unsupported fact unknown.

## Performance

Emulator `performance` records index loading, heap delta, PSS, thread count,
256-endpoint snapshot/grouping timings and sample Activity launch-to-idle. It is
explicitly synthetic and has no flaky speed threshold. Launch-to-idle is not
cold-process first-frame timing; heap delta is affected by garbage collection.

For authorized physical measurements, leave Network Lens foreground and start
a scan manually if desired:

```sh
python3 scripts/measure-phone-performance.py --serial YOUR_AUTHORIZED_PIXEL_SERIAL \
  --samples 10 --interval 3 --output artifacts/phone-performance/report.json
```

The read-only helper verifies the Pixel 8 target and foreground before/after each
inspection. It records APK hash, aggregate PSS, threads and cumulative rendered/
janky frames. Foreground loss or process/APK change stops it. It cannot launch,
stop, install, start scans, clear data, read results or take screenshots. Compare
matched workloads and sampling windows, not cumulative counts from different
process lifetimes. Cold startup, scan/cancellation latency, actual TalkBack and
battery need separate physical checks. Keep reports ignored; publish only reviewed
aggregate evidence, never household data.
