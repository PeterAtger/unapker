# Play Console copy for Unapker

## Store listing

**App name** (30 max)
Unapker: XAPK & APKS Installer

**Short description** (80 max)
Tap an .xapk, .apks or .apkm file and it installs. No ads, no account.

**Full description**
Downloaded an app as an .xapk, .apks or .apkm and Android won't open it? Unapker will.

Tap the file in any file manager, pick Unapker, and confirm the install. That's it.

What it does
• Installs .xapk files from APKPure and similar sites, including game data (OBB)
• Installs .apks files from SAI and split-APK backups
• Installs .apkm files from APKMirror (unencrypted ones)
• Installs plain .apk files too
• Only installs the parts made for your phone, so nothing extra takes up space
• Shows the real app name, icon and version before it installs

What it doesn't do
• No ads
• No account
• No internet access at all. Your files never leave your phone.

Encrypted .apkm files can only be opened by APKMirror Installer. Unapker tells you when you've got one.

Open source: https://github.com/PeterAtger/unapker

**Category:** Tools
**Tags:** File manager / Tools
**Privacy policy URL:** https://github.com/PeterAtger/unapker/blob/main/PRIVACY.md

## App content forms

**Ads:** No, the app does not contain ads.

**Data safety:**
- Does your app collect or share any of the required user data types? **No**
- (Everything else follows from that: no data collected, no data shared.)

**Target audience:** 18+ (simplest for a sideloading tool)

**Content rating questionnaire:** category "Utility, productivity, communication, or other"; answer No to everything.

**Government app / financial features / health:** No.

## Sensitive permission declaration: REQUEST_INSTALL_PACKAGES

Core functionality: installing app packages the user chooses is the app's only purpose.

Suggested text:
> Unapker is a package installer. Its only function is to install split-APK bundles (.xapk, .apks, .apkm) and APK files that the user explicitly opens or picks from their device storage. It reads the selected file, extracts the APKs that match the device, and passes them to Android's PackageInstaller, where the user confirms every install in the system dialog. The app does not download anything (it has no internet permission), never installs anything the user didn't select, and never updates itself outside Google Play.

Video demo (often requested): screen-record opening an .xapk from the file manager through to "Installed!".
