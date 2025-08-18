
<p align="center"><img src="metadata/en-US/branding/wordmark.svg" alt="AVNC Banner" height="200"></img></a>


<p align="center"> <b>AVNC</b> is a VNC client for Android. </p>

-------------------------------------------------------------------------------


### Features
- Material Design (with Dark theme)
- Configurable gestures
- Virtual Keys
- VNC Repeater support
- Wake-on-LAN support
- Built-in SSH tunnel (VNC over SSH)
- Picture-in-Picture mode
- View-only mode
- No-video mode
- Automatic Server Discovery (Zeroconf)
- Import/Export servers
- Clipboard Sync with server
- `vnc://` URI support
- TLS support (AnonTLS, VeNCrypt)
- Tight encoding support


[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.gaurav.avnc/)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Play Store" height="80">](https://play.google.com/store/apps/details?id=com.gaurav.avnc)

### Screenshots

[<img src="metadata/en-US/images/phoneScreenshots/1.jpg" width="250">](metadata/en-US/images/phoneScreenshots/1.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/2.jpg" width="250">](metadata/en-US/images/phoneScreenshots/2.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/3.jpg" width="250">](metadata/en-US/images/phoneScreenshots/3.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/4.jpg" width="250">](metadata/en-US/images/phoneScreenshots/4.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/5.jpg" width="250">](metadata/en-US/images/phoneScreenshots/5.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/6.jpg" width="250">](metadata/en-US/images/phoneScreenshots/6.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/7.jpg" width="380">](metadata/en-US/images/phoneScreenshots/7.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/8.jpg" width="380">](metadata/en-US/images/phoneScreenshots/8.jpg)

  
Development
===========

Tools required:

- Git 
- Android Studio
- Android SDK
- NDK (with CMake)

To get started, simply clone the repo and initialize submodules:

```bash
git clone https://github.com/gujjwal00/avnc.git
cd avnc
git submodule update --init --depth 1
```

Now you can import the project in Android Studio, or build it directly from terminal.

Read [Architecture.kt](app/src/main/java/com/gaurav/avnc/Architecture.kt) (preferably in
Android Studio) to know more about the code.


##
You can translate AVNC on [Weblate](https://hosted.weblate.org/engage/avnc/).

[<img src="https://hosted.weblate.org/widgets/avnc/-/open-graph.png" alt="Translation status" height="200" />](https://hosted.weblate.org/engage/avnc/)


## Credits

- Author of libraries AVNC depends on (LibVNCClient, libjpeg-turbo, wolfSSL, sshlib, leakcanary etc.)
- Contributors for reporting issues, providing fixes
- Contributors for translating AVNC, and [Weblate](https://weblate.org/) for translation hosting
- [Browserstack](https://www.browserstack.com/) for providing testing infrastructure