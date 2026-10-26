#include <Arduino.h>
#include <NimBLEDevice.h>
#include "mbedtls/md.h"
#include "esp_sleep.h"

#define PIN_PIR_SENSOR   GPIO_NUM_4  
#define PIN_ALERT_LED    7           
#define AWAKE_DURATION   3000        // Chỉ phát sóng BLE đúng 3 giây

const char* SECRET_KEY = "BUI_THE_VINH_IOT_2026_SECRET";

#define SERVICE_UUID_B   "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_MOTION_UUID "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

RTC_DATA_ATTR uint32_t motionCounter = 0;

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

void setup() {
    Serial.begin(115200);
    pinMode(PIN_PIR_SENSOR, INPUT_PULLDOWN);
    pinMode(PIN_ALERT_LED, OUTPUT);

    esp_sleep_wakeup_cause_t wakeup_reason = esp_sleep_get_wakeup_cause();
    
    // Xử lý khi có chuyển động thực sự
    if (wakeup_reason == ESP_SLEEP_WAKEUP_EXT0 || digitalRead(PIN_PIR_SENSOR) == HIGH) {
        motionCounter++; // Bắt buộc tăng ID sự kiện mỗi lần thức dậy
        digitalWrite(PIN_ALERT_LED, HIGH); 
        Serial.printf("[NODE B] PHÁT HIỆN CHUYỂN ĐỘNG! (#%u)\n", motionCounter);

        String msg = "MOTION:" + String(motionCounter);
        String securePacket = msg + "|" + computeShortHMAC(msg, SECRET_KEY);

        NimBLEDevice::init("NODE_B");
        NimBLEServer* pServer = NimBLEDevice::createServer();
        NimBLEService* pService = pServer->createService(SERVICE_UUID_B);
        NimBLECharacteristic* pMotionChar = pService->createCharacteristic(
            CHAR_MOTION_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
        );
        pMotionChar->setValue(securePacket.c_str());
        pService->start();

        NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
        std::string payloadData = std::string(securePacket.c_str());
        pAdvertising->setManufacturerData(payloadData);
        pAdvertising->start();
        
        Serial.println("[NODE B] Đã phát sóng BLE: " + securePacket);

        // 1. Chỉ phát sóng BLE đúng 3 giây rồi ngắt
        unsigned long startTime = millis();
        while (millis() - startTime < AWAKE_DURATION) {
            pMotionChar->notify();
            delay(300);
        }
        
        NimBLEDevice::deinit(true);
        digitalWrite(PIN_ALERT_LED, LOW);

        // 2. Chờ cảm biến PIR hạ về mức LOW (Tối đa 8 giây) trước khi cho chip đi ngủ
        Serial.println("[NODE B] Đang chờ cảm biến PIR nhả về mức LOW...");
        unsigned long waitLowStart = millis();
        while (digitalRead(PIN_PIR_SENSOR) == HIGH && (millis() - waitLowStart < 8000)) {
            delay(200);
        }
    }
    
    digitalWrite(PIN_ALERT_LED, LOW);
    
    // Cấu hình đánh thức bằng ngắt ngoài khi PIR lên mức HIGH
    esp_sleep_enable_ext0_wakeup(PIN_PIR_SENSOR, 1); 
    Serial.println("[NODE B] Vào Deep Sleep chờ lượt chuyển động tiếp theo...\n");
    esp_deep_sleep_start();
}

void loop() {}