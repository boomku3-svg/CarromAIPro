# Carrom AI Pro — Android source project

An original Kotlin/Android starter project for screenshot-based carrom analysis, manual board correction, direct-shot geometry, one-cushion virtual-pocket candidates, and a basic rolling/collision physics library.

## What is included

- Android app UI with gallery screenshot import.
- Android MediaProjection one-frame screen capture flow (Android asks the user for permission).
- Offline board-boundary estimate and simple contrast-blob suggestions.
- Manual striker placement, manual coin placement/removal.
- Six-pocket board geometry, ghost-ball contact point, blocker checks.
- Direct and one-cushion bank candidate planner.
- Vector mathematics, line/circle intersection, impulse collision model, numerical trajectory integration.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.

## Build the APK using only your phone

1. Create/sign in to GitHub.
2. Create a new repository named `CarromAIPro`.
3. Extract this ZIP on your phone.
4. Upload the **contents** of this folder to the repository root (not the outer folder).
5. Open the repository → **Actions** → **Build Carrom AI Pro APK** → **Run workflow**.
6. Wait for the green check mark. Open the completed workflow run.
7. Under **Artifacts**, download `Carrom-AI-Pro-debug-apk`, extract it, then install `app-debug.apk`.
8. If Android asks, allow your browser/file manager to install unknown apps.

The workflow uses Gradle 8.9-compatible Android Gradle Plugin 8.7.3. It builds a debug APK; a release APK requires signing configuration.

## Important accuracy limits

This is a functional starting project, not a trained computer-vision model and not a certified game solver. Auto detection is heuristic and will vary with game graphics, scaling, shadows, compression and board theme. Always verify the board bounds, striker and coin centers manually. Physics values (mass, restitution, friction, spin and cushion behavior) are approximations and must be calibrated against the specific game. The score is a geometry ranking, **not a percentage chance of pocketing**.

Live capture uses Android's screen-sharing permission and captures one frame after permission is granted. Some Android/game combinations may block capture or require retrying. It does not bypass game protections.

## Controls

- **Gallery screenshot**: choose a screenshot.
- **Live capture**: grant Android screen capture permission; switch to the carrom game and wait for the captured frame.
- **Auto detect**: estimate board and possible disc blobs.
- **Set striker**: tap the striker center.
- **Add coin**: tap each target coin center.
- **Remove nearest**: tap a mistaken detected disc.
- **Calculate best shots**: rank direct and one-cushion candidates.
- **Next best shot**: cycle through ranked candidates.

## Project layout

- `app/src/main/java/com/carromaipro/CarromEngine.kt` — vectors, geometry, physics, planner.
- `BoardDetector.kt` — offline image heuristics.
- `AimOverlayView.kt` — screenshot and path drawing.
- `MainActivity.kt` — UI, screenshot import, manual correction and calculations.
- `CaptureService.kt` — MediaProjection screenshot capture.
- `.github/workflows/android.yml` — cloud APK build.

## Next engineering steps

1. Add drag-to-adjust handles and explicit pocket calibration.
2. Train/test a coin detector on screenshots from the exact game.
3. Add spin, striker baseline constraints, queen rules and collision-chain simulation.
4. Calibrate physics by recording known shots and fitting friction/restitution parameters.
5. Add instrumented tests and release signing.
