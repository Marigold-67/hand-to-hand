# Alpha.10 completed results

## Outcome and scope

The candidate preserves actual uphill traversal, native voluntary jumps, fixed held hands, ordinary cooperative movement, optional forced grabbing and release. Passive vertical samples no longer combine a delayed height with a newer landing state. Small, collision-checked first-person view damping and a shared reachable hand target add presentation smoothing while actual positions remain physical.

The configuration investigation reproduces an alpha.9 defect for an actual OP: the grab enable command leaves movement following disabled, and the grab receives the misleading disabled/OP error. Changing only that prerequisite makes the old grab succeed. Alpha.10 enables the prerequisite in the same save, verifies live values and reports distinct blocker reasons. This demonstrates a real matching error path; the user's remote server configuration and permission plugins were not observed directly, so this is not a claim that its exact state was confirmed. The target never needs OP.

## Old error reproduced and patched configuration checked

```text
PASS: Actual initiator is OP, not a target-permission problem
PASS: Old command reports/applies grabbing enabled
PASS: Old command leaves required movement following off
PASS: Reproduced the misleading disabled/OP error for an already-OP initiator
PASS: Restoring only the missing following prerequisite makes the old OP grab succeed
COMPLETE: OLD DEFECT REPRODUCED
```

All 44 original alpha.9 gameplay classes match the legacy reproduction byte for byte. Only diagnostic fixtures were added. The patched check explicitly starts with following OFF, then uses an actual OP's command source to enable grabbing, verifies following becomes ON, successfully grabs a non-OP target, waits through a file-watch interval, permits an actual non-OP initiator after the explicit policy change, and verifies saved settings. A real restart of the isolated host process reloads those settings, permits another grab and confirms that disabling revokes the live grab/control. Configuration commands are executed in the server dispatcher with real-player command sources; the general regression separately exercises real G/H commands over independent TCP clients. This does not certify third-party command/permission interception.

## Matched-path smoothing comparison

Three JVMs run an observing integrated host plus two actual TCP participants. Minecraft 1.21.1, NeoForge 21.1.250, PAL 1.1.6, Pehkui, EMF/ETF, Not Enough Animations, Skin Layers, Fresh Moves and FA Player are loaded in isolated instances. The comparison uses normal-size players and the passive participant's first-person view. Profiles schedule ordered stream delay, with measured echo RTTs below. They do not simulate real WAN congestion, packet loss, bandwidth limits or low server TPS.

| Scenario | Vertical tracking error p95, alpha.9 / candidate, blocks | Camera second difference p95, alpha.9 / candidate, blocks per 50-ms sample squared | Candidate median RTT ms |
| --- | ---: | ---: | ---: |
| Full-block hill, baseline delay | 0.343 / 0.000 | 0.097 / 0.114 | 74 |
| Stairs | 0.577 / 0.016 | 0.098 / 0.168 | 74 |
| Leader jumping, passive follower | 0.000 / 0.000 | 0.022 / 0.020 | 74 |
| Hill, medium delay | 0.263 / 0.000 | 0.112 / 0.111 | 325 |
| Hill, high delay | 0.311 / 0.000 | 0.130 / 0.113 | 550 |

Tracking error compares the local physical follower with its sampled physical actor. Camera second difference includes expected jumping, stepping and falling; it is not a pure jitter score. Values are not uniformly lower than alpha.9, so this report does not claim a universal percentage reduction or polished subjective comfort. More accurate full-height/landing motion can itself increase this simple world-motion measure. Arm angular-speed statistics are likewise not uniformly lower. Raw physical, camera, sampling, gait, arm and hand-gap traces are retained for inspection.

A separate short stair run records native and final camera coordinates on exactly the same frames. Its native/final p95 second difference is 0.3437/0.1839; the largest measured view-offset magnitude is 0.2663 blocks. This measures the filter on that path, not every player's view or every modpack. The view remains bounded inside the rendered actor and rejects solid collision; it does not move a fake partner next to the player. No first-person held-arm pose is implemented.

## Completed regression checks

- 77 passing JUnit tests: the existing 54 plus coherent sampling, reachable shared contact, frame-time/reset response, camera bounds and explicit grab-policy cases.
- Full unequal-size 0.75/1.15 animation-pack regression: walk/sprint, sideways/backwards motion, full turn, leadership handover, walls, size-appropriate steps, cooperative/opposed input, one/both sneaking, free look, G initiation, survival-target upright flight, H release and saved configuration/revocation.
- Expanded 1.0/1.15 terrain run with passive first-person view: four full-block rises/descent with either leader, actual stairs to an eight-block platform, one-sided wall recovery, crouched ledge safety, compound movement near 500+ ms RTT, both active uphill jumping, covered hill and no camera inside solid terrain.
- Normal-size directed hill recheck under a three-block-high ceiling, including actual first-person collision validation.
- Focused native-jump/control check with zero failed expectations. Exact measured results follow.

```text
PASS: Unlinked one-tick jump reference: height=1.2491870787446828, landed=true
PASS: Normal holding remains linked during passive free-look
PASS: Passive camera turns yaw/pitch while the other player walks
PASS: Passive participant is actually being led during free-look
PASS: Normal holding survives a one-tick jump tap
PASS: Normal holding preserves a full tapped jump: height=1.252203340253729, reference=1.2491870787446828
PASS: Jumping without W keeps moving with the leader: 3.3269812048846923 vs 3.5079094417185495
MEASURE: tapped jump delta=1.252203340253729
PASS: Normal holding preserves a held jump: height=1.252203340253729, reference=1.2491870787446828
PASS: Jumping did not cancel the normal link
PASS: Participant can steer sideways while being led: peak excursion=0.43023140200150517, later return delta=0.15097871918346634
PASS: Steering keeps the normal link and selected hands
PASS: Grab participant is actually being dragged during free-look
PASS: Grab target retains yaw/pitch camera control
PASS: Grab does not switch leader when target tries to move
PASS: Target H release clears movement control
PASS: After releasing grab, a tapped jump is fully native again: height=1.252203340253729
CHECK COMPLETE: 0 failed expectation(s)
```

## Rejected attempts and test corrections

An initial coherent-velocity candidate passed server traversal while the passive local actor could miss a full-block rise. It was rejected after local convergence diagnostics exposed the discrepancy. Vertical pose following and local grounded stair/fall handling were revised; final convergence checks cover the controlled physical actor, not an active native player intentionally ahead of buffered time. One intermediate assertion incorrectly applied that passive check to the active player and was corrected.

The inherited short-strafe check sampled net displacement several ticks after input ended and return-to-formation had begun. One run measured 0.1475 blocks against a 0.15 threshold even though the client trace had already steered much farther. The corrected check retains the 0.15 threshold and measures the authoritative peak excursion during the actual input/initial return window; the final control run passes. It does not suppress return-to-formation to satisfy the test.

The batch launcher initially flattened native DLLs from multiple architectures; the x64 extraction is corrected. A later JVM exited before world startup and that run was retried in a fresh folder with staggered launches. These startup attempts are not passing gameplay checks. A dedicated-server target was attempted but the installed runtime lacks Minecraft server libraries; configuration persistence was tested with an integrated host process restart, not a certified dedicated installation. No clean dependency-resolution Gradle build or general process-memory/long-duration certification was performed.

## Build, evidence and remaining assessment

The production JAR uses Java 21 installed-library compilation, contains no smoke fixtures or dependency classes, and matches the final tested smoke gameplay classes. Metadata, mixins, license, JSON, class version and ZIP integrity are audited. Evidence includes results, raw traces, echo RTTs, transport profiles, resource-pack loading, movement warnings, class identity and the old defect reproduction. Screenshots of the game are not bundled. The user's regular instance and remote server were not changed.

These are targeted comparisons and regression runs, not an hours-long soak or full-modpack/dedicated-server certification. Actual internet behavior, extreme scales, other flight/movement mods, arbitrary parkour and human comfort remain outside the numeric pass. The useful next assessment is the passive player's view on the user's real server, with matching alpha.10 on every client and the server. Re-run `/handholding config grab true` after updating to apply the prerequisite fix to an existing configuration.
