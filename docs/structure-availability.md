# Disabled structure detection

Search requests are classified on the server after ID/unlock validation and before
XP checks, search sessions or workers. Confirmed disabling blocks searches even if
old instances exist. Precedence per structure is YUNG, Structurify, generic.

Generic checks examine all loaded server dimensions: compatible generator biomes,
active placements, and the frequency of exact vanilla random-spread/ring placement
classes. One working placement preserves availability. Custom placement subclasses
are treated conservatively; repeated failed candidates do not prove disabling.
No viable dimension means disabled; a viable different dimension means the existing
wrong-dimension popup. Disabling world structure generation is also detected.

Optional adapters do not add mod dependencies. Seven YUNG 1.20 replacements are
mapped by the vanilla structure type their generation hook blocks, reading live
configuration flags (Strongholds has an unconditional blocker). Suggested variants
are restricted to IDs registered and allowed by this server. Structurify's verified 2.0.43 API
supplies global/per-structure flags, set disabling, zero weights and frequency
overrides. Original set membership is consulted when the mod removes entries.
An unchanged default frequency of zero is attributed to generic settings, not
Structurify. Unknown API shapes log once and do not guess a disabled state from
inaccessible configuration. No lookup result is cached across search requests.

A separate availability packet reports eligible IDs, wrong-dimension count and
structured disabled reasons/replacements. Request IDs and searching state prevent
stale replies from opening popups. Older clients without this channel receive a
normal rejection for disabled requests. For mixed Search Listed requests the server
skips unavailable entries; the client only includes actually searched IDs in the
existing failure Hide All action. When all eligible entries are disabled, the
aggregate popup offers Back only. Single-entry popup Hide uses the existing local
hidden-structures configuration. Popups do not time out.

Replacement links change the search text and focus the row; they do not search.
A temporary navigation override reveals a row despite client filters/hidden/locked
settings. It clears when the user changes the query and never edits those settings.
The text input is focused normally; the row is selected and scrolled into view.

Validation:
- Full JUnit suite covers optional-mod absence/unknown shapes, all seven YUNG
  mappings and flags, precedence, Structurify flags/sets/weights/frequency, generic
  eligibility, mixed selections, packet roundtrip, stale replies, exact message
  styling, and bytecode checks for validation before search sessions/costs.
- A temporary test mod (outside the repository) launched copied test worlds,
  captured all three popup styles plus the Back-only aggregate view at 854x480,
  and asserted replacement query/row navigation in the real GUI.
- Runtime checks passed with YUNG Better Desert Temples 3.0.3; with Structurify
  2.0.43 and YUNG together (including precedence and live flag changes); and with
  neither optional mod (including actual zero-frequency and wrong-dimension checks).
  A real disabled search request created no session and changed neither XP nor durability.
  The Structurify test used Fabric API 0.92.0 solely for its configuration-library
  dependency. The project remains on its original Fabric API dependency.
- Other YUNG mappings were source-verified and exercised with config fixtures;
  they were not all separately installed for runtime checks.

Benchmark tools remain on their separate branch. No existing test worlds or normal
mod installations were changed by the disposable runtime validation.
