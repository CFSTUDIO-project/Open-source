# Release process

DAYFRAME's in-app updater reads the public GitHub Releases feed and looks for an APK asset attached to the latest published release.

## Maintainer release steps

1. Let the Android CI workflow complete successfully on the default branch at least once. This initializes and stores the repository's distribution signing identity in the private GitHub Actions cache.
2. Update the version in the project changelog and source as appropriate.
3. Create and push a semantic version tag such as v1.0.1.
4. The Publish Android Release workflow restores the existing signing identity, builds the release APK, and attaches it to a GitHub Release.
5. Open the release page and verify the APK asset before announcing it.

The release workflow deliberately fails rather than silently generating a new signing key. Replacing the signing identity would prevent existing installations from accepting future APK updates.

## Signing identity

The maintained repository keeps the CI distribution signing file in a repository-scoped GitHub Actions cache rather than committing a keystore or password to the public source tree. The weekly CI schedule refreshes that cache while the workflow remains enabled.

This is a lightweight open-source project setup, not a recommended enterprise key-management service. For production distribution, maintainers should migrate the signing identity to protected GitHub Actions secrets or another dedicated signing service and update the release workflow accordingly. Never publish a private release keystore in the source repository.

## Forks

A fork does not inherit the original repository's private Actions cache. Run its own successful CI build to create a separate signing identity before publishing a release. Apps signed by different identities cannot be installed as in-place updates over one another.
