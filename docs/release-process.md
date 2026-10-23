# Branches and releases

- **dev**: all development. Every build sent for testing comes from here.
- **main**: only builds the project owner has approved after using them on a real device.
  Nothing is merged into main without that approval.

When a build is approved:

1. Bring main to the approved commit (fast-forward when possible).
2. The push to main runs `.github/workflows/release.yml`: unit tests, lint and an R8-optimized
   release build, then a GitHub release tagged `v` + the app's versionName (e.g. `v0.4.6`), with
   the APK attached and notes generated from the commits. The workflow creates the tag itself.

The APK is signed with the committed debug key (see the README), so releases install over each
other and over test builds. A real signing key is needed before publishing to a store.
