# Remote animation replay hooks

These hooks prepare Blockfield ADR4 replay. They do not activate it, select a
remote player, define a packet schema or authorize a release. The Blockfield U1
typed schema remains the only serialized state. No tag or compatibility pin is
changed by this branch.

`Animation.setDriven(player, true)` refuses the local player. For a driven
remote, native action physics/lifecycle still run, but `Animation.tick`, frame
option dispatch, action render callbacks and render-time removal cannot advance
the retained animation. `restoreDriven` replaces the active scratch animator,
passive object and option without calling start/finish/tick. Each invocation must
receive fresh materialized scratch from its immutable checkpoint; native callbacks
can still mutate that scratch. The separate driven animator slot prevents a
compatibility mixin's direct native-field assignment from replacing it.

The animator tick, action counters and passive counters have explicit import/read
hooks. They do not serialize Java objects. Animator subtype fields, deterministic
Hang/Dive transitions, target intent, event delivery and observer seed projection
still require the Blockfield family adapters before this API can be enabled.
Leaving driven mode requires the caller's normal compatible reseed/reset path.

Local `ClientSetting` snapshots reread only EnableAnimation,
EnableFallingAnimation and EnableLeanAnimationOfFastRun through their live native
getters. A policy override installed after the snapshot was created therefore
reaches later native ClientInfo encoding and the local lean read. All other
snapshot entries remain snapshots; decoded remote settings remain decoded values.
Policy must be installed before the first admitted ClientInfo encoding, or a fresh
ClientInfo must be sent after installation. This API cannot retroactively change
a packet already sent before JOIN.

Validation: `lintJava regressionCheck` with Java 21, `--no-daemon --max-workers=2`
and a 2 GiB Gradle heap. Regression checks use an in-memory config and
constructor-free entities; they start no Minecraft client/server or window. The
Blockfield transformed oracle still uses its pinned bf7 reference. A later paired
pin must run the production transformed hooks before exact-cohort activation.
