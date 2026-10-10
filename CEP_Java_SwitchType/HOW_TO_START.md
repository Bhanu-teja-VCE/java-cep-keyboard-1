# 🚀 Getting Started & User Manual: SwitchType

Welcome to **SwitchType**! This guide walks you through:
1. **How to start the project from your directory**
2. **How to use the application and start typing with a single switch**

---

## 📋 Part 1: How to Start the Project in Your Directory

Your project is located at:
```
c:\Users\bhanu\desktop\java cep\bhanu-1
```

### Prerequisites
- **Java Runtime Environment (JRE) or JDK**: Java 8, 11, 17, or 21 (Verified: OpenJDK 21 is already installed on your machine).
- **MySQL Database**: **Optional!** The application automatically works 100% offline using local data files (`data/` folder). If MySQL is running, it connects automatically; if not, it never crashes.

---

### Method 1: The Fastest Way (Run Pre-Built JAR)
The project is already compiled into a self-contained executable JAR.

#### Using Windows Explorer:
1. Open File Explorer.
2. Navigate to:
   ```
   c:\Users\bhanu\desktop\java cep\bhanu-1\CEP_Java_SwitchType\app\target
   ```
3. **Double-click `SwitchType.jar`**. The app window will immediately open!

#### Using PowerShell / Terminal:
Open PowerShell in `c:\Users\bhanu\desktop\java cep\bhanu-1` and run:
```powershell
java -jar "CEP_Java_SwitchType\app\target\SwitchType.jar"
```

---

### Method 2: Run via IntelliJ IDEA
1. Open **IntelliJ IDEA**.
2. Click **File > Open** and select:
   ```
   c:\Users\bhanu\desktop\java cep\bhanu-1\CEP_Java_SwitchType\app\pom.xml
   ```
3. Select **Open as Project**.
4. In the Project Explorer on the left, navigate to:
   ```
   src/main/java > com.switchtype > Main.java
   ```
5. Right-click [`Main.java`](file:///c:/Users/bhanu/desktop/java%20cep/bhanu-1/CEP_Java_SwitchType/app/src/main/java/com/switchtype/Main.java) and select **Run 'Main.main()'**.

---

### Method 3: Run via Maven Command Line
From the `c:\Users\bhanu\desktop\java cep\bhanu-1\CEP_Java_SwitchType\app` directory, run:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" exec:java
```

---

## 🎮 Part 2: How to Use the Application

### 💡 Core Concept: What is Single-Switch Scanning?
Traditional keyboards require 10 fingers. **SwitchType is built for people who can only press ONE single button.**
- The application automatically moves a highlight across groups, rows, or keys at a fixed speed (e.g. every 450 ms).
- **Your switch is the `Spacebar` key** (or Enter key).
- You **do not touch the mouse** while typing; you simply tap the Spacebar when the item you want is highlighted!

---

### 🔤 Tutorial 1: Typing Your First Word (Row-Column Scan)

When the app opens, the default mode is **Row-Column Scanning**:

1. **Watch the Screen**: Notice the blue highlight moving down row by row:
   - Row 1: Prediction row (`the`, `i`, `please`, `to`)
   - Row 2: First letter row (`space`, `e`, `o`, `s`, `r`, `d`, `u`, `b`)
   - Row 3: Second letter row (`t`, `a`, `n`, `.`, `p`, `g`, `v`, `x`)
   - And so forth...
2. **Step 1 — Select the Row**:
   - Want to type the letter **`h`**? Look for the row containing `h`.
   - When the highlight lands on that row, **press `Spacebar` once**.
   - The row is now locked, and the highlight starts scanning individual keys from left to right!
3. **Step 2 — Select the Character**:
   - When the highlight reaches **`h`**, **press `Spacebar` again**.
   - The letter **`h`** appears in the text box!
   - The scanner resets to the top, ready for the next character.

---

### ⚡ Tutorial 2: Using the Word Prediction Row (Fast Typing!)

Above the letter keys, there are **4 blue prediction buttons**:
1. Type the first letter or two of a word (e.g., `h` then `e`).
2. Look at the top row: intelligent word suggestions appear (e.g. `hello`, `help`, `here`, `he`).
3. When the highlight is on the top **prediction row**, press `Spacebar`.
4. The highlight scans across the 4 words. Press `Spacebar` on `hello`.
5. The entire word `hello` is typed, followed by an automatic space!
   > 🚀 **Pro Tip:** This saves over 40% of switch presses!

---

### 🔄 Tutorial 3: Correcting Mistakes & Editing

On the keyboard grid, look at the edit row:
- **`delete`**: Acts like Backspace, removing the last character.
- **`undo word`**: Deletes the entire last typed word at once.
- **`pause`**: Freezes the scanner so the user can rest. Press Spacebar anytime to resume.

---

### 🎯 Tutorial 4: The Calibration Wizard (Tab 2)

Before a session, personalize the speed to your personal reaction time:
1. Click the **Calibrate** tab at the top.
2. Click **Start Calibration** (or press Spacebar).
3. A large box will change color at random intervals.
4. Whenever it changes color, tap **Spacebar**.
5. After 5 trials, the wizard calculates:
   - Your **Mean Reaction Time** (e.g., 420 ms)
   - Your **Standard Deviation / Jitter**
   - Recommended Dwell Speed & Clinical Profile (*Quick, Typical, Slow, Tremor, or Fatigue-prone*).
6. Click **Apply Recommended Settings**!

---

### 🏋️ Tutorial 5: Practice Mode

Want to test your typing speed in Words Per Minute (WPM)?
1. On the **Keyboard** tab, click **Start / stop practice**.
2. A prompt phrase will appear (e.g., *"the quick brown fox"*).
3. Type the phrase using the switch.
4. When finished, SwitchType calculates:
   - **Characters Per Minute (CPM)** and **Words Per Minute (WPM)**
   - **Error Rate %**
   - **Presses per character**

---

### ⚙️ Tutorial 6: Customizing Settings

Click **Settings...** in the top-left toolbar:
- **Scan Style**:
  - `Linear`: Checks every key one by one.
  - `Row-Column`: Fast row selection followed by key selection.
  - `Group-Binary`: Splits the keyboard in halves using a Huffman tree (\(O(\log N)\) steps).
- **Layout**:
  - `Alphabetical`: Standard A–Z.
  - `QWERTY`: Standard computer keyboard layout.
  - `Frequency`: Letters placed by frequency (E, T, A, O first) — fastest for row-column scan!
- **Dwell Interval**: Adjust how long each key stays highlighted (from 200 ms to 2000 ms).
- **Adaptive Speed**: Turn this ON! If you make 5 consecutive correct selections, the keyboard speeds up automatically. If you make an error, it backs off to give you more time.
- **High Contrast Theme**: Dark high-contrast background with larger buttons for visual impairments.

---

### 📊 Tutorial 7: History & Data Privacy (Tabs 3 & 4)
- **History Tab**: Displays interactive graphs showing your CPM and error rate over time.
- **Data & Privacy Tab**: View raw session data, export to CSV, or exercise GDPR data erasure with one click.

---

## 🛠️ Summary of Quick Commands

| Goal | Command |
|---|---|
| **Run the Application** | `java -jar CEP_Java_SwitchType/app/target/SwitchType.jar` |
| **Run Unit Tests (48 tests)** | `mvn test` (from `CEP_Java_SwitchType/app`) |
| **Run Experiment Simulations** | `java -cp CEP_Java_SwitchType/app/target/SwitchType.jar com.switchtype.sim.Experiments 8` |
| **View Technical Blog** | Open [`blog.html`](file:///c:/Users/bhanu/desktop/java%20cep/bhanu-1/blog.html) or [`index.html`](file:///c:/Users/bhanu/desktop/java%20cep/bhanu-1/index.html) in your browser |
