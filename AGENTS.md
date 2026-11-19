# AGENTS.md — read this first (for any new Claude session or sub-agent)

## What this repo is
**Bondhu (বন্ধু)**: a free, standalone Android "best friend + agentic assistant" for ordinary, non-technical Indians
(farmers, Rapido drivers, shop owners). Local-model-first, Indian languages (Bengali first, then Hindi), casual friend
personality. Optional cloud "boost" later. Separate future product: **BYNQ Phone** (Android client of the BYNQ Harness),
which will share code with Bondhu. Today this repo only contains **Bondhu Bench**, a test tool. The real app is NOT built yet.

Read in this order: `docs/ARCHITECTURE.md` (target design + honest status), `docs/DECISIONS.md` (what is decided / open),
`docs/ROADMAP.md`. Older Gemini-built docs and code live in Google Drive ("Our Apps (Products)/BonDhu"); they were never
compiled, so treat them as ideas, not truth.

## Who you work for
The project owner (a beginner in coding/ML) writes casual Bengali. Private working notes about the owner are kept outside this public repo.
- Reply in Bengali (simple words, English technical terms are fine). Be direct and honest, no fluff.
- Say clearly what is done, what is NOT done, and what is unverified. Never say something works unless it was run.
- He likes collaborative iteration. Plan as the "brain", delegate execution to cheaper/smaller subagents.
- Prefer free tiers; ask before anything that costs money.

## Hard rules
1. **Privacy**: never store/send message text, contacts, audio, or numbers in logs. Failure logs = events only
   (code, short technical message, phone model, Android SDK). Reporting needs user opt-in. Sensitive topics
   (emotional, money, health) are local-only unless the user opts in; identifiers are masked before any cloud call.
2. **No secrets in the repo or APK.** API keys live only on a server. Signing keystore lives in GitHub Secrets
   with a backup the user keeps in his Drive.
3. **Self-improvement limits**: the system may only update prompts, templates and config that we sign and ship.
   It must never modify its own code or grant itself permissions.
4. **Safety**: no pretending to be a doctor/lawyer; crisis signals hand off to helplines (verify numbers before shipping,
   e.g. 112, Tele-MANAS 14416). The agent never uses bank accounts/PINs; wallet money only.
5. **18+ only in version 1.0.** A separate parent-linked "Bondhu Junior" comes later (India DPDP Act needs verifiable
   parental consent; verify before building).
6. Do not claim legal/ToS facts as settled (Google API resale terms, GST, DPDP rules, virtual-number KYC): mark as "verify".

## How to build and verify
- There is **no Android SDK in the Claude workspace**. Builds happen in GitHub Actions (`.github/workflows/build-bench-apk.yml`).
  Licence: AGPL-3.0 (see `LICENSE`). Check the run with `gh api repos/anishghosh-art/bondhu-app/actions/runs/<id>` (GraphQL-based `gh` commands are blocked here).
- Artifact storage quota is full, so CI publishes the APK to the GitHub Release `bench-latest` instead.
- Kotlin package is `` `in`.bynq.bondhu.bench `` (`in` is a Kotlin keyword, so backticks).
- Commits end with the attribution lines the session asks for. Push only when the user has asked for it.

## Current status (2026-11-19)
- Permanent signing key: the owner must create it once and add the 4 GitHub Secrets (names in README, section Signing). Never commit it.
- Bondhu Bench 0.1.0 built by CI and tested once on one mid-range phone (Android 16, MediaTek, ~7.6 GB RAM,
  only ~2.4 GB free at test time). Report: Google TTS has bn-IN/hi-IN/en-IN voices; on-device STT reported available
  (Bengali language pack NOT yet verified).
- Version 0.2.0 (model download, model bench, in-app update, signing in CI) is BUILT in code but not yet compiled/field-tested at the time of writing; check the CI run and the status table in `docs/ARCHITECTURE.md`.
- Not built: everything else (see `docs/ARCHITECTURE.md`, status table).

## Open verification items (do these before relying on them)
See `docs/DECISIONS.md` section "Unverified / to research".
