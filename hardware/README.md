# Unipay — ESP32 NFC POS Terminal

Hardware-based Point-of-Sale terminal built with an **ESP32 microcontroller** that connects to the Unipay backend API for processing contactless NFC card payments in real-time.

## Components

| Component | Model | Purpose |
|---|---|---|
| Microcontroller | ESP32 DevKit V1 | WiFi connectivity + processing |
| NFC Reader | MFRC522 (SPI) | Read student NFC card UIDs |
| Display | SH1106 OLED 128×64 (I2C) | Show payment status, amounts |
| Input | 4×4 Matrix Keypad | Enter payment amounts |
| Buzzer | Passive Buzzer | Audio feedback for transactions |
| LEDs | Green + Red LEDs | Visual payment status indicators |

## Pin Wiring

```
ESP32 Pin   →   Component
─────────────────────────────
GPIO 4      →   MFRC522 RST
GPIO 5      →   MFRC522 SDA/SS
GPIO 18     →   MFRC522 SCK  (default SPI)
GPIO 23     →   MFRC522 MOSI (default SPI)
GPIO 19     →   MFRC522 MISO (default SPI)
GPIO 2      →   Buzzer
GPIO 16     →   Green LED
GPIO 17     →   Red LED
GPIO 21     →   SH1106 SDA (default I2C)
GPIO 22     →   SH1106 SCL (default I2C)

Keypad Rows:  GPIO 13, 14, 27, 26
Keypad Cols:  GPIO 25, 33, 32, 15
```

## Terminal Flow

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│  BOOT       │────▶│  IDLE        │────▶│  WAITING CARD │────▶│  PROCESSING  │
│  WiFi+Auth  │     │  Enter ₹     │     │  Tap NFC      │     │  API Call    │
└─────────────┘     └──────────────┘     └───────────────┘     └──────┬───────┘
                           ▲                                          │
                           │          ┌──────────┐  ┌─────────┐      │
                           └──────────│ SUCCESS  │◀─│ VERIFY  │◀─────┘
                                      │ ✅ Green │  │ Server  │
                                      └──────────┘  └─────────┘
```

## Setup

1. Install [Arduino IDE](https://www.arduino.cc/en/software) with ESP32 board support
2. Install required libraries via Library Manager:
   - `MFRC522` by GithubCommunity
   - `Adafruit GFX Library`
   - `Adafruit SH110X`
   - `Keypad` by Mark Stanley
   - `ArduinoJson` by Benoit Blanchon
3. Open `esp32_pos_terminal.ino`
4. Update the configuration section at the top of the file:
   ```cpp
   const char*  ssid              = "YOUR_WIFI_SSID";
   const char*  password          = "YOUR_WIFI_PASSWORD";
   const String BASE_URL          = "http://your-backend-url:8080";
   const String MERCHANT_EMAIL    = "merchant@example.com";
   const String MERCHANT_PASSWORD = "YourMerchantPassword";
   ```
5. Select `ESP32 Dev Module` as the board and upload

## Keypad Controls

| Key | Action |
|---|---|
| `0-9` | Enter payment amount digits |
| `*` | Backspace (delete last digit) |
| `D` | Confirm amount → wait for NFC tap |
| `A` | Cancel current transaction |
