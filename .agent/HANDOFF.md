# Current Handoff

## State

The simplified §30 basic Dynamic Settlement Cycle is implemented end to end, after core `mvp-v1.0` and binary WILD/CLAIMED transitions. Config 15. Native-client/user acceptance and deployment-load tuning remain separate. No Civic Capacity, mixed Buildings, fragments or new simulation.

## Completed

- CycleService/JooqCycleRepository persists exact release group and deadline. One Settlement per check; up to 16 rotating candidate seeds with preferred existing notice. Whole Building/Parcel closure, RUINED and zero-Holder checks, retained connectivity and exterior-reachable removal prevent holes or split ownership.
- Default grace 168000/check 1200 shared game ticks. Online Owner/member notices, login readback and `/settlement cycle [settlement|UUID]` expose coordinates/deadline. Support recovery invalidates notice transactionally; changed geometry/ownership/config restarts grace. Scheduler and SQL transaction recheck clock advancing; no downtime accrual/forced loads.
- Release uses existing Building reconciliation/cancellation and publication gate after Activity drain. Unassigned Parcel cells/name/revision/policy are archived. Containers remain untouched. Safe terminal groups remove all Claims, active memberships and pending governance; history survives.
- Historical Treasury beneficiary is the last Owner, not an active role or new claimant. Existing balance/history/withdraw commands accept the old UUID. Only idempotent TRANSFER to that beneficiary's Wallet is allowed; historical deposits, grants, payment to others and budgets reject. Unused Repair materials remain manually refundable to their original payer independently of roles.
- New forward `cycle-release.xml`; generated table ownership updated. Config migration backs up `config-before-cycle.yml`. Existing ordinary unclaim/abandon guards remain; automatic lifecycle is separate.

## Changed files

New cycle service/repository, Paper CycleRuntime and topology regression; existing local Activity recovery hook, Settlement Treasury entitlement port and factories, certification cancellation state/history catalog, ledger authorization and callers, Paper coordinator/config/commands, schema master/codegen filter and old-fixture teardown. Regression additions in RepairServiceTest and LedgerServiceTest. Architecture §30 implementation status, README/RELEASE, AGENTS and .agent status/evidence updated.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain`: **150 tests, zero failures/errors/skips**. No Java/schema changes after this build.
- Regression scenarios: topology holes/disconnection/peripheral/end; persistent grace/restart and overdue frozen rejection; activity recovery/new Holder; whole Parcel/ruin terminal release with original-payer refund; nonempty Treasury termination, denied former-member access, manual withdrawal/replay/restart and forbidden historical payments/deposits.
- Real Paper `.run/basic-cycle`: new Settlement, 25 Treasury coins, unassigned Parcel, chest with three diamonds; notice survived stop/start and was delivered on Owner login. After time advance, final Claim and active members/Parcel ended, Parcel history archived, chest inventory unchanged. Last Owner withdrew 25; repeat transaction paid nothing again; former member was denied.
- A second Settlement's real block break credited local support and cancelled notice. After adding a Claim and assigning a Holder to the original Parcel, low support released only the unassigned peripheral Claim; assigned Holder Claim remained through further elapsed time.
- A third Settlement registered Roof and naturally decayed to RUINED. Partially supplied Repair reserved one material. Automatic grace release made the same Building WILD, cancelled Repair safely and preserved one refundable material. Unrelated player refund denied; original payer collected once; Era remained zero.
- Final artifact restarted and created no notices during frozen game time. After resume, a new Settlement notified and terminated with actionable historical Treasury UUID/command; remaining Holder protection stayed enabled.
- Previous binary-transition installation copied to `.run/basic-cycle-upgrade`, scheduling disabled: all pre-existing gameplay rows unchanged, only Liquibase metadata changed; three new tables empty, config 15/backup present, READY reached. Original development data untouched.
- Ignored evidence: `.run/basic-cycle/acceptance.log`, checkpoint.json, server logs/world/DB; `.run/basic-cycle-upgrade` migration copy. Temporary driver removed during cleanup. No native visual acceptance claimed; protocol clients and server commands supplied runtime evidence. One final smoke query used a nonexistent test-only column and was corrected to the actual quote relation before the successful final run.
- Final JAR SHA-256: `bfeb1968c05b8360a2ffefd9445c25fded7efc8e9771321e896895982376e032`. Both Paper fixtures stopped with exit 0; driver exit 143 was intentional supervisor shutdown. README/RELEASE/current-note links resolve; no temporary driver remains.

## Next action

Run native-client/player acceptance and deployment-specific load/tuning on a disposable copy using README/RELEASE. Basic coding is complete; do not resume a design questionnaire or automatically start deferred systems.

## Risks or blockers

- Cross-boundary Buildings remain rejected. Existing single-chunk purchase cannot partially adopt multi-chunk wild Buildings; no bulk-claim/fragment subsystem was added.
- Bounded cycle guards retain protection for oversized metadata/topology (1024 Claims/Buildings, 32768 Parcel cells, 65536 exterior cells); operators must reduce unsupported scale, not bypass safety.
- Disabling scheduling preserves notices while normal game time may continue; review pending deadlines before re-enabling. Use paired world/player/database cold backups.
- Native-client UX and representative large-deployment capacity are still user acceptance, not missing basic cycle code. No remote push, new tag or production deployment performed.
