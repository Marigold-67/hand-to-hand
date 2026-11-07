# Optional Windows TCP test runner

These scripts reproduce the isolated three-JVM harness used for alpha.9. They are not part of the gameplay JAR. They use an existing Minecraft installation and never download dependencies. The Java fixture source is `src/smoke/java/dev/marigold/handinhand/client/PairNetworkTest.java`.

Set these PowerShell environment variables to absolute paths:

- `JAVA_HOME`: installed Java 21 root, containing `bin/java.exe`.
- `HANDINHAND_MC_INSTALL`: Minecraft installation containing `versions/neoforge-21.1.250` and mapped libraries/assets.
- `HANDINHAND_PAL_JAR`: PAL 1.1.6+mc.1.21.1 JAR.
- `HANDINHAND_PEHKUI_JAR`: Pehkui 3.8.3+1.21-neoforge JAR, when using `--pehkui`.
- `GRADLE_USER_HOME`: optional existing Gradle cache containing JUnit dependencies for `--tests`.
- `HANDINHAND_ANIMATION_INSTANCE`: optional instance with the tested animation mod/resource-pack filenames, for `--emf`.

From this project's root, compile with `python tools/tcp_test/build_local.py --smoke --tests`. This uses the same source sets as the tested build and writes a separate `-smoke.jar`. Production compilation omits `--smoke`.

In four terminals, use the same `PAIR_RUN` (an unused output folder), `PAIR_HOST_PORT`, and `PAIR_PROXY_PORT`. For example set `PAIR_RUN=work/tcp-run`, host port 49181 and proxy port 49182. Start:

1. `python tools/tcp_test/pair_proxy.py 49182 49181 0 work/tcp-run`
2. `python tools/tcp_test/launch.py --role host --pehkui --wait`
3. `python tools/tcp_test/launch.py --role alpha --pehkui --wait`
4. `python tools/tcp_test/launch.py --role bravo --pehkui --wait`

The proxy binds 127.0.0.1 only. The disposable integrated host disables authentication for these offline test identities, and writes its own temporary world/configuration under PAIR_RUN. Never point PAIR_RUN at an existing gameplay instance. DONE.txt or FAIL.txt ends all clients. The runner does not connect to a public server or use account tokens.

For the expanded run, choose a new PAIR_RUN/ports, use proxy delay argument 20 instead of 0, and add `--emf --scaled` to all three launch commands. This adds 20 ms per ordered TCP read in each direction, not an exact RTT or packet-loss simulation. The required animation mods/resource-pack names are explicit in `launch.py`; they are copied from the provided instance into the disposable test directories.

After completion use `python tools/tcp_test/summarize.py work/tcp-run`. Inspect SERVER-RESULTS.txt, DONE/FAIL, METRICS.json, logs, and both players' rendered walking/turning/lifting. A passing numeric suite does not certify every human interaction or every modpack.

Current results are in TEST-RESULTS.md; older reports are historical.

For the focused mobility check, add `--mobility` to all three launcher commands. This runs a separate one-tick jump reference and tests free-look, normal tapped/held jumping, sideways steering, grab restrictions, and release. Read MOBILITY-RESULTS.txt as well as DONE.txt. The preserved original alpha.8 investigation intentionally records one failed expectation; alpha.8.1 must record zero.

For the long run, use `latency_proxy.py HOST_PROXY_PORT HOST_PORT PAIR_RUN` instead of pair_proxy.py, and add `--endurance` to all three launch commands (plus the desired existing animation/scale flags). The test writes NETWORK.json profiles itself and measures actual ClockPayload echo RTTs. Add `--directed` to all three launch commands for the corrected short flight/linked-disconnect recheck. Use a fresh PAIR_RUN and port pair for each run. The full run takes about 12.5 minutes after setup; the first six movement phases maintain one pair for ten minutes. The script currently includes the corrected flight/regrab fixture described in ENDURANCE-TESTS.md.

For alpha.9 terrain/range testing, use `latency_proxy.py PROXY_PORT HOST_PORT PAIR_RUN` and `--terrain` on all three launch commands. The fixture writes its own baseline/high-latency profile, measures real echo RTT, and checks full-block hills in both leadership directions, stairs, a one-sided wall, a crouched ledge, compound input at about 500 ms RTT, both-active uphill jumping and low ceiling clearance. Inspect TERRAIN-RESULTS.txt, TERRAIN-TRACE.csv, both RTT CSVs, DONE/FAIL and logs. Use fresh run folders/ports. `--scaled` uses 1.0/1.15 for terrain; the general regression uses 0.75/1.15.

For just the cooperative/covered hill recheck, use `--terrain --terrain-directed`. Without `--scaled`, it verifies a normal-size pair under a three-block-high ceiling; the larger-pair fixture uses four-block clearance.

Alpha.10 comparisons: use `--smoothing` on all three clients with latency_proxy.py and fresh run folders/ports. The fixture records first-person camera, physical positions, sample/ground states, actual rendered arms, hand gaps and measured RTTs. `--smoothing --smoothing-stairs --legacy-camera` isolates the new physical stair route with native camera. Diagnostic alternatives are `--legacy-timeline`, `--legacy-presentation`, `--legacy-pose`, and `--legacy-gait`. These properties affect presentation/following, not server authorization. `--grab-config` runs the OP/non-OP/prerequisite test; reuse only the isolated host game directory with PAIR_GAME_HOST and use `--grab-restart` for its second process. Never reuse DONE/FAIL folders or point this at a gameplay instance.
