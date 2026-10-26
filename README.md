# Smart home với ESP32 qua giao thức BLE

Hệ thống nhà thông minh sử dụng vi điều khiển ESP32 giao tiếp qua giao thức Bluetooth Low Energy (BLE), đi kèm ứng dụng Android điều khiển và đồng bộ dữ liệu lên cơ sở dữ liệu.

## Cấu trúc dự án

* **`android-app/` (`ble`)**: Mã nguồn ứng dụng Android dùng để kết nối và điều khiển 3 node ESP32 qua BLE.
* **`esp32-nodes/` (`iotv2`)**: Mã nguồn nhúng cho 3 mạch ESP32:
  * **`node_a`**: Điều khiển bật/tắt đèn.
  * **`node_b`**: Đọc dữ liệu cảm biến chuyển động.
  * **`node_s`**: Node trung tâm quản lý chung `node_a`, `node_b` và đẩy thông tin lên Database qua Wi-Fi.
