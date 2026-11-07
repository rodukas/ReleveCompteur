# MISSION
Build a native Android application using Jetpack Compose and Kotlin that acts as an automated utility and vehicle meter tracking hub. The app captures physical meter readouts using on-device vision, updates a centralized Google Sheet stored on Google Drive via Google Identity OAuth, renders multi-year comparative consumption trends, and calculates dynamic cost projections.

---

## 1. ARCHITECTURE & TECH STACK
- **Language / UI Toolkit:** Kotlin, Jetpack Compose, Material 3 Design.
- **Architecture Pattern:** Clean Architecture with MVVM, StateFlow, Repository pattern, and Kotlin Coroutines.
- **Vision / OCR Pipeline:** 
  - CameraX for capture.
  - ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition`) or embedded Gemini Nano / Vision LLM for OCR and meter identification.
  - Template matching / visual classification using a bundled reference asset library (`/assets/meter_references/`).
- **Google Services Integration:**
  - Google Sign-In (`com.google.android.gms:play-services-auth`) with OAuth 2.0 scopes: `https://www.googleapis.com/auth/spreadsheets` and `https://www.googleapis.com/auth/drive.file`.
  - Google Sheets API v4 (`com.google.apis:google-api-services-sheets`).
- **Local Persistence & Cache:** DataStore / Room for user settings (e.g., target Spreadsheet ID, unit tariff rates, meter offset values).
- **Visualization:** Vico Charting Library or MPAndroidChart wrapped in Compose for multi-series trend lines.

---

## 2. DOMAIN MODEL & METER TAXONOMY

The application must track exactly 8 distinct meters:

| Key Identifier | Display Label | Category | Unit | Target / Logic |
| :--- | :--- | :--- | :--- | :--- |
| `TWINGO` | `Twingo` | Vehicle Odometer | km | Renault vehicle mileage |
| `TIGUAN` | `Tiguan` | Vehicle Odometer | km | Volkswagen vehicle mileage |
| `MX5` | `mx5` | Vehicle Odometer | km | Mazda vehicle mileage |
| `EDF` | `edf` | Main Grid | kWh | Whole-house grid electricity (Linky / standard meter) |
| `CLIM_T00` | `clim (T00)` | Sub-metering | kWh | Air conditioning electricity |
| `EAU_GEN` | `Eau generale` | Main Water | m³ | Total house water consumption |
| `EAU_JARDIN` | `Eau jardin` | Water Sub-meter | m³ | Garden + swimming pool water |
| `ARROSAGE` | `Arrosage` | Water Sub-meter | m³ | Dedicated garden plant watering |

### Meter Replacement Offsets (Normalized Values)
Some physical meters have been replaced over time and reset to `0`. 
- **Raw Value:** Digits read from the meter dial/LCD.
- **Normalized Value:** `Normalized = Raw Value + Meter_Cumulative_Offset`.
- Store an editable configuration map `meter_offsets.json` or a Preferences screen where the user can define the cumulative offset constant for each meter key. Initial value of the offset should be obtain through the analysis of the Google Sheet file. A sample sheet csv file is included in the project as `GoogleSheetSample.csv`.

---

## 3. GOOGLE DRIVE & GOOGLE SHEET SCHEMA

### Authentication
- Provide a setup screen allowing the user to sign in with their Google account.
- Allow the user to select or input the target Spreadsheet ID stored in their personal Google Drive.
- Verify read/write permissions prior to running write jobs.

### Google Sheet Columns
When recording a new capture, append a single row to the sheet containing exactly 5 columns in this order:

1. **Date:** Format `DD/MM/YY`. Example: `03Aug26`
2. **Meter Type:** Exact string label matching taxonomy (`Twingo`, `Tiguan`, `mx5`, `clim (T00)`, `edf`, `Eau generale`, `Eau jardin`, `Arrosage`).
3. **Raw Value:** Numeric decimal parsed directly from OCR.
4. **Normalized Value:** Numeric decimal calculated as `Raw Value + Offset`.
5. **Comment:** Optional free-form text entered during verification (e.g., "Mise en route chauffage", "Remplissage piscine").

---

## 4. CORE ACTIVITIES & USER FLOWS

Implement a bottom navigation bar or top tabbed interface switching across 3 primary screens:

### Activity 1: Meter Capture & Optical Ingestion (Camera & OCR)
1. **Camera Interface:** Custom CameraX viewfinder with an alignment reticle and flash toggle.
2. **Recognition Pipeline:**
   - **Meter Classification:** Analyze the captured frame against reference images located in `/assets/meter_references/` (combining aspect ratio, LCD/mechanical counter style, bounding box features, and surrounding text labels).
   - **Digit Extraction (OCR):** Identify the primary numerical display, strip extraneous units (`km`, `kWh`, `m³`), handle decimal points / rotating dials, and extract the rounded value to the nearest unit.
3. **Confirmation Modal / Sheet:**
   - Display a cropped preview of the meter readout.
   - Dropdown pre-filled with the auto-detected meter type (editable if classification missed).
   - Numeric input field pre-filled with the extracted raw value (editable if OCR misread).
   - Display of the resulting calculated **Normalized Value** based on configured offsets.
   - Text input field for **Comment / Remark**. This field is optional.
   - Action buttons: `Annuler`, `Reprendre`, and `Valider et Enregistrer`.
4. **Append Operation:** On confirmation, asynchronously push the 5-element row to Google Sheets, show an immediate sync toast/snackbar, and cache locally.

### Activity 2: Multi-Year Trend Analysis & Leak Detection
1. **Data Ingestion:** Fetch historical rows from the active Google Sheet, group data points by `Meter Type` and partition by calendar year.
2. **Comparative Visualization:**
   - Selector to switch between the 8 meters.
   - Time-series line chart plotting **Normalized Value** (or computed delta consumption: daily/monthly increments) across the X-axis (Day of Year: Jan 1 to Dec 31).
   - Overlay curves: Current Year (bold primary accent) vs. Year -1, Year -2, and Year -3 (distinct muted color lines).
3. **Anomaly & Leak Heuristics:**
   - For water meters (`Eau generale`, `Eau jardin`, `Arrosage`): Calculate daily consumption velocity ($\Delta \text{Value} / \Delta \text{Days}$).
   - Flag sudden slopes or continuous baseline consumption without zero-intervals as potential leaks, showing a prominent warning banner. THere could be several weeks or months between two measures for a given meter. Make sure the heuristics will support long measure intervals.

### Activity 3: Cost Projection & Tariff Simulator
1. **Configurable Unit Costs:**
   - Water rate: `€ / m³`
   - Electricity rate (Base, or Peak/Off-peak average): `€ / kWh`
   - Optional fuel rate (for vehicle km estimation): `€ / 100 km` composed of the combination of sub units 'kWh for 100 km' + `€ / kWh` obtained from the Electricity rate field for an electric car (Twingo)and `price per litre` and ` litre per 100 km` for a thermal car (mx5 and Tiguan). Each car should accept different values for `price per litre` and ` litre per 100 km`.
2. **Timeframe Selector:**
   - Chips: `Dernier mois` (30 days), `Derniers 3 mois` (90 days), `Derniers 6 mois` (180 days), `Dernière année` (365 days).
3. **Projection Engine:**
   - Calculate delta consumption for each meter over the selected lookback window:
     $$\Delta \text{Consumption} = \text{Normalized}_{\text{end}} - \text{Normalized}_{\text{start}}$$
   - Extrapolate annualized or monthly spending:
     $$\text{Cost} = \Delta \text{Consumption} \times \text{Tariff Rate}$$
   - Render a breakdown card per category (Electricity, Water, Vehicles) and an aggregate estimated total cost.

---

## 5. REFERENCE ASSET INTEGRATION FOR OCR CLASSIFIER
Structure the application to accept initial reference image fixtures under:
`app/src/main/assets/meter_references/`
- `twingo_ref.jpg`
- `tiguan_ref.jpg`
- `mx5_ref.jpg`
- `clim_ref.jpg`
- `edf_ref.jpg`
- `eau_generale_ref.jpg`
- `eau_jardin_ref.jpg`
- `arrosage_ref.jpg`

Include an explicit matching interface (`MeterClassifier`) capable of switching between ML Kit visual feature similarity and prompt-based multimodal inference if an on-device local model is used.

---

## 6. DELIVERABLES EXPECTED
1. Complete Android Studio project scaffolding with Gradle build scripts (`build.gradle.kts`) and AndroidManifest permissions (`CAMERA`, `INTERNET`, `GET_ACCOUNTS`).
2. Google Sheets API authentication wrapper and background synchronization workers.
3. CameraX capture view with OCR bounding box overlay and confirmation dialog.
4. Clean, modular Jetpack Compose UI with French localization for all meter labels and units.