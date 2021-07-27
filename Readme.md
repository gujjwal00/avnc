
<p align="center"><img src="metadata/en-US/branding/wordmark.svg" alt="AVNC Banner" height="200"></img></a>


<p align="center"> <b>AVNC</b> is a VNC client for Android. </p>

-------------------------------------------------------------------------------


### Features
- Material Design (with Dark theme)
- Configurable gestures
- Tight encoding
- Virtual Keys
- Picture-in-Picture mode
- View-only mode
- Zeroconf Server Discovery
- TLS support (AnonTLS, VeNCrypt)
- SSH tunnel (VNC over SSH)
- Import/Export servers
- VNC Repeater support
- Clipboard Sync with server


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
git clone git@github.com:gujjwal00/avnc.git
cd avnc
git submodule update --init --depth 1
```

Now you can import the project in Android Studio, or build it directly from terminal.

Read [Architecture.kt](app/src/main/java/com/gaurav/avnc/Architecture.kt) (preferably in
Android Studio) to know more about the code.


-------------------------------------------------------------------------------

To reduce build time during development, Libjpeg-turbo and LibreSSL are **not** 
included in default build types. 

Thus, the Tight encoding, and security types based on TLS, will not work in such builds.
If you need these features, you can create a full build.

Before creating a full build, you have to prepare the LibreSSL source tree:

1. Make sure following packages are installed: automake, autoconf, git, libtool, perl
2. Run the following commands from root of the project:
   ```bash
   cd extern/libressl/
   ./autogen.sh
   ```

Now, select an appropriate build type (`releaseAll`/`debugAll`) to create full build.
See [build.gradle](app/build.gradle) for available build types.

-------------------------------------------------------------------------------
