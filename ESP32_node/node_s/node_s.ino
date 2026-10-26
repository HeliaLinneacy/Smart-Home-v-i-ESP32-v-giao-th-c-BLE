// 1. Khai báo thông tin cấu hình Blynk (Bắt buộc đặt ở đầu file)
#define BLYNK_TEMPLATE_ID "TMPL6toRR1F0P"
#define BLYNK_TEMPLATE_NAME "dieu khien thiet bi"
#define BLYNK_AUTH_TOKEN "TUYv1vd4YQ6fMwH8B6VQi4ZAMlVYca53"

#include <Arduino.h>
#include "wifiConfig.h"          // Đã bao gồm WiFi.h, WebServer.h, EEPROM.h, ArduinoJson.h, Ticker.h
#include <BlynkSimpleEsp32.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <NimBLEDevice.h>
#include "mbedtls/md.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

// Chân GPIO 2 đã được wifiConfig.h sử dụng làm ledPin báo trạng thái Wi-Fi
// Chân GPIO 0 đã được wifiConfig.h sử dụng làm btnPin (Nhấn giữ 5s để xóa Wi-Fi cũ)
#define PIN_SIGNAL_LED   3   // Đèn LED báo nhận tín hiệu BLE (GPIO 3)

const char* SUPABASE_URL  = "https://pktbrsuwkfjgmflrnajj.supabase.co";
const char* SUPABASE_KEY  = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBrdGJyc3V3a2ZqZ21mbHJuYWpqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI2NzQzMzksImV4cCI6MjEwODI1MDMzOX0.HqLZnS_eTnrRI6c1dsmNE2XZ_UyrepT4h_MbBQtbig8";
const char* SECRET_KEY    = "BUI_THE_VINH_IOT_2026_SECRET";

#define SERVICE_UUID_A        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_CONTROL_UUID_A   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define SERVICE_UUID_S        "12345678-1234-5678-1234-56789abcdef0"
#define CHAR_GATEWAY_UUID_S   "12345678-1234-5678-1234-56789abcdef1"

NimBLECharacteristic* pGatewayNotifyChar = nullptr;
NimBLEClient* pClientA = nullptr;

NimBLEAddress nodeAAddress;
volatile bool hasFoundNodeA = false;
volatile bool motionEventDetected = false;

uint32_t lastHandledMotionId = 0;
uint32_t lastHandledNodeALogId = 0;

// Biến quản lý kết nối Blynk
bool blynkConnect = false;
unsigned long lastBlynkRetry = 0;

// Hàng đợi đẩy Log lên Supabase chạy ngầm (không làm gián đoạn BLE & Blynk)
struct SupabaseLogItem {
    char nodeName[16];
    char eventType[24];
    char message[80];
};

// Hàng đợi lệnh BLE gửi sang Node A (hỗ trợ nhận nhiều lệnh liên tiếp từ Blynk và BLE)
struct BLECmdItem {
    char cmd[24];
};

QueueHandle_t supabaseQueue;
QueueHandle_t bleCmdQueue;
Ticker ledTicker;

void turnOffSignalLED() {
    digitalWrite(PIN_SIGNAL_LED, LOW);
}

void triggerSignalLED() {
    digitalWrite(PIN_SIGNAL_LED, HIGH);
    ledTicker.once(1.0, turnOffSignalLED);
}

// Đưa lệnh điều khiển vào hàng đợi để gửi sang Node A
void enqueueBLECommand(const String& cmd) {
    if (!bleCmdQueue) return;
    BLECmdItem item;
    strncpy(item.cmd, cmd.c_str(), sizeof(item.cmd) - 1);
    item.cmd[sizeof(item.cmd) - 1] = '\0';
    xQueueSend(bleCmdQueue, &item, 0);
}

// ================= CÁC HÀM XỬ LÝ SỰ KIỆN BLYNK =================
BLYNK_CONNECTED() {
    Serial.println("[BLYNK] Đã đồng bộ kết nối với Blynk Cloud!");
    // Nếu muốn đồng bộ trạng thái nút nhấn khi vừa kết nối, bỏ comment dòng dưới:
    // Blynk.syncVirtual(V1, V2, V3, V4);
}

BLYNK_WRITE(V1) {
    int p = param.asInt();
    String cmd = p ? "LED1_ON" : "LED1_OFF";
    Serial.println("[BLYNK] Nhận lệnh V1 -> " + cmd);
    enqueueBLECommand(cmd);
}

BLYNK_WRITE(V2) {
    int p = param.asInt();
    String cmd = p ? "LED2_ON" : "LED2_OFF";
    Serial.println("[BLYNK] Nhận lệnh V2 -> " + cmd);
    enqueueBLECommand(cmd);
}

BLYNK_WRITE(V3) {
    int p = param.asInt();
    String cmd = p ? "LED3_ON" : "LED3_OFF";
    Serial.println("[BLYNK] Nhận lệnh V3 -> " + cmd);
    enqueueBLECommand(cmd);
}

BLYNK_WRITE(V4) {
    int p = param.asInt();
    String cmd = p ? "LED4_ON" : "LED4_OFF";
    Serial.println("[BLYNK] Nhận lệnh V4 -> " + cmd);
    enqueueBLECommand(cmd);
}

// Đồng bộ ngược trạng thái thực tế từ Node A lên giao diện Blynk
void syncStateToBlynk(const String& cmdName) {
    if (!Blynk.connected()) return;
    if (cmdName == "LED1_ON")       Blynk.virtualWrite(V1, 1);
    else if (cmdName == "LED1_OFF") Blynk.virtualWrite(V1, 0);
    else if (cmdName == "LED2_ON")  Blynk.virtualWrite(V2, 1);
    else if (cmdName == "LED2_OFF") Blynk.virtualWrite(V2, 0);
    else if (cmdName == "LED3_ON")  Blynk.virtualWrite(V3, 1);
    else if (cmdName == "LED3_OFF") Blynk.virtualWrite(V3, 0);
    else if (cmdName == "LED4_ON")  Blynk.virtualWrite(V4, 1);
    else if (cmdName == "LED4_OFF") Blynk.virtualWrite(V4, 0);
}
// ===============================================================

String computeShortHMAC(const String& message, const char* key) {
    byte hmacResult[32];
    mbedtls_md_context_t ctx;
    mbedtls_md_init(&ctx);
    mbedtls_md_setup(&ctx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
    mbedtls_md_hmac_starts(&ctx, (const unsigned char*)key, strlen(key));
    mbedtls_md_hmac_update(&ctx, (const unsigned char*)message.c_str(), message.length());
    mbedtls_md_hmac_finish(&ctx, hmacResult);
    mbedtls_md_free(&ctx);
    char hexStr[9];
    for (int i = 0; i < 4; i++) sprintf(hexStr + (i * 2), "%02x", hmacResult[i]);
    hexStr[8] = '\0';
    return String(hexStr);
}

// Hàm tạo mô tả chi tiết cho từng lệnh bật/tắt LED từ Node A
String formatLEDLogMessage(const String& cmd, uint32_t logId) {
    if (cmd == "LED1_ON")  return "Điều khiển BẬT LED 1 (Chân 13) [#" + String(logId) + "]";
    if (cmd == "LED1_OFF") return "Điều khiển TẮT LED 1 (Chân 13) [#" + String(logId) + "]";
    if (cmd == "LED2_ON")  return "Điều khiển BẬT LED 2 (Chân 12) [#" + String(logId) + "]";
    if (cmd == "LED2_OFF") return "Điều khiển TẮT LED 2 (Chân 12) [#" + String(logId) + "]";
    if (cmd == "LED3_ON")  return "Điều khiển BẬT LED 3 (Chân 11) [#" + String(logId) + "]";
    if (cmd == "LED3_OFF") return "Điều khiển TẮT LED 3 (Chân 11) [#" + String(logId) + "]";
    if (cmd == "LED4_ON")  return "Điều khiển BẬT LED 4 (Chân 3) [#" + String(logId) + "]";
    if (cmd == "LED4_OFF") return "Điều khiển TẮT LED 4 (Chân 3) [#" + String(logId) + "]";
    return "Điều khiển Node A: " + cmd + " [#" + String(logId) + "]";
}

// Đưa Log vào hàng đợi để Task chạy ngầm gửi lên Supabase
void enqueueSupabaseLog(const char* nodeName, const String& eventType, const String& message) {
    SupabaseLogItem item;
    strncpy(item.nodeName, nodeName, sizeof(item.nodeName) - 1);
    item.nodeName[sizeof(item.nodeName) - 1] = '\0';

    strncpy(item.eventType, eventType.c_str(), sizeof(item.eventType) - 1);
    item.eventType[sizeof(item.eventType) - 1] = '\0';

    strncpy(item.message, message.c_str(), sizeof(item.message) - 1);
    item.message[sizeof(item.message) - 1] = '\0';

    xQueueSend(supabaseQueue, &item, 0);
}

// Hàm gửi trực tiếp lên Supabase (được gọi bởi Task chạy ngầm)
void pushLogToSupabase(const char* nodeName, const char* eventType, const char* message) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[SUPABASE] Lỗi: Wi-Fi chưa kết nối!");
        return;
    }

    WiFiClientSecure client;
    client.setInsecure();
    client.setTimeout(10);

    HTTPClient https;
    String endpoint = String(SUPABASE_URL) + "/rest/v1/system_logs";

    if (https.begin(client, endpoint)) {
        https.setTimeout(8000);
        https.addHeader("Content-Type", "application/json");
        https.addHeader("apikey", SUPABASE_KEY);
        https.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
        https.addHeader("Prefer", "return=minimal");

        JsonDocument doc;
        doc["node_name"]  = nodeName;
        doc["event_type"] = eventType;
        doc["message"]    = message;

        String payload;
        serializeJson(doc, payload);

        int httpCode = https.POST(payload);
        if (httpCode == 201 || httpCode == 200 || httpCode == 204) {
            Serial.printf("[SUPABASE] ===> ĐÃ LƯU LOG (%s - %s) THÀNH CÔNG! <===\n", nodeName, eventType);
        } else if (httpCode > 0) {
            Serial.printf("[SUPABASE] Lỗi HTTP %d: %s\n", httpCode, https.getString().c_str());
        } else {
            Serial.printf("[SUPABASE] Lỗi kết nối HTTPS (%d): %s\n", httpCode, https.errorToString(httpCode).c_str());
        }
        https.end();
    }
}

// Task chạy ngầm chuyên xử lý gửi dữ liệu lên Supabase để BLE luôn hoạt động 100% thời gian
void supabaseWorkerTask(void* parameter) {
    SupabaseLogItem item;
    while (true) {
        if (xQueueReceive(supabaseQueue, &item, portMAX_DELAY) == pdTRUE) {
            pushLogToSupabase(item.nodeName, item.eventType, item.message);
            vTaskDelay(pdMS_TO_TICKS(50));
        }
    }
}

bool sendBLECommandToNodeA(const String& command) {
    NimBLEScan* pScan = NimBLEDevice::getScan();
    pScan->stop();

    if (!hasFoundNodeA) {
        Serial.println("[NODE S] Đang quét tìm Node A (3 giây)...");
        NimBLEScanResults results = pScan->getResults(3000, false);
        for (int i = 0; i < results.getCount(); i++) {
            const NimBLEAdvertisedDevice* dev = results.getDevice(i);
            String devName = String(dev->getName().c_str());
            if (devName == "NODE_A" || devName == "ESP32S3_NODE_A" || dev->isAdvertisingService(NimBLEUUID(SERVICE_UUID_A))) {
                nodeAAddress = dev->getAddress();
                hasFoundNodeA = true;
                break;
            }
        }
        pScan->clearResults();
    }

    if (!hasFoundNodeA) {
        Serial.println("[NODE S] Không tìm thấy Node A!");
        pScan->start(0, false, false);
        return false;
    }

    bool success = false;
    if (pClientA) {
        pClientA->setConnectTimeout(5000);
        if (pClientA->connect(nodeAAddress)) {
            NimBLERemoteService* pSvc = pClientA->getService(SERVICE_UUID_A);
            if (pSvc) {
                NimBLERemoteCharacteristic* pChr = pSvc->getCharacteristic(CHAR_CONTROL_UUID_A);
                if (pChr) {
                    String signedPacket = command + "|" + computeShortHMAC(command, SECRET_KEY);
                    if (pChr->writeValue(signedPacket.c_str(), signedPacket.length(), false)) {
                        Serial.println("[NODE S -> NODE A] Đã gửi lệnh: " + command);
                        success = true;
                    }
                }
            }
            pClientA->disconnect();
        } else {
            hasFoundNodeA = false;
        }
    }

    pScan->start(0, false, false);
    return success;
}

class SystemScanCallbacks : public NimBLEScanCallbacks {
    void onResult(const NimBLEAdvertisedDevice* advertisedDevice) override {
        String devName = String(advertisedDevice->getName().c_str());

        if (devName == "NODE_A" || devName == "ESP32S3_NODE_A" || advertisedDevice->isAdvertisingService(NimBLEUUID(SERVICE_UUID_A))) {
            nodeAAddress = advertisedDevice->getAddress();
            hasFoundNodeA = true;
        }

        if (advertisedDevice->haveManufacturerData()) {
            std::string stdData = advertisedDevice->getManufacturerData();
            String rawData = String(stdData.data(), stdData.length());

            // 1. Bắt gói tin Log bật/tắt LED từ Node A (Định dạng: LOG:<id>:<cmd>|<hmac>)
            int logPos = rawData.indexOf("LOG:");
            if (logPos != -1) {
                String validPacket = rawData.substring(logPos);
                int sep = validPacket.indexOf('|');
                if (sep != -1 && validPacket.length() >= sep + 9) {
                    String msg = validPacket.substring(0, sep);
                    String sig = validPacket.substring(sep + 1, sep + 9);

                    if (sig.equalsIgnoreCase(computeShortHMAC(msg, SECRET_KEY))) {
                        int firstColon = msg.indexOf(':');
                        int secondColon = msg.indexOf(':', firstColon + 1);
                        if (firstColon != -1 && secondColon != -1) {
                            uint32_t logId = msg.substring(firstColon + 1, secondColon).toInt();
                            String cmdName = msg.substring(secondColon + 1);

                            if (logId != lastHandledNodeALogId) {
                                lastHandledNodeALogId = logId;
                                Serial.printf("\n[NODE S] ===> NHẬN LOG TỪ NODE A (#%u): %s <===\n", logId, cmdName.c_str());
                                triggerSignalLED();

                                // Đồng bộ trạng thái lên Blynk và đẩy log lên Supabase
                                syncStateToBlynk(cmdName);
                                String logDesc = formatLEDLogMessage(cmdName, logId);
                                enqueueSupabaseLog("NODE_A", cmdName, logDesc);
                            }
                        }
                    }
                }
            }

            // 2. Bắt gói tin chuyển động từ Node B (Định dạng: MOTION:<id>|<hmac>)
            int motionPos = rawData.indexOf("MOTION:");
            if (motionPos != -1) {
                String validPacket = rawData.substring(motionPos);
                int sep = validPacket.indexOf('|');
                if (sep != -1 && validPacket.length() >= sep + 9) {
                    String msg = validPacket.substring(0, sep);
                    String sig = validPacket.substring(sep + 1, sep + 9);

                    if (sig.equalsIgnoreCase(computeShortHMAC(msg, SECRET_KEY))) {
                        uint32_t currentMotionId = msg.substring(7).toInt();
                        if (currentMotionId != lastHandledMotionId) {
                            lastHandledMotionId = currentMotionId;
                            motionEventDetected = true;
                        }
                    }
                }
            }
        }
    }
};

static SystemScanCallbacks scanCallbacks;

class PhoneCommandCallback : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pChar, NimBLEConnInfo& connInfo) override {
        String cmd = String(pChar->getValue().c_str());
        cmd.trim();
        cmd.toUpperCase();
        if (cmd.length() > 0) {
            enqueueBLECommand(cmd);
        }
    }
};

void setup() {
    Serial.begin(115200);

    pinMode(PIN_SIGNAL_LED, OUTPUT);
    digitalWrite(PIN_SIGNAL_LED, LOW);

    // Khởi tạo hàng đợi Supabase và hàng đợi lệnh BLE
    supabaseQueue = xQueueCreate(30, sizeof(SupabaseLogItem));
    bleCmdQueue   = xQueueCreate(20, sizeof(BLECmdItem));
    xTaskCreatePinnedToCore(supabaseWorkerTask, "SupabaseTask", 8192, NULL, 1, NULL, 0);

    // Khởi tạo Wi-Fi qua wifiConfig (Đọc EEPROM hoặc phát Access Point cấu hình)
    wifiConfig.begin();
    if (wifiMode != 0) {
        WiFi.setSleep(true);
    }

    // Cấu hình máy chủ Blynk
    Blynk.config(BLYNK_AUTH_TOKEN, "blynk.cloud", 80);

    // Khởi tạo BLE Gateway (Node S)
    NimBLEDevice::init("ESP32S3_NODE_S");
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    pClientA = NimBLEDevice::createClient();

    NimBLEServer* pServer = NimBLEDevice::createServer();
    NimBLEService* pService = pServer->createService(SERVICE_UUID_S);

    pGatewayNotifyChar = pService->createCharacteristic(
        CHAR_GATEWAY_UUID_S,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY
    );
    pGatewayNotifyChar->setCallbacks(new PhoneCommandCallback());
    pService->start();

    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID_S);
    pAdvertising->enableScanResponse(true);
    pAdvertising->setName("ESP32S3_NODE_S");
    pAdvertising->start();

    NimBLEScan* pScan = NimBLEDevice::getScan();
    pScan->setScanCallbacks(&scanCallbacks, true);
    pScan->setInterval(80);
    pScan->setWindow(50);
    pScan->setActiveScan(true);
    pScan->start(0, false, false);

    Serial.println("[NODE S] Khởi tạo hoàn tất! Đang chạy Wi-Fi/Blynk và lắng nghe BLE...");
}

void loop() {
    // 1. Duy trì kiểm tra nút nhấn Reset Wi-Fi (GPIO 0) và WebServer cấu hình Wi-Fi
    wifiConfig.run();

    // 2. Duy trì kết nối và xử lý sự kiện Blynk khi đã có Wi-Fi
    if (WiFi.status() == WL_CONNECTED) {
        if (!blynkConnect && (millis() - lastBlynkRetry > 10000 || lastBlynkRetry == 0)) {
            lastBlynkRetry = millis();
            Serial.println("[BLYNK] Connecting to blynk cloud...!");
            if (Blynk.connect(5000)) {
                Serial.println("[BLYNK] Connected to blynk cloud!");
                blynkConnect = true;
            } else {
                Serial.println("[BLYNK] Connection failed. Try again later.");
            }
        }
        if (!Blynk.connected()) {
            blynkConnect = false;
        } else {
            Blynk.run();
        }
    } else {
        blynkConnect = false;
    }

    // 3. Xử lý khi nhận tín hiệu chuyển động PIR từ Node B
    if (motionEventDetected) {
        motionEventDetected = false;
        Serial.printf("\n[NODE S] ===> ĐÃ BẮT TÍN HIỆU NODE B (ID #%u) <===\n", lastHandledMotionId);
        triggerSignalLED();

        if (pGatewayNotifyChar) {
            String alertMsg = "ALERT:MOTION:" + String(lastHandledMotionId);
            pGatewayNotifyChar->setValue(alertMsg.c_str());
            pGatewayNotifyChar->notify();
        }

        sendBLECommandToNodeA("LIGHT:1");
        enqueueSupabaseLog("NODE_S", "MOTION_DETECTED", "PIR phát hiện chuyển động #" + String(lastHandledMotionId));
    }

    // 4. Xử lý hàng đợi lệnh điều khiển từ Blynk (V1-V4) hoặc từ BLE Điện thoại gửi sang Node A
    BLECmdItem cmdItem;
    if (xQueueReceive(bleCmdQueue, &cmdItem, 0) == pdTRUE) {
        sendBLECommandToNodeA(String(cmdItem.cmd));
    }

    delay(20);
}