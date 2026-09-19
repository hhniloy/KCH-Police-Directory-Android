# KCH Police Directory - Android App

A simple Android application that displays the KCH Police Directory website in a native WebView.

## About

This app provides easy mobile access to the KCH Police Directory website (https://pdkch.netlify.app) through a lightweight native Android application.

## Features

- Native Android WebView implementation
- Loads https://pdkch.netlify.app
- Offline detection with retry functionality
- Back button navigation support
- Small APK size
- No unnecessary permissions
- No ads or tracking

## Technical Details

- **Package Name:** com.kchpolicedirectory.app
- **Minimum Android Version:** Android 8.0 (API 26)
- **Target Android Version:** Android 14 (API 34)
- **Version:** 1.0.0

## Building

This project uses GitHub Actions to automatically build release APKs. See `.github/workflows/build-apk.yml` for the build configuration.

## Permissions

- `INTERNET` - Required to load the website

## License

This app is a simple WebView wrapper for the KCH Police Directory website.
