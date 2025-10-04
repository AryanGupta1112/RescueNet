<!-- README.md for RescueNet -->
<!-- Developed by Aryan Gupta | Patent Filed 2025 -->

<h1 align="center">🚨 RescueNet: Disaster Relief Volunteer Coordination Tool</h1>
<p align="center">
  <b>An Offline-First Mobile System for Disaster Zone Coordination, Mapping & Survivor Logging</b><br/>
  Built with ❤️ using <b>Android Studio</b>, <b>Leaflet.js</b>, <b>OpenStreetMap</b>, and <b>BLE Sync</b>  
  <br/><br/>
  <img src="https://img.shields.io/badge/Status-Patent%20Filed-orange?style=for-the-badge&logo=google-scholar&logoColor=white"/>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Technology-Offline%20Sync%20%26%20Mapping-blue?style=for-the-badge&logo=gpsdot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Open%20Source-Yes-brightgreen?style=for-the-badge&logo=opensourceinitiative&logoColor=white"/>
</p>

---

## 🧭 Overview
**RescueNet** is an **AI-assisted, offline-first coordination tool** designed for **disaster response teams**, enabling efficient **mapping, survivor logging, and volunteer task management** in environments with poor or no internet connectivity.  
Developed using open-source technologies, it provides **real-time local synchronization** through **Bluetooth Low Energy (BLE)** and **QR-based data transfer**, ensuring continuity of operations even in remote disaster zones.

---

## 🚀 Features
- 🛰️ **Offline-First Architecture** – Operates fully without internet using local GPS and offline map tiles.  
- 🔄 **BLE & QR Synchronization** – Syncs survivor data, volunteer lists, and resources across nearby devices.  
- 🗺️ **Offline Mapping Interface** – Built on Leaflet.js and OpenStreetMap for precise local navigation.  
- 🧾 **Survivor & Resource Logging** – Stores survivor details, locations, and supply points in local JSON cache.  
- 📡 **Hazard-Aware Navigation** – Marks blocked roads, safe zones, and relief centers dynamically.  
- 👥 **Volunteer Coordination Module** – Assigns tasks, tracks locations, and facilitates decentralized updates.  
- ⚡ **Open Source Stack** – No paid APIs; fully open-source, lightweight, and deployable anywhere.  

---

## 🧩 Methodology
- **Requirement Identification:** Studied failures in major disasters (e.g., Kerala Floods 2018, Odisha Cyclone 2019).  
- **Design Framework:** Created decentralized, offline-first architecture using open mapping and BLE mesh sync.  
- **Offline Maps:** Preloaded MBTiles for detailed local maps with multiple basemap layers.  
- **Data Logging:** Survivor entries stored with geotags in structured JSON, updated locally.  
- **Synchronization Mechanism:** BLE for short-range peer sync; QR-based fallback for manual exchange.  
- **Routing:** Integrated hazard-aware route calculation and navigation prompts.  
- **Prototype Implementation:** Android (Kotlin/Java) frontend with Leaflet.js WebView interface.  

---

## 📸 Screenshots
<p align="center">
  <img src="app/src/Screenshots/1.jpg" width="200"/>
  <img src="app/src/Screenshots/2.jpg" width="200"/>
  <img src="app/src/Screenshots/4.jpg" width="200"/>
</p>
<p align="center">
  <img src="app/src/Screenshots/5.jpg" width="200"/>
  <img src="app/src/Screenshots/12.jpg" width="200"/>
  <img src="app/src/Screenshots/16.jpg" width="200"/>
</p>
<p align="center">
  <img src="app/src/Screenshots/17.jpg" width="200"/>
  <img src="app/src/Screenshots/18.jpg" width="200"/>
  <img src="app/src/Screenshots/19.jpg" width="200"/>
</p>
<p align="center">
  <i>*(Additional screenshots available in the `/Screenshots` folder.)*</i>
</p>

---

## 🧠 Tech Stack
| Category | Technologies Used |
|-----------|------------------|
| **Frontend** | Java, Kotlin (Android Studio) |
| **Mapping** | Leaflet.js, OpenStreetMap, MBTiles |
| **Storage** | Local JSON Cache, SharedPreferences/SQLite |
| **Syncing** | Bluetooth Low Energy (BLE), QR Code |
| **UI/UX** | Material Design, Responsive Mobile Layout |
| **Architecture** | Decentralized, Offline-First System |

---

## 🏅 Highlights
✅ Offline synchronization tested successfully via BLE & QR  
✅ Fully functional mapping and survivor logging interface  
✅ Operates in disaster zones with no connectivity  
✅ Lightweight and energy-efficient for field devices  
✅ Built entirely on open-source frameworks (no external dependencies)

---

## 📚 Academic Origin
Developed as part of a **research-driven innovation initiative** at  
**Manipal University Jaipur, Department of Computer Science & Engineering**  

---

<p align="center">
  <b>© 2025 RescueNet | Offline-First Disaster Response System</b><br/>
  <i>“Connecting responders when the world is disconnected.”</i>
</p>
