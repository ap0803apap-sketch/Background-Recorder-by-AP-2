# Background Recorder by AP

A powerful and discreet Android application designed for background video, audio, and photo capture. Built with modern Android development practices using Jetpack Compose and CameraX.

---

## 📸 Screenshots

<table>
  <tr>
    <td align="center">
      <img width="1080" height="2096" alt="Screenshot_20260331_113257_Background Recorder by AP" src="https://github.com/user-attachments/assets/28edf7f6-c5b7-4029-a120-98b897774321" />
    </td>
    <td align="center">
      <img width="1080" height="2096" alt="Screenshot_20260331_113302_Background Recorder by AP" src="https://github.com/user-attachments/assets/bd743092-cd39-4467-9d6c-21d9bff874b9" />
    </td>
      <td align="center">
      <img width="1080" height="2096" alt="Screenshot_20260331_113306_Background Recorder by AP" src="https://github.com/user-attachments/assets/1fda28ab-a804-4a30-9bac-42109a51af82" />
    </td>
  </tr>
</table>

## 🚀 Features

### 📸 Versatile Recording Modes

* **Video Recording**: Capture high-quality video in the background.
* **Audio Recording**: Discreetly record audio without keeping the app open.
* **Timed Photos**: Take photos at customizable intervals.

### 🕵️ Background Triggers

* **Quick Settings Tile**: Start/Stop recording directly from your notification panel.
* **Digital Assistant Integration**: Map the app as your default digital assistant to toggle recording by long-pressing the power or home button.
* **Silent Operation**: Triggers start recording immediately without opening the main UI.

### ⚙️ Advanced Camera Controls

* **Multi-Camera Support**: Switch between Primary, Secondary (Ultrawide), and Front cameras.
* **Manual & Auto Focus**: Toggle between focus modes for the perfect shot.
* **Digital Zoom**: Adjust zoom levels up to 10x.
* **Resolution & FPS**: Configure video resolution (up to 4K) and frame rates separately for front and back cameras.

### 🔒 Security & Privacy

* **Biometric Lock**: Protect your recordings with Fingerprint or Face Unlock.
* **Private Storage**: Recordings are stored internally by default.
* **Selective Export**: Easily save specific recordings to the public `Downloads` folder for gallery access.

### 🎨 Modern UI/UX

* **Material 3**: Clean and intuitive interface following the latest Android design guidelines.
* **Dynamic Color**: Supports Material You dynamic theming based on your wallpaper.
* **AMOLED Dark Mode**: True black theme for battery saving on OLED screens.

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose
* **Camera API**: CameraX
* **Architecture**: MVVM with Flow & StateFlow
* **Data Persistence**: Jetpack DataStore (Preferences)
* **Background Tasks**: Android Services (Foreground & Tile Services)

---

## 📋 How to Use

1. **Grant Permissions**: Ensure all necessary camera, microphone, and storage permissions are granted.
2. **Select Mode**: Choose between Video, Audio, or Photo from the home screen.
3. **Configure Settings**: Visit the Settings page to adjust resolutions, quality, and app theme.
4. **Background Shortcut**:

   * Go to **Device Settings > Default Apps > Digital Assistant** and select **Background Recorder**.
   * Long-press your power or home button to start/stop recording silently.

---

**Developer**: [AP](https://github.com/ap0803apap-sketch)
