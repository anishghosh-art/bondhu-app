# Decisions

Format: decided (D), open (O), unverified (U). Update this file when something changes.

## Decided
- D1. Standalone free app first (1.0 phone-only, no server). Server/credits/boost in 1.1. Call answering, Teach Mode, Junior mode in 1.2.
- D2. Capability ladder tiers 0-3 as in `ARCHITECTURE.md` section 1 (script-only / small local model / boost / BYNQ). Phone is profiled and benchmarked at first launch.
- D3. Privacy: mask identifiers before any cloud call; emotional/money/health local by default; logs are events only; reporting is opt-in.
- D4. Self-improvement only via signed prompt/template/config updates; never self-modifying code or permissions.
- D5. 18+ only in 1.0. Junior mode later, parent-linked.
- D6. Sideload distribution (Facebook page + website), no Play Store for now. In-app update reads Releases of this public repo (no separate releases repo needed).
- D7. Wallet money only for payments, never bank/PIN. Emergency numbers and trusted contacts built in and offline.
- D8. Local multi-agent = sequential roles; parallel workers only via Boost.
- D9. Credits are prepaid (Razorpay/UPI). Provider-agnostic router; several providers.
- D11. Licence: AGPL-3.0 for this app repo. Paid server code (credits, call answering) lives in a separate private repo.
- D12. Runtime: LiteRT-LM Kotlin API (`com.google.ai.edge.litertlm:litertlm-android`, documented as stable for Android). Models are `.litertlm` files from `litert-community` on Hugging Face. Verified 2026-11-19 from official docs and the HF API.
- D10. Android build verification happens in CI only (no SDK in the Claude workspace).

## Open
- O1. Which small models and which runtime (see "Unverified").
- O2. Exact Boost pricing and margin; credit packs.
- O3. Whether to offer an optional user-owned free Gemini key at all (key auto-creation via Accessibility may break Google ToS/UI).
- O4. Wake word "বন্ধু" for Bengali/Hindi (hard; openWakeWord is English-only).
- O5. Where the failure-report endpoint runs (Cloudflare Worker proposed; needs a free Cloudflare account of the project owner).

## Unverified / to research before relying on them
- U1. Model candidates to benchmark (files verified to exist): `litert-community/Qwen3-0.6B` (`Qwen3-0.6B.litertlm`, ~0.6 GB, Apache-2.0, ungated; int4 no-think variant in `Qwen3-0.6B-int4`), `litert-community/gemma-4-E2B-it-litert-lm` (`gemma-4-E2B-it.litertlm`, ~2.6 GB, Apache-2.0, ungated; may not fit when only ~2.4 GB RAM is free), Gemma3-1B (gated, skip for now). Bengali/Hindi quality of every small model is still UNKNOWN: benchmark must include Bengali/Hindi prompts judged by a native speaker. Exact Kotlin API usage must be read from the LiteRT-LM Android guide at build time. Earlier Piper voice names from Gemini docs are suspected wrong. `litert-community` also hosts small ASR models (whisper-tiny, moonshine-tiny, parakeet, Qwen3-ASR-0.6B): Bengali support unverified.
- U2. Sarvam Edge (STT/TTS/translation on device) — third-party access unconfirmed.
- U3. Phone STT: `isOnDeviceRecognitionAvailable` is true on the test phone, but whether the Bengali language pack is installed/works is unchecked.
- U4. Legal/commercial: Google Gemini API resale terms, GST on credits, DPDP Act rules for minors, virtual-number KYC, call-recording consent, TRAI rules.
- U5. All helpline numbers.
- U6. Per-OEM steps for battery optimisation, autostart, Accessibility on Android 13+ ("Restricted settings").
- U7. Free-tier limits of Cloudflare/Firebase/Groq change; re-check at build time.
