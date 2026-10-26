#include <Arduino.h>
#include <NimBLEDevice.h>
#include "mbedtls/md.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

// Giữ nguyên đúng các chân LED ban đầu của bạn
const int led1 = 13;
const int led2 = 12;
const int led3 = 11;
const int led4 = 3; 

#define PIN_STATUS_LED        2
const char* SECRET_KEY = "BUI_THE_VINH_IOT_2026_SECRET";

#define SERVICE_UUID_A        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_CONTROL_UUID_A   "beb5483e-36e1-4688-b7f5-ea07361b26a8"

NimBLECharacteristic* pControlChar = nullptr;
uint32_t ledLogCounter = 0;

struct CommandItem {
    char cmd[24];
    bool fromPhone;
};

struct LogBroadcastItem {
    char payload[30];
};

QueueHandle_t cmdQueue;
QueueHandle_t logQueue;

unsigned long lastAdvUpdateTime = 0;
unsigned long lastKeepAliveTime = 0;
bool isBroadcastingLog = false;

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

void updateBLEAdvertising(const String& manufacturerPayload = "") {
    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
    
    NimBLEAdvertisementData advData;
    advData.setFlags(0x06); 
    if (manufacturerPayload.length() > 0) {
        advData.setManufacturerData(std::string(manufacturerPayload.c_str()));
    }

    NimBLEAdvertisementData scanRespData;
    scanRespData.setName("NODE_A");
    scanRespData.addServiceUUID(NimBLEUUID(SERVICE_UUID_A));

    pAdvertising->setAdvertisementData(advData);
    pAdvertising->setScanResponseData(scanRespData);
    if (!pAdvertising->isAdvertising()) {
        pAdvertising->start();
    }
}

void enqueueLogToNodeS(const String& cmdName) {
    ledLogCounter++;
    String msg = "LOG:" + String(ledLogCounter) + ":" + cmdName;
    String fullPacket = msg + "|" + computeShortHMAC(msg, SECRET_KEY);

    LogBroadcastItem item;
    strncpy(item.payload, fullPacket.c_str(), sizeof(item.payload) - 1);
    item.payload[sizeof(item.payload) - 1] = '\0';
    xQueueSend(logQueue, &item, 0);
}

void executeLEDCommand(const String& cmd, bool fromPhone) {
    bool validCmd = true;

    if (cmd == "LED1_ON")       digitalWrite(led1, HIGH);
    else if (cmd == "LED1_OFF") digitalWrite(led1, LOW);
    else if (cmd == "LED2_ON")  digitalWrite(led2, HIGH);
    else if (cmd == "LED2_OFF") digitalWrite(led2, LOW);
    else if (cmd == "LED3_ON")  digitalWrite(led3, HIGH);
    else if (cmd == "LED3_OFF") digitalWrite(led3, LOW);
    else if (cmd == "LED4_ON")  digitalWrite(led4, HIGH);
    else if (cmd == "LED4_OFF") digitalWrite(led4, LOW);
    else if (cmd == "ALL_ON") {
        digitalWrite(led1, HIGH);
        digitalWrite(led2, HIGH);
        digitalWrite(led3, HIGH);
        digitalWrite(led4, HIGH);
    } else if (cmd == "ALL_OFF") {
        digitalWrite(led1, LOW);
        digitalWrite(led2, LOW);
        digitalWrite(led3, LOW);
        digitalWrite(led4, LOW);
    } else {
        validCmd = false;
        Serial.println("[NODE A] Lệnh không hợp lệ: " + cmd);
    }

    if (validCmd) {
        Serial.printf("[NODE A] Đã thực thi: %s (Nguồn: %s)\n", cmd.c_str(), fromPhone ? "ĐIỆN THOẠI" : "HỆ THỐNG");

        if (pControlChar) {
            String ack = "OK:" + cmd;
            pControlChar->setValue(ack.c_str());
            pControlChar->notify();
        }

        // Chỉ gửi log về Node S khi thao tác điều khiển xuất phát từ điện thoại
        if (fromPhone) {
            enqueueLogToNodeS(cmd);
        }
    }
}

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo) override {
        Serial.println("[NODE A] Điện thoại đã kết nối BLE thành công!");
        pServer->updateConnParams(connInfo.getConnHandle(), 12, 24, 0, 600);
        NimBLEDevice::startAdvertising();
    }

    void onDisconnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo, int reason) override {
        Serial.printf("[NODE A] Thiết bị ngắt kết nối (Mã: %d), khởi động lại Advertising...\n", reason);
        NimBLEDevice::startAdvertising();
    }
};

class ControlCharCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pChar, NimBLEConnInfo& connInfo) override {
        std::string val = pChar->getValue();
        if (val.empty()) return;

        String packet = String(val.c_str());
        packet.trim();

        CommandItem item;
        int sep = packet.indexOf('|');

        if (sep != -1) {
            // Lệnh từ Node S gửi xuống (do người dùng thao tác qua app kết nối Node S)
            String cmd = packet.substring(0, sep);
            String sig = packet.substring(sep + 1);
            if (sig.equalsIgnoreCase(computeShortHMAC(cmd, SECRET_KEY))) {
                cmd.toUpperCase();
                strncpy(item.cmd, cmd.c_str(), sizeof(item.cmd) - 1);
                item.cmd[sizeof(item.cmd) - 1] = '\0';
                item.fromPhone = true; // Co như lệnh từ phía người dùng/điện thoại
                xQueueSendFromISR(cmdQueue, &item, NULL);
            }
        } else {
            // Lệnh từ điện thoại kết nối trực tiếp vào Node A
            packet.toUpperCase();
            strncpy(item.cmd, packet.c_str(), sizeof(item.cmd) - 1);
            item.cmd[sizeof(item.cmd) - 1] = '\0';
            item.fromPhone = true;
            xQueueSendFromISR(cmdQueue, &item, NULL);
        }
    }
};

static ServerCallbacks serverCallbacks;
static ControlCharCallbacks controlCallbacks;

void setup() {
    Serial.begin(115200);

    // Khởi tạo 4 chân LED phần cứng
    pinMode(led1, OUTPUT);
    pinMode(led2, OUTPUT);
    pinMode(led3, OUTPUT);
    pinMode(led4, OUTPUT);
    pinMode(PIN_STATUS_LED, OUTPUT);

    digitalWrite(led1, LOW);
    digitalWrite(led2, LOW);
    digitalWrite(led3, LOW);
    digitalWrite(led4, LOW);
    digitalWrite(PIN_STATUS_LED, HIGH);

    cmdQueue = xQueueCreate(20, sizeof(CommandItem));
    logQueue = xQueueCreate(20, sizeof(LogBroadcastItem));

    NimBLEDevice::init("NODE_A");
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    NimBLEDevice::setMTU(128);

    NimBLEServer* pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(&serverCallbacks);
    pServer->advertiseOnDisconnect(true);

    NimBLEService* pService = pServer->createService(SERVICE_UUID_A);
    
    pControlChar = pService->createCharacteristic(
        CHAR_CONTROL_UUID_A,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR | NIMBLE_PROPERTY::NOTIFY
    );
    pControlChar->setCallbacks(&controlCallbacks);
    pControlChar->setValue("READY");
    pService->start();

    updateBLEAdvertising("");

    Serial.println("[NODE A] Đã sẵn sàng! Chỉ phản hồi khi có lệnh điều khiển LED từ điện thoại.");
}

void loop() {
    // Xử lý các lệnh bật/tắt LED từ hàng đợi
    CommandItem cmdItem;
    while (xQueueReceive(cmdQueue, &cmdItem, 0) == pdTRUE) {
        executeLEDCommand(String(cmdItem.cmd), cmdItem.fromPhone);
    }

    // Phát quảng bá Log để Node S bắt được và ghi vào Supabase
    unsigned long now = millis();
    if (now - lastAdvUpdateTime >= 400) {
        LogBroadcastItem logItem;
        if (xQueueReceive(logQueue, &logItem, 0) == pdTRUE) {
            lastAdvUpdateTime = now;
            isBroadcastingLog = true;
            updateBLEAdvertising(String(logItem.payload));
        } else if (isBroadcastingLog && (now - lastAdvUpdateTime >= 1200)) {
            isBroadcastingLog = false;
        }
    }

    // Giữ kết nối BLE chống timeout
    if (now - lastKeepAliveTime >= 15000) {
        lastKeepAliveTime = now;
        if (NimBLEDevice::getServer()->getConnectedCount() > 0 && pControlChar) {
            pControlChar->notify();
        }
    }

    delay(5);
}