# 🐝 Edu Hive

**Edu Hive** is a sophisticated, AI-powered study assistant designed for university students. It leverages cutting-edge **on-device Large Language Models (LLMs)** to transform static study materials—like PDFs, lecture slides, and personal notes—into interactive learning tools including flashcards, quizzes, and structured concept maps.

Unlike traditional AI apps, Edu Hive performs inference locally on your Android device using MediaPipe. This ensures total privacy for your academic data, reduces latency, and provides offline availability without the need for expensive cloud API subscriptions.

---

## ✨ Core Features

### 🧠 Intelligent Study Generation
*   **Concept Extraction**: Automatically identifies key terminology, definitions, and core logic from dense academic texts.
*   **Smart Flashcards**: Generates high-quality Spaced Repetition (SRS) ready flashcards using "Cognitive Angle" prompting to avoid generic questions.
*   **Reasoning-Based Quizzes**: Creates Multiple Choice (MCQ) and True/False questions that test deep understanding and application rather than simple recall.
*   **Summary Logic**: Condenses long documents into digestible summaries focusing on actionable study points.

### 📄 Robust Document Processing
*   **PDF Intelligence**: Integrated PDF text extraction with a custom `DocumentCleaner` pipeline to strip headers, footers, and formatting noise.
*   **Recovery System**: A hardened processing pipeline that handles native engine interruptions, allowing the app to resume document analysis even if a specific page causes an LLM timeout.
*   **Batch Processing**: Efficiently handles multiple concepts simultaneously to optimize hardware usage and reduce wait times.

### 🛡️ Privacy & Performance
*   **On-Device Inference**: Powered by the MediaPipe GenAI SDK. Your documents never leave your device.
*   **Adaptive Tuning**: Dynamic prompt mutation based on task complexity and the specific model loaded (Gemma 2, Qwen, or SmolLM).
*   **Resource Aware**: Intelligent memory management that scales AI tasks based on available system RAM and device thermal state.

---

## 📸 Screenshots

| Study Dashboard | Concept Extraction | Flashcard Generation | Quiz Mode |
| :---: | :---: | :---: | :---: |
| ![Placeholder](https://via.placeholder.com/200x400?text=Dashboard) | ![Placeholder](https://via.placeholder.com/200x400?text=Extraction) | ![Placeholder](https://via.placeholder.com/200x400?text=Flashcards) | ![Placeholder](https://via.placeholder.com/200x400?text=Quiz) |

---

## 🚀 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/) (100%)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 (Adaptive Layouts)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous Flow**: Coroutines & Kotlin Flow
- **AI Engine**: [MediaPipe GenAI SDK](https://developers.google.com/mediapipe/solutions/genai/llm_inference)
- **Local Storage**: [Room Database](https://developer.android.com/training/data-storage/room)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **PDF Processing**: PDFBox-Android
- **HTML Parsing**: Jsoup

---

## 🏗️ Architecture Overview

The project is structured following Clean Architecture to ensure the AI logic is decoupled from the UI and easily testable:

*   **`data/source/ai`**: The core AI package.
    *   `AIModelManager`: Manages the lifecycle of `.task` model files, hardware acceleration (GPU/CPU), and JNI synchronization.
    *   `AIDataSource`: Orchestrates the flow between raw text extraction and structured AI data.
    *   `LLMPromptTemplates`: A dedicated "Prompt Engineering" layer that structures instructions for high-quality LLM output.
*   **`data/source/file`**: Handles document ingestion, OCR, and cleaning.
*   **`domain`**: Contains the business logic, models, and UseCases (e.g., `GetNextReviewItemsUseCase`).
*   **`ui`**: Built entirely with Jetpack Compose, featuring an adaptive layout for phones, tablets, and foldable devices.

---

## 📦 Folder Structure

```text
com.dibe.eduhive/
├── data/
│   ├── local/            # Room DAOs, Entities, and TypeConverters
│   ├── repository/       # Implementation of Domain Repositories
│   └── source/
│       ├── ai/           # LLM logic, Prompting, and Model Management
│       ├── local/        # Local DataSources (Flashcard, Quiz, etc.)
│       └── file/         # PDF and Document cleaning logic
├── domain/
│   ├── model/            # Plain Kotlin business models
│   ├── repository/       # Repository interfaces
│   └── usecase/          # Individual domain logic units (Interactors)
└── ui/
    ├── components/       # Reusable Compose UI elements (cards, buttons)
    ├── navigation/       # Compose Navigation graphs and routes
    ├── screens/          # Screen-level Composables and ViewModels
    └── theme/            # Material 3 Design System implementation
```

---

## 🛠️ Installation & Setup

### Requirements
*   **Android Version**: API 24 (Nougat) or higher.
*   **RAM Expectations**: 
    *   *Minimum*: 4GB (Supports SmolLM-135M / Qwen-0.5B)
    *   *Recommended*: 8GB+ (Supports Gemma-2B / Gemma-3-1B)
*   **Storage**: ~1GB to 2GB free space for model storage.

### Setup Steps
1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/EduHive.git
    ```
2.  Open the project in **Android Studio (Ladybug 2024.2.1 or newer)**.
3.  Ensure the latest Android SDK and Build Tools are installed.
4.  Sync Gradle and wait for dependencies to download.
5.  **Important**: Run the app on a physical device. Emulators often lack the necessary GPU support for MediaPipe's native inference.

---

## 📖 Usage Guide

1.  **Importing Content**: Tap the "Add" button to upload a PDF file or paste text directly into a new Hive.
2.  **Extraction**: The app will automatically run `ConceptExtraction` to identify the most relevant study topics.
3.  **Generating Materials**: Select a concept and tap "Generate Flashcards" or "Generate Quiz". You can customize the number of items and the "Cognitive Angle" of the questions.
4.  **Studying**: Use the "Review" tab to go through your flashcards using the built-in spaced repetition algorithm.
5.  **Refinement**: If the AI output isn't perfect, use the "Refine" option. The app will use a second-pass validation prompt to improve the clarity and accuracy of the cards.

---

## 🤝 Contribution

Contributions make the open-source community an amazing place to learn and create.
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---
*Developed with ❤️ for students, by developers who know the struggle.*
