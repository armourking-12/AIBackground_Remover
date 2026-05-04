# AIBackground_Remover

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF)
![Android](https://img.shields.io/badge/Android-34+-3DDC84)
![ONNX Runtime](https://img.shields.io/badge/ONNX-Runtime-blue)

**Fast, Private & On-Device AI Background Remover for Android**

A lightweight Android application that removes image backgrounds using **on-device AI** powered by ONNX Runtime and the RMBG model. No internet required after initial setup.

---

### ✨ Features

- ⚡ **High Performance On-Device Inference** using ONNX Runtime + NNAPI (GPU/NPU acceleration)
- 🔋 **Smart Power Management** – AI model loads on demand and auto-releases memory
- 🖼️ **Handles Large Images** – Smart downscaling support for 50MP+ photos
- 📜 **Processing History** – View and manage previously processed images
- 🔄 **Batch Processing** – Remove backgrounds from multiple images at once
- 🎨 **Modern UI** – Built with Material 3 design
- 💾 **Direct Gallery Save** – Saves transparent PNGs using MediaStore

---

### 🛠 Tech Stack

- **Language**: Kotlin (100%)
- **AI Framework**: ONNX Runtime Android
- **Model**: RMBG (quantized for mobile)
- **UI**: Jetpack Compose + Material 3
- **Image Processing**: Bitmap + custom masking
- **Architecture**: Clean Architecture with Coroutines

---

### 📱 Screenshots

### SplashScreen
<img width="720" height="1600" alt="image" src="https://github.com/user-attachments/assets/6971bf0c-15cd-48c0-bd9c-10facfab3480" />

### ModelDownloading

<img width="720" height="1600" alt="image" src="https://github.com/user-attachments/assets/163af008-432c-46e0-8535-a4275255afeb" />
<img width="720" height="1600" alt="image" src="https://github.com/user-attachments/assets/ba1c165b-abc5-4283-8173-be0c89f6c795" />

### SystemIsReadyNow

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/6218effd-022f-4451-8efd-ffaa92180e1b" />

### BatchSystem

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/15afb7d2-832c-46e7-a0c6-8934fc8a08ba" />



---

### 🚀 Getting Started

#### Prerequisites

- Android Studio (latest version)
- Android device or emulator (API 24+)
- `rmbg_model.onnx` model file in `assets/` folder

#### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/armourking-12/AIBackground_Remover.git
