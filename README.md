<h1 align=center>巡线</h1>

<p align="center">
    <a href=""><img alt="Version" src="https://img.shields.io/badge/version-1.0.3-green.svg"/></a>
    <a href="https://www.android.com/versions/lollipop-5-0/"><img alt="Android API Level" src="https://img.shields.io/badge/Android_API_Level-21-A4C639.svg"/></a>
</p>

<p align="center">
    线路巡查应用 for Android™<br>
    开发中
</p>

## Description
本应用体系由 Android 客户端及 FTP/FTPS 服务器端构成。

本项目当前开发重点为 Android 客户端，FTP/FTPS 服务器暂时通过 Python 及 [pyftpdlib](https://pypi.python.org/pypi/pyftpdlib/) 架设。

### 版本
Android 客户端现分为应用了 [Google Maps API](https://developers.google.com/maps/) 的 `Google Map 版` 和应用了[高德开放平台](http://lbs.amap.com)的 `AMap 版`。

两个版本目前平行开发，功能基本保持一致。

### 坐标系
* `Google Map 版` 主要使用 WGS-84 坐标系统，在中国大陆地区使用时地图显示可能存在较大偏移。服务器端应提供基于 WGS-84 坐标系统的 GPX 文件。
* `AMap 版` 使用经高德 API 处理的 GCJ-02 坐标系统，符合中华人民共和国相关规范及法律要求，地图显示正常。服务器端应提供基于 GCJ-02 坐标系统的 GPX 文件。

## Notice
* 编译 Android 客户端前请自行获取并加入 [Google API Key](https://developers.google.com/maps/documentation/android-api/signup) / [高德 API Key](http://lbs.amap.com/api/android-sdk/guide/create-project/get-key)
* 启动 FTPS 服务器前请自行生成证书文件，详情请参考源代码内注释。

## License
```
/*
 * Copyright (c) 2018 The Project Team
 *
 * All right reserved by the project team temporarily during development.
 * Please contact us for more details.
 *
 */
```
*Android 是 Google Inc. 的商标。*

## Dependencies
### Android App
* [Apache Commons Net](https://commons.apache.org/proper/commons-net/) - [License](http://www.apache.org/licenses/LICENSE-2.0)
* [Gson](https://github.com/google/gson) - [License](https://github.com/google/gson/blob/master/LICENSE)
* [Anko](https://github.com/Kotlin/anko) - [License](https://github.com/Kotlin/anko/blob/master/LICENSE)
* [Android Support Preference V7 Fix](https://github.com/Gericop/Android-Support-Preference-V7-Fix) - [License](https://github.com/Gericop/Android-Support-Preference-V7-Fix/blob/master/LICENSE)
* [FABProgressCircle](https://github.com/JorgeCastilloPrz/FABProgressCircle) - [License](https://github.com/JorgeCastilloPrz/FABProgressCircle#license)
* [iconmonstr](https://iconmonstr.com) - [License](https://iconmonstr.com/license/)
* [Google Maps API](https://developers.google.com/maps/) / [高德开放平台](http://lbs.amap.com)

### Server
* [pyftpdlib](https://pypi.python.org/pypi/pyftpdlib/) - [License](https://github.com/giampaolo/pyftpdlib/blob/master/LICENSE)
