# Bondhu architecture (target design + honest status)

Status legend: DONE = built and run on a real phone. BUILT = compiles in CI, not field-tested. PLAN = designed only.

## 1. Capability ladder (the user never has to know about it)
| Tier | What the user needs | What Bondhu can do |
|---|---|---|
| 0 | Nothing (no download, no account, no key) | Scripted friend talk, phone's own STT/TTS, expense tracking from SMS (code only), reminders, WhatsApp reply templates |
| 1 | One-time model download (WiFi preferred, mobile data allowed after a size warning, resumable) | Small local model for chat and for filling templates |
| 2 | "Boost": cloud model via credits (later also optional user-owned free Gemini key) | Better replies, several parallel workers |
| 3 | BYNQ subscription / BYNQ Harness link | Larger agents, VM, shared with BYNQ Phone |

On first launch Bondhu profiles the phone (RAM, Android version, SoC, free storage, thermal, OEM) and runs a short
benchmark, then picks a tier and model. Free RAM matters more than total RAM (the test phone has 7.6 GB total but ~2.4 GB free),
so plan models around ~1-1.5 GB.

## 2. Local multi-agent reality
One model's weights load once; each extra session only adds KV-cache memory. The real limit is compute and heat: sessions
slow each other. So locally we run roles (planner, doer, checker) **sequentially**: better quality, not more speed.
True parallel workers exist only with Boost (cloud). Boost cost is metered per user.

## 3. Cloud use and privacy
- Plain HTTPS is not enough for sensitive text, because the model must read it. Instead:
  1. Everyday chat/tasks may go to cloud with identifiers masked on the phone (names, numbers, accounts, addresses become placeholders, restored on return).
  2. Emotional/money/health topics: local by default; cloud only if the user opts in, and only to providers that do not train on data.
  3. Most private topics: never leave the phone.
- A server-side router chooses provider per task (difficulty, cost, privacy). User sees only: cheap / balanced / best.
  DeepSeek is fine for code/translation, not for sensitive content (China-hosted). Providers: several, not one (do not depend on a single aggregator).
- Server (v1.1) is a thin proxy: auth, credit balance, provider routing, metering, Razorpay webhook. Start on free tiers (e.g. Cloudflare Workers). Keys never in the APK.
- Payments: prepaid credits via Razorpay (UPI), not monthly autopay.

## 4. Phone control (all in 1.0, permission asked just-in-time, one at a time)
Accessibility node tree as text first, screenshots only as fallback; notification listener; SMS read for expenses; intents for apps.
Android 13+ sideloaded apps need the user to allow "Restricted settings" before Accessibility; aggressive OEMs (Xiaomi, Oppo, vivo,
realme, Samsung...) need a per-brand battery/autostart guide. Install is by sideload (Facebook page + website), not Play Store.

## 5. Friend behaviour
Casual, proactive, warm, growth-oriented, Bengali/Hindi slang allowed. No fear-first onboarding. Learns the user by asking.
After trust it asks for contact/call-log access and then asks "who is this?" about frequent numbers; emergency contacts emerge
from that conversation. Built-in offline emergency numbers (112, police, ambulance, railway, women/child, cyber, Tele-MANAS) — verify each before release.
Trusted-contact SOS = SMS with location, works offline.

## 6. Call answering (v1.2, paid, per-minute)
Android apps cannot tap normal call audio. Approach: user sets conditional call forwarding (busy/unanswered) to a virtual number;
a cloud voice-AI provider answers with the user's context and sends a summary. Must disclose it is AI; virtual-number KYC and
recording-consent rules to verify. Price per minute passed through to the user's credits.

## 7. Updates, models, and feedback loop (next build)
- **In-app update**: app checks the GitHub Release `bench-latest` of this (now public) repo via `GET /repos/anishghosh-art/bondhu-app/releases/tags/bench-latest` (the `/releases/latest` endpoint skips pre-releases and returns 404),
  downloads, installs via PackageInstaller. Android always asks the user to confirm once; no silent install for sideloaded apps.
  Requires a **permanent signing key** (CI debug keys differ per run and break updates) and an increasing versionCode (use the CI run number).
- **In-app model download** (needs INTERNET permission): resumable, shows size, checks free space, verifies hash. Verified 2026-11-19 on Hugging Face: `litert-community/Qwen3-0.6B` and `litert-community/gemma-4-E2B-it-litert-lm` are Apache-2.0 and NOT gated (direct download);
  `litert-community/Gemma3-1B-IT` is gated (Gemma licence + HF token), so avoid it for in-app download.
- **Automatic problem reports** (opt-in toggle at first run): every 1-2 hours the app batches new failure events and sends them to a free
  server endpoint that opens/updates GitHub issues (deduplicated; the app never holds a GitHub token). When a fix ships, the issue is closed.
  Events only, never content.
- Claude cannot reach the phone live: loop is Claude commits -> CI builds -> release -> user taps Update. Reading issues happens when the user asks
  (or a scheduled task, later).

## 8. Planned modules
`:core` (contracts, privacy gate/masking, router, thermal guard, kill switch, SQLite memory), `:device` (accessibility, notifications, SMS, voice),
`:app` (UI), later `:harness-link` (BYNQ Phone via the BYNQ Product Protocol).

## 9. Status table
| Item | Status |
|---|---|
| Bondhu Bench: device profile, speech check, CPU bench, failure log, share report | DONE (tested on 1 phone) |
| CI build + release of APK | DONE |
| Model bench (speed/RAM/heat), in-app model download, in-app update, permanent signing key | PLAN (next) |
| Auto problem reports -> GitHub issues | PLAN |
| Everything else in this document | PLAN |
