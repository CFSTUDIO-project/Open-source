# Contributing to DAYFRAME

Thanks for taking an interest in DAYFRAME.

## Before opening a pull request

1. Open an issue for substantial changes so the approach can be discussed.
2. Keep each pull request focused on one concern.
3. Build the debug variant with JDK 17 and Gradle 8.10.2.
4. Verify the compact portrait layout and a wide / landscape layout.
5. Check that task and focus data still survive an app restart.
6. Describe what changed and how it was tested.

## Design principles

- Local-first: planner content stays on the device.
- Honest status: never display placeholder network or update state as live.
- Adaptive by window size: do not assume a fixed device model.
- Motion with purpose: animations should communicate changes or hierarchy.
- Accessible controls: use clear labels, adequate touch targets and readable contrast.

## Code style

Use Kotlin conventions, descriptive names, small composables and explicit state ownership. Avoid adding analytics, advertising, account systems or mandatory backend services.

## Reporting a bug

Include the Android version, device form factor, steps to reproduce, expected behavior and actual behavior. Remove personal information from logs and screenshots.
