# Android TV Support

This document details the Android TV compatibility and UI enhancements introduced in Xhizuku.

## Overview

Xhizuku provides a first-class experience on Android TV devices, featuring a dedicated UI built with **Material 3 Expressive** components tailored for large screens and 10-foot interactions.

## Material 3 Expressive UI for TV

The Android TV interface is not a scaled version of the phone UI. It is a native implementation using Jetpack Compose for TV:
- **Large-screen layout**: Optimized for 16:9 aspect ratios.
- **Enhanced Typography**: High-legibility fonts and sizes suitable for viewing from a distance.
- **TV-specific components**: Uses `androidx.tv.material3` components, including TV-optimized buttons, cards, and navigation surfaces.

## Monet (Dynamic Color) Support

On devices running Android 12 (SDK 31) and higher, Xhizuku supports **Monet (Dynamic Color)**:
- The UI automatically extracts colors from the user's wallpaper or system theme.
- This ensures visual consistency with the Android TV system UI and other modern TV applications.
- Users can toggle Dynamic Color in the Settings menu.

## Black Night Theme

Xhizuku includes a dedicated **Black Night Theme** optimized for OLED and high-contrast viewing:
- Uses true black (`#000000`) backgrounds.
- Reduces eye strain in dark environments.
- Improves contrast for better legibility on various TV panel types.

## Focus Management and D-pad Navigation

The UI is designed for remote control interaction:
- **Predictable Focus**: Logical focus movement between elements using the D-pad.
- **Visual Focus Cues**: Clear, high-contrast focus rings and scale animations on focused items.
- **Click Handling**: All interactive elements are mapped to the D-pad "Select" or "OK" button.
- **Back Button**: Standardized back navigation using the remote's back button.

## TV-Optimized Dialogs

Dialogs in **Settings** and **Modules** have been redesigned for TV:
- **Center-aligned**: Dialogs appear in the center of the screen for better visibility.
- **Focus Locking**: Focus is trapped within the dialog until it is dismissed.
- **Scrollable Content**: Long module descriptions or logs are easily scrollable using the D-pad.
- **Confirmation Actions**: Clear "Confirm" and "Cancel" buttons with distinct focus states.

## Package Identity

All TV features respect the Xhizuku package identity:
- Package Name: `xeonleon.xhizuku`
- Shared User ID: `xeonleon.xhizuku.uid` (if applicable)

## Verification

The TV UI has been verified on:
- Android TV 16 emulator*
