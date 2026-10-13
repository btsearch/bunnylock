# Tested devices

These results were reported from real devices. Support may change with firmware, carrier configuration, or modem version.

## What each backend can do

| Backend           | Needs root | Band | ARFCN | RAT | PCI |
| ----------------- | :--------: | :--: | :---: | :-: | :-: |
| Android radio HAL |     No     |  ✅  |  ✅   | ✅  | ❌  |
| MediaTek AT       | Sometimes  |  ✅  |  ✅   | ✅  | ✅  |
| Qualcomm QMI      |    Yes     |  ✅  |  ✅   | ✅  | ✅  |

Without root, bunnylock runs through Shizuku. Some MediaTek phones allow AT commands that way, others need root.

## Devices

✅ works, ❌ doesn't work, - not tested.

| Device                        | Android | Access  | Backend           | Band | ARFCN | RAT | PCI | Notes                    |
| ----------------------------- | :-----: | ------- | ----------------- | :--: | :---: | :-: | :-: | ------------------------ |
| Google Pixel 6 Pro            |   17    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Google Pixel 10               |   17    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| OnePlus Nord CE5              |   16    | Shizuku | MediaTek AT       |  ✅  |  ✅   | ✅  | ✅  | Stock ROM                |
| Oppo Reno 5 5G                |   13    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Oppo A74 5G                   |   13    | Shizuku | Android radio HAL |  ✅  |  ❌   | ✅  | ❌  |                          |
| Oppo Find X9 Pro              |   16    | Shizuku | MediaTek AT       |  ✅  |  ✅   | ✅  | ✅  | Stock ROM                |
| Realme 8 Pro                  |   13    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Redmi Note 10                 |   14    | Root    | MediaTek AT       |  ✅  |  ✅   | ✅  | ✅  | Custom ROM               |
| Samsung Galaxy A33            |   15    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy A37            |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy A52            |   14    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy A52s           |   14    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy A57            |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy M11            |   12    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy M23            |   15    | Root    | Qualcomm QMI      |  ✅  |  ✅   | ✅  | ✅  |                          |
| Samsung Galaxy M35            |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy S21 Ultra      |   15    | Shizuku | Android radio HAL |  ❌  |  ❌   | ✅  | ❌  | Modem ignores everything |
| Samsung Galaxy S23+           |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy S23 Ultra      |   16    | Root    | Qualcomm QMI      |  ✅  |  ✅   | ✅  | ✅  |                          |
| Samsung Galaxy S24            |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy S25            |   17    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy S25 Ultra      |   16    | Root    | Qualcomm QMI      |  ✅  |  ✅   | ✅  | ✅  |                          |
| Samsung Galaxy S26 Ultra      |   17    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Samsung Galaxy Z Fold 8 Ultra |   17    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Sony Xperia 1 V               |   15    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Sony Xperia 1 VII             |   16    | Shizuku | Android radio HAL |  ✅  |  ✅   | ✅  | ❌  |                          |
| Ulefone Note 6T               |   12    | Shizuku | MediaTek AT       |  ✅  |  ✅   | ✅  | ✅  |                          |
| Xiaomi 14T                    |   16    | Shizuku | Android radio HAL |  ❌  |  ❌   | ✅  | ❌  | Modem ignores everything |

## Reporting a device

If you test another phone, include:

- the model and Android version
- the access method (root or Shizuku)
- the selected backend
- the features you actually tried, and whether each one worked
