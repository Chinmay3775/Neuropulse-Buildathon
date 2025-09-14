# Neuropulse

> **Privacy-first, on-device detector for "droom scrolling"** — predicting imminent dopamine-driven, late-night, mindless scrolling and nudging users with friendly, actionable interventions.

---

## Table of contents

* [About](#about)
* [Why Neuropulse](#why-neuropulse)
* [Key features](#key-features)
* [Architecture overview](#architecture-overview)
* [Technical stack](#technical-stack)
* [Project structure](#project-structure)
* [Local setup (Android)](#local-setup-android)

  * [Prerequisites](#prerequisites)
  * [Build & run](#build--run)
  * [Where to place the model](#where-to-place-the-model)
* [Database & schema](#database--schema)
* [Debug & data export](#debug--data-export)
* [ML pipeline (training → TFLite)](#ml-pipeline-training---tflite)
* [OpenAI integration (optional & privacy)](#openai-integration-optional--privacy)
* [Privacy & security](#privacy--security)
* [Testing & validation](#testing--validation)
* [Scaling & roadmap](#scaling--roadmap)
* [Contributing](#contributing)
* [License & credits](#license--credits)

---

## About

Neuropulse is an Android-first research & product prototype that monitors app usage sessions, learns personal behavior patterns, and predicts when a user is likely to enter a **droom scrolling** state (mindless, dopamine-driven scrolling — especially late at night). The app focuses on privacy: by default all sensing and prediction run **on-device** using TensorFlow Lite and session data is stored locally in Room.

This repository contains the Android app, Room database entities/DAOs, sensor logic (UsageStatsManager / AccessibilityService), and example integration points for an ML model and optional cloud features.

---

## Why Neuropulse

Many users underestimate how much time they lose to mindless scrolling. Existing tools show stats or set limits but lack predictive, personalized interventions. Neuropulse bridges that gap by learning an individual's patterns and nudging them *before* hours are lost — while keeping their data private.

---

## Key features

* Session-based tracking (start, end, duration) per foreground app
* Rich behavioral indicators (time buckets, late-night flag, binge flag, session count, gap-between-sessions, previous app)
* Rule-based alerts for early testing and immediate value
* On-device TensorFlow Lite inference for predicting droom scrolling risk
* Local Room DB for temporary storage and developer debug view
* Optional OpenAI-based human-readable explanations (anonymized, opt-in)

---

## Architecture overview

High level components:

1. **Android client** (Java)

   * Background service to collect usage sessions
   * Session lifecycle manager (SessionTracker)
   * Local Room DB (SessionEntity / SessionDao)
   * DebugActivity to inspect stored sessions
   * TensorFlow Lite interpreter for local inference
   * Notification / UI for nudges

2. **ML pipeline (offline / dev)**

   * Feature engineering & training (Python + TensorFlow)
   * Conversion to `.tflite`
   * Optional: local fine-tuning with TF Lite, federated updates

3. **Optional Cloud (opt-in)**

   * Aggregated, anonymized metrics/embeddings
   * OpenAI calls for natural language explanations (only anonymized features)

---

## Technical stack

* Android (Java)
* UsageStatsManager API / AccessibilityService (optional)
* Room (SQLite) for local persistence
* TensorFlow Lite for on-device inference
* Python (TensorFlow, scikit-learn) for offline training
* (Optional) OpenAI APIs for natural language explanations & embeddings

---

## Project structure

```
Neuropulse-MegaProject/
├─ app/                     # Android app module
│  ├─ src/main/java/com/neuropulse/
│  │  ├─ SessionTracker.java
│  │  ├─ SessionEntity.java
│  │  ├─ SessionDao.java
│  │  ├─ AppDatabase.java
│  │  ├─ DebugActivity.java
│  │  └─ ...
│  ├─ assets/
│  │  └─ droom_model.tflite  # place model here (see below)
│  └─ AndroidManifest.xml
├─ ml/                      # training code, dataset templates, convert scripts
│  ├─ train.py
│  ├─ convert_to_tflite.py
│  └─ sample_dataset.csv
├─ docs/
│  └─ design.md
└─ README.md
```

---

## Local setup (Android)

### Prerequisites

* Android Studio
* Java 11+ (or Android-project-compatible JDK)
* Android SDK (API level compatible with `UsageStatsManager` and your target devices)
* Device/emulator with `Usage Access` enabled for the app (Settings → Security → Usage Access)

### Build & run

1. Clone the repo:

   ```bash
   git clone https://github.com/Chinmay3775/Neuropulse-MegaProject.git
   cd Neuropulse-MegaProject
   ```
2. Open the project in Android Studio.
3. If you have a `.tflite` model, place it in `app/src/main/assets/droom_model.tflite`.
4. Build and install on a device/emulator.
5. Grant the app **Usage Access** permission and any other requested accessibility permissions if used.

### Where to place the model

Put `droom_model.tflite` in `app/src/main/assets/`. The app's `TFLiteHelper` (or similar) should load it via assets mapping. Example snippet for loading:

```java
AssetFileDescriptor fileDescriptor = context.getAssets().openFd("droom_model.tflite");
FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
FileChannel fileChannel = inputStream.getChannel();
long startOffset = fileDescriptor.getStartOffset();
long declaredLength = fileDescriptor.getDeclaredLength();
MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
Interpreter tflite = new Interpreter(modelBuffer);
```

---

## Database & schema

The app uses Room. Typical `SessionEntity` fields:

* `id` (PK)
* `packageName` (string)
* `appName` (string)
* `startTimestamp` (long)
* `endTimestamp` (long)
* `durationSec` (int)
* `timeBucket` (string) — e.g., "23:00-23:30"
* `dayOfWeek` (int)
* `sessionsToday` (int)
* `gapSinceLastSec` (int)
* `previousApp` (string)
* `lateNightFlag` (boolean)
* `bingeFlag` (boolean)
* `createdAt` (long)

Example Room entity (simplified):

```java
@Entity(tableName = "sessions")
public class SessionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String packageName;
    public String appName;
    public long startTimestamp;
    public long endTimestamp;
    public int durationSec;

    // flags & meta
    public String timeBucket;
    public boolean lateNightFlag;
    public boolean bingeFlag;
    public String previousApp;
}
```

---

## Debug & data export

* `DebugActivity` shows a list of recent session records (App | Start | End | Duration | Flags).
* Add an **Export** button to save anonymized CSVs to `Downloads/` for offline model training (`sample_dataset.csv` format provided under `/ml`).

CSV columns example:

```
package_name,app_name,start_ts,end_ts,duration_sec,time_bucket,day_of_week,sessions_today,gap_sec,previous_app,late_night,binge
```

---

## ML pipeline (training → TFLite)

1. Collect anonymized CSVs from pilot users (or simulate data for prototyping).
2. Feature engineering: encode categorical fields (app → category or id), normalize durations, bucket times.
3. Train a binary classifier (normal vs droom) using TensorFlow/Keras. Suggested models: Dense network; consider simple RNN/LSTM if you want sequential patterns.
4. Evaluate & tune on validation sets.
5. Convert trained model to TFLite using `tf.lite.TFLiteConverter` and place `.tflite` in the app assets.

Sample conversion script in `ml/convert_to_tflite.py`.

**On-device personalization (optional)**: keep the model small and support fine-tuning only of last layers using TF Lite Model Personalization APIs (experimental).

---

## OpenAI integration (optional & privacy)

If you enable cloud-enhanced messaging, send only **very small anonymized feature summaries** (no app text, no screenshots, no raw logs) when a high-risk event occurs. Example payload:

```json
{
  "app_category":"Social",
  "time_bucket":"23:00-23:30",
  "duration_min":32,
  "sessions_last_hour":3,
  "late_night":true
}
```

Use Chat Completion to transform this into a concise, empathetic explanation + one micro-action. Always run moderation on generated content and allow user opt-in/opt-out for cloud features.

---

## Privacy & security

* Default behavior: **local-only**. No raw logs leave the device by default.
* Database can be encrypted (SQLCipher) in production builds.
* Provide clear onboarding explaining what is collected and why.
* Optional uploads must be explicit, minimal, and reversible (user can delete data or opt out).

---

## Testing & validation

* Validate session detection accuracy across Android versions and device vendors.
* Measure battery impact and optimize (batch writes, adaptive sampling).
* Pilot with small user group to collect labeled data for model training.
* Track KPIs: model precision/recall, alert acceptance rate, reduction in average session length.

---

## Scaling & roadmap

* Expand to iOS (ScreenTime API) and wearables for richer signals.
* Implement federated-style aggregation of model updates to improve base models while preserving privacy.
* Add enterprise features for corporate well-being and research partnerships for behavior studies.

---

## Contributing

Contributions are welcome. If you want to help:

1. Fork the repo
2. Create a feature branch
3. Open a PR with tests and description

Please follow the code style and include a short description of data/feature additions.

---

## License & credits

This project is provided as a research & prototype reference. Include your preferred license (e.g., MIT) in `LICENSE`.

---

## Contact

Chinmay / Chintu — add your contact email or GitHub profile for questions and collaboration.

---

*Thank you for using Neuropulse. Build responsibly and with privacy-first principles.*
