# CRITERIA — DuraPing 1.0.0 (Fabric 1.21.1, Java 21)

Evidence rule: a box is checked only with named evidence (CI run URL, file
contents, in-game observation). Never from memory.

## Build & repo (cloud-only)

- [x] Folder `DuraPing/` exists in the Mods workspace with the full project inside it.
- [x] Public repo `DuraPing` exists under the authenticated account
      (https://github.com/Sekai0NI0itamio/DuraPing).
- [x] `./gradlew build` passes on GitHub Actions (Temurin Java 21, Gradle 8.10.2);
      mod JAR attached as artifact. Evidence: run 38891517099
      (https://github.com/Sekai0NI0itamio/DuraPing/actions/runs/38891517099),
      BUILD SUCCESSFUL, `duraping-1.0.0.jar` (28,469 bytes, non-empty).
- [x] All unit tests pass in CI (`:test` executed, not skipped; 3 test classes:
      DurabilityWarnEvaluator (14), DuraPingConfig (10), MessageFormatter (5);
      29 tests, 0 failures, 0 errors). Evidence: same run, test-reports artifact.
- [x] NEVER built locally — only via `.github/workflows/build.yml`
      (workspace has no `build/` output; all compile/test evidence is CI).
- [x] No production release and no Modrinth/CurseForge upload without explicit
      authorization. Evidence: no `release` job in the workflow, no published files.

## Correctness (must all hold)

> Status: warning logic is unit-tested in CI (run 38891517099) and the mod
> compiles against 1.21.1; in-game confirmation is pending a client test run —
> boxes stay open until then.

- [ ] Warns at configured ordinary thresholds (default 10 and 3).
- [ ] Critical countdown warns at 3, 2, 1 by default without repeats.
- [ ] Threshold crossing detected on skipped values (e.g. 11 -> 8 still reports 10).
- [ ] Overlapping ordinary + countdown values produce exactly one (critical) message.
- [ ] Main hand, offhand, helmet, chestplate, leggings, boots each monitored.
- [ ] Each slot can be enabled/disabled via command and config.
- [ ] All warnings are local client-side chat messages (no server traffic).
- [ ] Repairs re-arm thresholds; equipment swaps never warn by themselves.
- [ ] Creative mode and non-damageable items never warn and never crash.
- [ ] Malformed config preserves the bad file and falls back to defaults.

## Docs

- [x] README documents install, commands, config, anti-spam, limitations, CI artifact.
- [x] MIT LICENSE present and bundled into the JAR.
