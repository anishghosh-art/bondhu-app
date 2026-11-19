# Roadmap

## Stage 1 - Bondhu Bench (this app)
Device profile, Bengali/Hindi/English speech checks, CPU micro-benchmark, shareable report, failure log. No ML model yet.
Goal: learn what real phones (especially cheap ones) can do.

## Stage 2 - Model bench
Add on-device model tests: speed, RAM use and heat, using LiteRT-LM and a tiny GGUF runtime.
Goal: decide which model fits which phone tier.

## Stage 3 - Real Bondhu app
Split into modules: `:core` (shared logic), `:device` (phone features and checks), `:app` (UI).

## Version plan
- **1.0**: phone-only, no server.
- **1.1**: server, credits and boost.
- **1.2**: call answering, Teach Mode and junior mode.
