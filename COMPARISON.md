# Comparisons

This page compares bunnylock's locking with other Android apps that lock bands or cells.

✅ yes, ❌ no, ❓ not documented or couldn't be checked, - doesn't apply.

## The apps

|                  | bunnylock      | [NetMonster](https://netmonster.app/)                                       | [NSG](https://www.qtrun.com/eng/nsg/)    | [Cellular-Pro](https://play.google.com/store/apps/details?id=make.more.r2d2.play.cellular_pro) | [QCOM Band Menu](https://github.com/Fronsipswu/qcom-band-menu) |
| ---------------- | -------------- | --------------------------------------------------------------------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| Source           | GPL-3.0        | Closed[^nm-core]                                                            | Closed                                   | Closed                                                                                         | GPL-3.0                                                        |
| Price            | Free           | Free, with a Premium subscription[^nm-price]                                | Free, with paid time periods[^nsg-price] | Free, with paid memberships[^cp-price]                                                         | Free                                                           |
| Ads and tracking | None[^updates] | Ads for non-subscribers in some regions, Firebase Analytics and Crashlytics | Ads                                      | Ads                                                                                            | None                                                           |
| Android          | 11+            | 11+ for Android's selection channels                                        | 6+                                       | ❓                                                                                             | 11+                                                            |

## Access and chipsets

|                | bunnylock                                                                        | NetMonster                                                                                                           | NSG                           | Cellular-Pro   | QCOM Band Menu |
| -------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------- | -------------- | -------------- |
| Without root   | Shizuku                                                                          | Shizuku                                                                                                              | ❌                            | ❌             | ❌             |
| With root      | ✅                                                                               | ✅ since 5.4.0                                                                                                       | ✅                            | ✅             | ✅             |
| Qualcomm       | QMI with root, Motorola's own service on Motorola phones, radio HAL with Shizuku | Direct with root. With Shizuku, Motorola's own service on Motorola phones and Android's selection channels on others | Root                          | Root           | QMI with root  |
| MediaTek       | AT commands with root, and with Shizuku where the RIL passes them on             | AT commands with root                                                                                                | Root                          | Root           | ❌             |
| Google Tensor  | Modem NV settings with root, radio HAL with Shizuku                              | Android's selection channels                                                                                         | Root                          | ❓             | ❌[^shannon]   |
| Samsung Exynos | Radio HAL, plus Samsung's 5G mode switch and band menu                           | Android's selection channels                                                                                         | Root and a token from Samsung | ❌             | ❌             |
| Unisoc         | AT commands through the RIL, with Shizuku                                        | Android's selection channels                                                                                         | ❓                            | ❓             | ❌             |
| Anything else  | Radio HAL                                                                        | Android's selection channels                                                                                         | ❌                            | ❌             | ❌             |
| Tested phones  | 55 phones and tablets, see [Tested devices](TESTED_DEVICES.md)                   | 336 models verified by users, see [their list](https://netmonster.app/devices)                                       | No public list                | No public list | No public list |

NetMonster's list is much longer than bunnylock's. It doesn't say whether a lock was verified through root or Shizuku, or which lock types worked.

## What it can lock

|                                       | bunnylock                                                       | NetMonster                                                                | NSG                          | Cellular-Pro                | QCOM Band Menu  |
| ------------------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------- | ---------------------------- | --------------------------- | --------------- |
| 2G and 3G bands                       | ✅                                                              | ❌                                                                        | ✅                           | ❓                          | ✅              |
| 4G bands                              | ✅                                                              | ✅                                                                        | ✅                           | ✅                          | ✅              |
| 5G bands                              | ✅                                                              | ✅                                                                        | ✅[^nsg-paid]                | ✅[^cp-paid]                | ✅              |
| Bands per lock with Shizuku           | Any[^hal-bands]                                                 | Up to 4                                                                   | -                            | -                           | -               |
| 5G SA and NSA bands apart             | Qualcomm                                                        | ❓                                                                        | ❓                           | ❓                          | ✅              |
| 5G mode                               | SA, NSA or both[^nr-mode]                                       | Off, NSA, or NSA and SA                                                   | SA, NSA or both              | ❓                          | SA, NSA or both |
| Network mode                          | ✅                                                              | ✅                                                                        | ✅                           | ✅                          | ✅              |
| Mobile network (PLMN)                 | ✅                                                              | ✅                                                                        | Qualcomm                     | ❓                          | ✅              |
| 4G or 5G channel                      | ✅                                                              | ✅                                                                        | ✅                           | ✅                          | ✅              |
| 4G cell (PCI)                         | Qualcomm with root, MediaTek with root or Shizuku[^mtk-shizuku] | Qualcomm and MediaTek with root                                           | ✅                           | ✅                          | ✅              |
| 5G cell (PCI)                         | Qualcomm with root, MediaTek with root or Shizuku[^mtk-shizuku] | Qualcomm and MediaTek with root                                           | Snapdragon 8 Gen 3 and newer | ✅[^cp-nr-pci]              | ✅              |
| Several PCIs at once                  | Up to 60 on 4G and 8 on 5G, on one channel, on Qualcomm         | ❌                                                                        | ❓                           | ❓                          | ✅              |
| 4G anchor and 5G cell together        | Qualcomm and radio HAL                                          | ❓                                                                        | ❓                           | ❓                          | ❓              |
| Carrier aggregation on or off         | MediaTek                                                        | ❌                                                                        | ❓                           | ❓                          | ❌              |
| Per SIM                               | ✅                                                              | ✅                                                                        | Users report SIM 2 problems  | Users report SIM 2 problems | ✅              |
| Lock routines                         | ❌                                                              | Premium                                                                   | ❓                           | ❓                          | ❌              |
| Keeps locks after a reboot            | Optional on Qualcomm, otherwise up to the phone                 | Up to the phone. On rooted Qualcomm, cell locks stay and band locks don't | Up to the phone              | ❓                          | ❓              |
| Notices when the modem ignores a lock | ✅                                                              | ✅                                                                        | ❓                           | ❓                          | ❓              |
| AT or QMI console                     | Both                                                            | ❌                                                                        | ❓                           | ❓                          | Termux CLI      |

## Sources

- NetMonster: [band and channel lock](https://netmonster.app/articles/netmonster-band-and-channel-lock-4g-5g-shizuku), [routines](https://netmonster.app/articles/netmonster-band-lock-routines), [root support](https://netmonster.app/articles/netmonster-root-or-shizuku-direct-modem-access), [network configuration](https://netmonster.app/articles/netmonster-shizuku-modem-data-and-network-configuration), [devices](https://netmonster.app/devices), [privacy policy](https://netmonster.app/privacy-policy), [Google Play](https://play.google.com/store/apps/details?id=cz.mroczis.netmonster), [NetMonster Core](https://github.com/mroczis/netmonster-core)
- NSG: [Qtrun](https://www.qtrun.com/eng/nsg/), [Google Play](https://play.google.com/store/apps/details?id=com.qtrun.QuickTest), [manual (2017)](https://m.qtrun.com/docs/NSG_Manual_Aug_2017.pdf), [changelogs](https://www.apkmirror.com/apk/qtrun-technologies/network-signal-guru/), user reviews on Google Play
- Cellular-Pro: [Google Play](https://play.google.com/store/apps/details?id=make.more.r2d2.play.cellular_pro), [Tencent](https://sj.qq.com/appdetail/make.more.r2d2.cellular_pro), user reviews on Google Play
- QCOM Band Menu: [README and releases](https://github.com/Fronsipswu/qcom-band-menu)

[^nm-core]: The app is built on the open-source [NetMonster Core](https://github.com/mroczis/netmonster-core) library (Apache-2.0), which doesn't do the locking.

[^nm-price]: Routines & Plain locks after 3rd try need Premium.

[^nsg-price]: Users report about $50 a month.

[^cp-price]: Users report that channel and cell locks need a membership.

[^updates]: The only network request is bunnylock's update check against GitHub, which you can turn off in **Settings**.

[^shannon]: The same author's [Shannon Band Menu](https://github.com/Fronsipswu/shannon-band-menu) covers Tensor Pixels.

[^nsg-paid]: User reviews suggest 5G band and cell locks need a paid version, and that 5G bands are missing on MediaTek.

[^cp-paid]: Users report that 5G band locks need the premium tier.

[^hal-bands]: Old Qualcomm radio HALs take at most 8 5G bands.

[^nr-mode]: On Qualcomm, Pixel and Samsung phones. Elsewhere bunnylock shows an EN-DC switch if Android offers one.

[^mtk-shizuku]: With Shizuku, only where the phone's RIL passes AT commands on. See [Tested devices](TESTED_DEVICES.md).

[^cp-nr-pci]: One user reports it failing on a OnePlus 12R.
