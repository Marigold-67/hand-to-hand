# Changelog

## 0.1.0-beta.1 — First public beta

- Promote the alpha.10 gameplay implementation to beta without changing gameplay Java classes, animations, movement logic or network protocol 10.
- Update mod version metadata, source build version and release documentation.
- Retain consent-based chat requests, coordinated movement, and optional administrator-controlled roleplay grabbing.
- Keep the GPLv3 license and third-party attribution. A separate beta-specific Minecraft regression test has not been performed.

## 0.1.0-alpha.10

- Evaluate passive vertical state coherently and share one sampling time per client tick; retain native voluntary jumping and local stair collision.
- Add bounded, collision-checked passive first-person view smoothing without changing physical positions or camera angles.
- Taper pair presentation using horizontal reach; use a shared filtered, reachable hand contact and gentler airborne gait.
- Make roleplay-grab enable activate and save its required movement following; verify live values and distinguish all permission/config blockers. Recognize permission-level-2 initiators as well as vanilla OPs.
- Add client-convergence/camera/pose comparisons and actual command, prerequisite and process-restart checks. Protocol 10 requires updating clients and server together.

## 0.1.0-alpha.9

- Bound horizontal separation during blocked-partner and opposing/compound movement, with predictive outward easing and no accumulated spring velocity.
- Add collision-surface routing and passive full-block uphill hops, retaining headroom, native collision and crouched ledge safety.
- Preserve normal steering, full native jumps, free camera and fixed held hands; keep optional G force-grab distinct.
- Add real-client hill/stair/ceiling/wall/ledge and measured high-ping checks. Protocol 9 requires updating server and clients together.

## 0.1.0-alpha.8.1

- Fix a short-tapped voluntary jump being clipped when normal hand-holding resumed control after Space release. Keep native jump physics through landing.
- Carry the leader's forward pace through a normal jump without requiring the follower to hold W.
- Add focused real-client mobility checks for first-person free-look, tapped/held jumps, sideways steering, forced-grab restrictions, and release.
- Keep G force-grab locomotion restrictions and protocol 8 unchanged.

## 0.1.0-alpha.8

- Buffer shared snapshots and average resolved pace; use bounded feed-forward reconciliation for passive movement.
- Allow cooperative forward pace matching while preserving lateral and opposing native input. Release passive server ownership when normal steering resumes.
- Keep ordinary sneaking/sneak-walking linked and account for crouched arm height.
- Interpolate passive body heading; retain actual walking animation on both remote actors.
- Add saved permission-level-2 commands: `/handholding config grab true|false` and `/handholding config grabOpOnly true|false`.
- Correct the config-file instructions for the tested NeoForge installation.
- Protocol 8 requires updating server and clients together.

## 0.1.0-alpha.7

- Move passive following to server-owned collision resolution and share ordered physical snapshots of both actors with both clients and nearby chunk watchers.
- Add monotonic-clock synchronization, bounded interpolation/prediction, stale-stream expiry, and scoped vanilla movement-packet integration.
- Drive both remote models' walking animation from resolved displacement, including when either real client leads.
- Keep the same hands and pair side until release; smoothly align the passive body without rotating its camera.
- Add separately rebindable G grab/release, disabled by default and OP-only when enabled. Retain H consent/release and target release, cooldowns, and live permission checks.
- Lift a passive survival partner during upright flight without granting flight; restore gravity on release.
- Validate with two independent TCP clients plus an observer, including animation packs, unequal Pehkui sizes, delayed TCP reads, walls, steps, and release/config behavior. Temporary hand gaps under delayed turns remain an alpha limitation.
- Protocol 7 requires updating the server and all clients together.

## 0.1.0-alpha.6

- Remove distance braking and shared movement-speed penalties from movement following.
- Copy leader displacement without an acceleration ramp or remembered tether velocity; correct toward a true side slot.
- Keep lateral movement free and use facing direction to preserve the slot through strafing.
- Allow a flying leader to lift a passive survival partner without granting flight. Scope floating-kick protection and gravity suppression to the current lift; restore gravity on release.
- Protocol 5 requires updating server and all clients together.


## 0.1.0-alpha.5

- Replaced accumulating lead impulses with bounded position following; repeated packets cannot build stored tether momentum.
- Automatically matches a passive partner to the leader's movement pace, with a cap on catch-up speed and no overshoot at arrival.
- Keeps the passive partner beside the leader and moves the side target along a gradual arc during turns.
- Allows the grip to adjust after a consistently less twisted hand pair becomes available, using the existing animation blend.
- Uses authenticated, rate-limited reports of each player's own movement controls to distinguish leading from passive following. Clients cannot select another player's position, speed, or force.
- Replaces, expires, and orders following plans; stale or duplicate plans cannot accumulate motion.
- Adds stopping, side-position, speed-cap, duplicate/out-of-order packet, and no-overshoot regression checks.
- Changes the movement protocol; update server and clients together.

## 0.1.0-alpha.4

- Added mutual dragging through a short, elastic hand tether. Either consenting player can lead; ordinary distance and reach gaps no longer unlink an active tether.
- Eased pull strength and changes of direction using lead-style directional weighting, with gradual resistance near the end of reach.
- Selected the held hands on the server at acceptance and kept them fixed across turns and role changes.
- Kept held arms reaching through temporary elastic/interpolation gaps without stretching the arm models.
- Preserved normal collision and terrain stepping, added vertical pulling when both partners already fly, and shared sprint pace when one grounded partner leads.
- Added partnership-scoped motion payloads; release discards late packets. Updated the network protocol, so server and clients must be upgraded together.
- Added configurable movementTether and tetherLength settings; disabling the tether keeps the previous coordinated-walking mode.
- Removed the interactive dummy's position-follow helper and replaced it with ordinary integration of the release mod's movement impulses.
- Added actual LocalPlayer dragging, handover, turning, opposing input, collision, terrain, flight, release, and relocation checks, plus pure tether-math tests.

## 0.1.0-alpha.3

- Fixed held-arm poses being overwritten by Entity Model Features player animations, including FA: Player.
- Added an optional client-only EMF bridge that reapplies the held arm and sleeve after the custom animation pass; it leaves other body parts alone.
- Added integration assertions against final rendered model transforms, alongside the PAL pose calculations.
- Clarified that the interactive dummy's automatic following is a test fixture; real-player dragging is not implemented.

## 0.1.0-alpha.2

- Added a configurable recovery period for momentary reach/visibility gaps, with immediate manual release preserved.
- Enabled coordinated sprinting by default.
- Added upright-flight compatibility and transient horizontal/vertical flight prediction factors.
- Improved the interactive dummy's response so it keeps up with turns, running, and flight.
- Added Minecraft integration checks for sprinting, brief-gap recovery, flight prediction, and cleanup.
- Increased the default hard-separation limit to 2.6 blocks; existing configurations retain their stored value.

## 0.1.0-alpha.1

- Added private chat requests with clickable Accept, Decline, and Cancel.
- Added recipient validation, request expiry, cooldowns, exclusive pairing, and persistent opt-outs.
- Added an original PAL animation layer with live, scale-aware hand contact and short pose blends.
- Added temporary shared walking pace, including Pehkui motion compensation.
- Added configurable request/release keys and server settings.
- Added automatic release and movement-modifier cleanup.
- Added consent/geometry unit tests and an opt-in Minecraft integration fixture.
- Deferred optional following, first-person hand posing, and full modpack/public-release validation.
