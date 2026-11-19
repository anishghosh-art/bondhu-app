# Roadmap

## Stage 1 - Bondhu Bench (this app)
Device profile, Bengali/Hindi/English speech checks, CPU micro-benchmark, shareable report, failure log. No ML model yet.
Goal: learn what real phones (especially cheap ones) can do.

## Stage 2 - Model bench (0.2.0: BUILT, being field-tested)
In-app model download (resumable, size/space/mobile-data checks), model bench with LiteRT-LM (speed, RAM, heat, Bengali/Hindi/English replies, JSON intent check), in-app update through the GitHub Release, permanent signing key in CI.
Still to do: run it on real phones, have a native speaker judge the Bengali/Hindi replies, decide which model fits which phone tier. A GGUF runtime is not planned for now.
Goal: decide which model fits which phone tier.

## Stage 3 - Real Bondhu app
Split into modules: `:core` (shared logic), `:device` (phone features and checks), `:app` (UI).

## Version plan
- **1.0**: phone-only, no server.
- **1.1**: server, credits and boost.
- **1.2**: call answering, Teach Mode and junior mode.
