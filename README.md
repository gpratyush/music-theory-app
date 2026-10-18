# music-theory-app

App to help me learn intervals, harmony and explore progressions.

## Layout

- `MusicTheory/` — Swift package with the theory engine (notes, intervals, quiz generation). Pure Swift, tested on Linux and macOS.
- `App/` — the SwiftUI iPhone app.
- `project.yml` — XcodeGen spec; the `.xcodeproj` is generated, not committed.

## Building

CI (GitHub Actions) runs the theory tests on Linux and builds the iOS app on a macOS runner for every PR.

Locally on a Mac:

```sh
brew install xcodegen
xcodegen generate
open MusicTheoryApp.xcodeproj
```

Theory tests only (any machine with Swift): `swift test --package-path MusicTheory`
