#include <SPI.h>
#include <MFRC522.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SH110X.h>
#include <Keypad.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

/*
 * =========================================
 *  UNIPAY POS v2.0  —  ESP32
 * =========================================
 *  Hardware NFC Point-of-Sale Terminal
 *  Connects to the Unipay backend API to
 *  process contactless card payments.
 *
 *  LED WIRING:
 *  SCREEN LAYOUT (128×64 SH1106):
 *  Row  0–13 : Header bar (inverted) — "UniPay | STATE"
 *  Row 14–50 : Content area
 *  Row 51–63 : Footer bar — key hints
 * =========================================
 */

/* ---- CONFIG (Update these with your own values) ---- */
const char*  ssid              = "YOUR_WIFI_SSID";
const char*  password          = "YOUR_WIFI_PASSWORD";
const String BASE_URL          = "http://localhost:8080";       // Your Unipay backend URL
const String MERCHANT_EMAIL    = "merchant@example.com";        // Your merchant login email
const String MERCHANT_PASSWORD = "YourMerchantPassword";        // Your merchant login password

/* ---- PINS ---- */
#define RST_PIN     4
#define SS_PIN      5
#define BUZZER_PIN  2
#define GREEN_LED   16
#define RED_LED     17

/* ---- OLED ---- */
#define SCREEN_WIDTH   128
#define SCREEN_HEIGHT   64
#define OLED_RESET      -1
#define SCREEN_ADDRESS 0x3C
Adafruit_SH1106G display = Adafruit_SH1106G(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

/* ---- RFID ---- */
MFRC522 rfid(SS_PIN, RST_PIN);

/* ---- KEYPAD ---- */
const byte ROWS = 4, COLS = 4;
char keys[ROWS][COLS] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
byte rowPins[ROWS] = {13, 14, 27, 26};
byte colPins[COLS] = {25, 33, 32, 15};
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

/* ---- STATE ---- */
enum TerminalState { STATE_IDLE, STATE_WAITING_CARD, STATE_PROCESSING };
TerminalState currentState = STATE_IDLE;
String paymentAmount = "";
String jwtToken      = "";
long   merchantId    = -1;

/* ---- LAYOUT CONSTANTS ----
   Adafruit GFX font: size 1 = 6×8px, size 2 = 12×16px, size 3 = 18×24px */
#define HEADER_H    14
#define FOOTER_Y    54
#define CONTENT_TOP 17

/* ---- DECLARATIONS ---- */
void connectWiFi(); bool loginMerchant(); void processPayment();
void drawHeader(String l, String r); void drawFooter(String t);
void centerText(String t, int y, int sz); void drawDivider(int y);
void showIdleScreen(); void showWaitingCardScreen();
void showProcessingStep(String s, String d);
void showSuccessScreen(String u, String a, String t);
void showErrorScreen(String r, String d);
void showInfoScreen(String title, String l1, String l2, String f);
void cancelTransaction(); void resetToIdle(); void bootSequence();
void beep(int ms); void errorBeep(); void successBeep();
void ledGreen(bool on); void ledRed(bool on); void ledsOff();


/* =========================================  SETUP  */
void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(GREEN_LED,  OUTPUT);
  pinMode(RED_LED,    OUTPUT);
  ledsOff();
  digitalWrite(BUZZER_PIN, LOW);

  Wire.begin();
  if (!display.begin(SCREEN_ADDRESS, true)) { for (;;); }
  display.display();
  delay(300);

  SPI.begin();
  rfid.PCD_Init();

  bootSequence();
  connectWiFi();

  if (loginMerchant()) {
    resetToIdle();
  } else {
    showInfoScreen("AUTH ERROR", "Login Failed", "Check Credentials", "Restarting...");
    delay(5000);
    ESP.restart();
  }
}


/* =========================================  LOOP  */
void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWiFi();

  char key = keypad.getKey();

  // Global cancel
  if (key == 'A' && currentState != STATE_PROCESSING)
    if (paymentAmount.length() > 0 || currentState == STATE_WAITING_CARD)
      cancelTransaction();

  if (currentState == STATE_IDLE) {
    if (key >= '0' && key <= '9') {
      beep(40);
      if (paymentAmount == "0") paymentAmount = "";
      paymentAmount += key;
      showIdleScreen();

    } else if (key == '*') {            // Backspace
      beep(40);
      if (paymentAmount.length() > 0)
        paymentAmount = paymentAmount.substring(0, paymentAmount.length() - 1);
      showIdleScreen();

    } else if (key == 'D') {            // Confirm
      if (paymentAmount.length() > 0 && paymentAmount != "0") {
        beep(100);
        currentState = STATE_WAITING_CARD;
        showWaitingCardScreen();
      } else {
        errorBeep(); ledRed(true); delay(300); ledRed(false);
      }
    }

  } else if (currentState == STATE_WAITING_CARD) {
    if (rfid.PICC_IsNewCardPresent() && rfid.PICC_ReadCardSerial()) {
      currentState = STATE_PROCESSING;
      processPayment();
    }
  }
}


/* =========================================  WIFI  */
void connectWiFi() {
  showInfoScreen("WIFI", "Connecting...", String(ssid), "Please wait...");
  WiFi.disconnect(true); delay(500);
  WiFi.mode(WIFI_STA);   delay(200);
  WiFi.begin(ssid, password);

  int c = 0, mx = 40;
  while (WiFi.status() != WL_CONNECTED && c < mx) {
    delay(500); c++;
    showInfoScreen("WIFI", "Connecting...", String(ssid), "Try " + String(c) + "/" + String(mx));
  }

  if (WiFi.status() == WL_CONNECTED) {
    ledGreen(true);
    showInfoScreen("WIFI OK", WiFi.localIP().toString(), "RSSI " + String(WiFi.RSSI()) + "dBm", "Connected!");
    beep(100); delay(1500); ledGreen(false);
  } else {
    ledRed(true);
    showInfoScreen("WIFI FAIL", "Cannot Connect", "Status: " + String(WiFi.status()), "Restarting...");
    delay(4000); ledRed(false); ESP.restart();
  }
}

bool loginMerchant() {
  showInfoScreen("SIGNING IN", "Authenticating", MERCHANT_EMAIL, "Please wait...");
  HTTPClient http;
  http.begin(BASE_URL + "/api/auth/merchant/login");
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(10000);

  StaticJsonDocument<200> doc;
  doc["email"] = MERCHANT_EMAIL; doc["password"] = MERCHANT_PASSWORD;
  String body; serializeJson(doc, body);

  int    code = http.POST(body);
  String resp = http.getString();

  if (code == 200 || code == 201) {
    StaticJsonDocument<1024> res;
    if (deserializeJson(res, resp)) { http.end(); return false; }
    jwtToken   = res["accessToken"].as<String>();
    merchantId = res["user"]["id"].as<long>();
    ledGreen(true);
    showInfoScreen("AUTH OK", "Welcome Back!", "Merchant #" + String(merchantId), "Loading...");
    delay(1200); ledGreen(false); http.end(); return true;
  } else {
    ledRed(true);
    showInfoScreen("AUTH FAIL", "Login Failed", "Code: " + String(code), "Check credentials");
    http.end(); return false;
  }
}


/* =========================================  PAYMENT  */
void processPayment() {
  String uid = "";
  for (byte i = 0; i < rfid.uid.size; i++) {
    uid += String(rfid.uid.uidByte[i] < 0x10 ? "0" : "");
    uid += String(rfid.uid.uidByte[i], HEX);
  }
  uid.toUpperCase();

  showProcessingStep("Card Detected", uid); delay(600);
  showProcessingStep("Connecting...", "Bank Server");

  HTTPClient http;
  http.begin(BASE_URL + "/api/payment/tap");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + jwtToken);
  http.setTimeout(15000);

  StaticJsonDocument<200> doc;
  doc["cardUid"] = uid; doc["amount"] = paymentAmount.toDouble(); doc["merchantId"] = merchantId;
  String body; serializeJson(doc, body);

  showProcessingStep("Verifying...", "Rs. " + paymentAmount);

  int    code = http.POST(body);
  String resp = http.getString();

  bool   success = false;
  String userName = "", txnId = "", errorMsg = "", errorDetail = "";

  if (code == 200 || code == 201) {
    StaticJsonDocument<1024> res;
    if (!deserializeJson(res, resp)) {
      if (res["success"].as<bool>()) {
        success = true;
        userName = res["data"]["userName"].as<String>();
        txnId    = res["data"]["transactionId"].as<String>();
      } else {
        errorMsg    = res["message"].as<String>();
        errorDetail = "Code: " + String(code);
        if (errorMsg == "" || errorMsg == "null") errorMsg = "Declined";
      }
    } else { errorMsg = "Parse Error"; }

  } else if (code == 401) {
    showProcessingStep("Re-Auth", "Token Expired");
    if (loginMerchant()) {
      rfid.PICC_HaltA(); rfid.PCD_StopCrypto1(); http.end();
      processPayment(); return;
    }
    errorMsg = "Auth Expired"; errorDetail = "Please restart";

  } else if (code == 402) { errorMsg = "Low Balance";    errorDetail = "Insufficient Funds";
  } else if (code == 403) {
    StaticJsonDocument<512> err;
    if (!deserializeJson(err, resp)) {
        String msg = err["message"].as<String>();
        // Distinguish blocked vs unassigned card
        if (msg.indexOf("blocked") >= 0 || msg.indexOf("disabled") >= 0) {
            errorMsg    = "Card Blocked";
            errorDetail = "Contact support";
        } else if (msg.indexOf("unassigned") >= 0) {
            errorMsg    = "Card Unassigned";
            errorDetail = "Register card first";
        } else {
            errorMsg    = "Access Denied";
            errorDetail = msg.substring(0, 20);
        }
    } else {
        errorMsg    = "Card Blocked";
        errorDetail = "Access Denied";
    }
}  else if (code == 400) {
    StaticJsonDocument<512> err;
    errorMsg = (!deserializeJson(err, resp)) ? err["message"].as<String>() : "Bad Request";
    if (errorMsg == "" || errorMsg == "null") errorMsg = "Bad Request";
    errorDetail = "Check card/amount";
  } else if (code == 404) { errorMsg = "Card Not Found"; errorDetail = "UID: " + uid.substring(0, 8);
  } else if (code  <  0)  { errorMsg = "No Connection";  errorDetail = "Check Network";
  } else                  { errorMsg = "Server Error";   errorDetail = "Code: " + String(code); }

  if (success) {
    ledGreen(true); successBeep();
    showSuccessScreen(userName, paymentAmount, txnId);
    delay(4000); ledGreen(false);
  } else {
    ledRed(true); errorBeep();
    showErrorScreen(errorMsg, errorDetail);
    delay(3000); ledRed(false);
  }

  rfid.PICC_HaltA(); rfid.PCD_StopCrypto1(); http.end();
  resetToIdle();
}


/* =========================================  DISPLAY HELPERS  */

void drawHeader(String left, String right) {
  display.fillRect(0, 0, SCREEN_WIDTH, HEADER_H, SH110X_WHITE);
  display.setTextColor(SH110X_BLACK); display.setTextSize(1);
  display.setCursor(3, 3); display.print(left);
  int rX = SCREEN_WIDTH - (int)right.length() * 6 - 3;
  if (rX < 0) rX = 0;
  display.setCursor(rX, 3); display.print(right);
}

void drawFooter(String text) {
  display.drawLine(0, FOOTER_Y - 3, SCREEN_WIDTH, FOOTER_Y - 3, SH110X_WHITE);
  display.setTextColor(SH110X_WHITE); display.setTextSize(1);
  int fX = (SCREEN_WIDTH - (int)text.length() * 6) / 2;
  if (fX < 0) fX = 0;
  display.setCursor(fX, FOOTER_Y); display.print(text);
}

void centerText(String text, int y, int sz) {
  int x = (SCREEN_WIDTH - (int)text.length() * 6 * sz) / 2;
  if (x < 0) x = 0;
  display.setTextSize(sz); display.setCursor(x, y); display.print(text);
}

void drawDivider(int y) { display.drawLine(0, y, SCREEN_WIDTH, y, SH110X_WHITE); }


/* =========================================  SCREENS  */

void showIdleScreen() {
  display.clearDisplay();
  drawHeader("UniPay", "READY");
  display.setTextColor(SH110X_WHITE);
  display.setTextSize(1); display.setCursor(3, CONTENT_TOP); display.print("Enter Amount:");
  String amt = "Rs." + (paymentAmount.length() > 0 ? paymentAmount : "0");
  if (amt.length() <= 8) centerText(amt, 28, 2);
  else                   centerText(amt, 30, 1);
  drawFooter("[D]Pay  [*]Del  [A]Clr");
  display.display();
}

void showWaitingCardScreen() {
  display.clearDisplay();
  drawHeader("UniPay", "TAP CARD");
  display.setTextColor(SH110X_WHITE);
  String amt = "Rs." + paymentAmount;
  if (amt.length() <= 8) centerText(amt, CONTENT_TOP, 2);
  else                   centerText(amt, CONTENT_TOP + 2, 1);
  drawDivider(37);
  display.setTextSize(1);
  centerText(">>> TAP YOUR CARD <<<", 41, 1);
  drawFooter("[A] Cancel");
  display.display();
}

void showProcessingStep(String step, String detail) {
  display.clearDisplay();
  drawHeader("UniPay", "WAIT");
  display.setTextColor(SH110X_WHITE); display.setTextSize(1);
  display.setCursor(3, CONTENT_TOP); display.print(">> " + step);
  String det = detail; if (det.length() > 20) det = det.substring(0, 20);
  display.setCursor(3, CONTENT_TOP + 12); display.print(det);
  display.drawRect(3, 44, 122, 8, SH110X_WHITE);
  int fill = (int)(millis() / 80) % 120;
  if (fill > 2) display.fillRect(4, 45, fill, 6, SH110X_WHITE);
  display.display();
}

void showSuccessScreen(String userName, String amount, String txnId) {
  display.clearDisplay();
  drawHeader("UniPay", "APPROVED");
  display.setTextColor(SH110X_WHITE);
  String amt = "Rs." + amount;
  if (amt.length() <= 8) centerText(amt, CONTENT_TOP, 2);
  else                   centerText(amt, CONTENT_TOP + 2, 1);
  drawDivider(35);
  display.setTextSize(1);
  String u = userName; if (u.length() > 17) u = u.substring(0, 17);
  display.setCursor(3, 38); display.print("User : " + u);
  String t = txnId; if (t.length() > 16) t = t.substring(0, 16);
  display.setCursor(3, 50); display.print("Txn  : " + t);
  display.display();
}

void showErrorScreen(String reason, String detail) {
  display.clearDisplay();
  drawHeader("UniPay", "DECLINED");
  display.setTextColor(SH110X_WHITE);
  String r = reason; if (r.length() > 20) r = r.substring(0, 20);
  if (r.length() <= 10) centerText(r, CONTENT_TOP + 2, 2);
  else                  centerText(r, CONTENT_TOP + 2, 1);
  String det = detail; if (det.length() > 21) det = det.substring(0, 21);
  int dX = (SCREEN_WIDTH - (int)det.length() * 6) / 2; if (dX < 0) dX = 0;
  display.setTextSize(1); display.setCursor(dX, 40); display.print(det);
  drawFooter("Tap A to retry");
  display.display();
}

void showInfoScreen(String title, String line1, String line2, String footer) {
  display.clearDisplay();
  drawHeader("UniPay", title);
  display.setTextColor(SH110X_WHITE); display.setTextSize(1);
  String l1 = line1; if (l1.length() > 21) l1 = l1.substring(0, 21);
  int x1 = (SCREEN_WIDTH - (int)l1.length() * 6) / 2; if (x1 < 0) x1 = 0;
  display.setCursor(x1, CONTENT_TOP + 4); display.print(l1);
  String l2 = line2; if (l2.length() > 21) l2 = l2.substring(0, 21);
  int x2 = (SCREEN_WIDTH - (int)l2.length() * 6) / 2; if (x2 < 0) x2 = 0;
  display.setCursor(x2, CONTENT_TOP + 18); display.print(l2);
  drawFooter(footer);
  display.display();
}


/* =========================================  UTILITY  */

void cancelTransaction() {
  ledRed(true); errorBeep();
  showInfoScreen("CANCELLED", "Transaction", "Aborted", "Returning home...");
  delay(1500); ledRed(false);
  resetToIdle();
}

void resetToIdle() {
  paymentAmount = ""; currentState = STATE_IDLE;
  showIdleScreen();
}

void bootSequence() {
  display.clearDisplay(); display.setTextColor(SH110X_WHITE);
  display.drawRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, SH110X_WHITE);
  centerText("UniPay", 10, 3);
  display.drawLine(10, 38, SCREEN_WIDTH - 10, 38, SH110X_WHITE);
  display.setTextSize(1);
  centerText("POS Terminal v2.0", 44, 1);
  centerText("Initializing...",   54, 1);
  display.display();
  beep(100); delay(60); beep(100); delay(1800);
}

void ledGreen(bool on) { digitalWrite(GREEN_LED, on ? HIGH : LOW); }
void ledRed(bool on)   { digitalWrite(RED_LED,   on ? HIGH : LOW); }
void ledsOff()         { ledGreen(false); ledRed(false); }

void beep(int ms)    { digitalWrite(BUZZER_PIN, HIGH); delay(ms); digitalWrite(BUZZER_PIN, LOW); }
void errorBeep()     { beep(150); delay(60); beep(150); }
void successBeep()   { beep(80); delay(80); beep(80); delay(80); beep(250); }
