# Decisions

Format: decided (D), open (O), unverified (U). Update this file when something changes.

## Decided
- D1. Standalone free app first (1.0 phone-only, no server). Server/credits/boost in 1.1. Call answering, Teach Mode, Junior mode in 1.2.
- D2. Capability ladder tiers 0-3 as in `ARCHITECTURE.md` section 1 (script-only / small local model / boost / BYNQ). Phone is profiled and benchmarked at first launch.
- D3. Privacy: mask identifiers before any cloud call; emotional/money/health local by default; logs are events only; reporting is opt-in.
- D4. Self-improvement only via signed prompt/template/config updates; never self-modifying code or permissions.
- D5. 18+ only in 1.0. Junior mode later, parent-linked.
- D6. Sideload distribution (Facebook page + website), no Play Store for now. In-app update via public `bondhu-releases` repo.
- D7. Wallet money only for payments, never bank/PIN. Emergency numbers and trusted contacts built in and offline.
- D8. Local multi-agent = sequential roles; parallel workers only via Boost.
- D9. Credits are prepaid (Razorpay/UPI). Provider-agnostic router; several providers.
- D11. Licence: AGPL-3.0 for this app repo. Paid server code (credits, call answering) lives in a separate private repo.
- D10. Android build verification happens in CI only (no SDK in the Claude workspace).

## Open
- O1. Which small models and which runtime (see "Unverified").
- O2. Exact Boost pricing and margin; credit packs.
- O3. Whether to offer an optional user-owned free Gemini key at all (key auto-creation via Accessibility may break Google ToS/UI).
- O4. Wake word "বন্ধু" for Bengali/Hindi (hard; openWakeWord is English-only).
- O5. Where the failure-report endpoint runs (Cloudflare Worker proposed; needs a free Cloudflare account of the project owner).

## Unverified / to research before relying on them
- U1. Model candidates: Qwen3 0.6B, Gemma 3 1B, Gemma 4 E2B (heavier). Bengali/Hindi quality of every small model is unknown. Official file formats, licenses and download URLs for the chosen runtime (LiteRT-LM `.litertlm` vs GGUF/llama.cpp) must be checked in the runtime's own docs first. Earlier Piper voice names from Gemini docs are suspected wrong.
- U2. Sarvam Edge (STT/TTS/translation on device) — third-party access unconfirmed.
- U3. Phone STT: `isOnDeviceRecognitionAvailable` is true on the test phone, but whether the Bengali language pack is installed/works is unchecked.
- U4. Legal/commercial: Google Gemini API resale terms, GST on credits, DPDP Act rules for minors, virtual-number KYC, call-recording consent, TRAI rules.
- U5. All helpline numbers.
- U6. Per-OEM steps for battery optimisation, autostart, Accessibility on Android 13+ ("Restricted settings").
- U7. Free-tier limits of Cloudflare/Firebase/Groq change; re-check at build time.
