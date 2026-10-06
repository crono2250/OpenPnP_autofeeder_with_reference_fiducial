# Three-fiducial auto feeder for OpenPnP

`ReferenceFiducialAutoFeeder` extends OpenPnP's `ReferenceAutoFeeder`. It measures three fixed machine fiducials with the top camera: `fid_A` at the front left, `fid_B` at the front right, and `fid_C` at the rear right machine origin. An affine transform derived from those measurements corrects the feeder's nominal XY pick position. Optional part recognition uses the same camera to refine the XY position of a presented part.

![Configuration UI preview](gui-preview.png)

The preview illustrates the custom feeder panel. Its fiducial fields use OpenPnP's standard X/Y/Z/Rotation location controls and capture/move buttons. The values shown are examples, not machine settings. All labels added by this feeder are in English.

## Files

| Path | Purpose |
| --- | --- |
| `overlay/src/main/java/...` | Feeder, three-point transform, and configuration wizard source |
| `register-feeder.patch` | Registers the new type in `ReferenceMachine` |
| `openpnp-fiducial-auto-feeder-50dcdce.jar` | Overlay classes for OpenPnP 2.7 build `50dcdce` |
| `Start-CustomOpenPnP.ps1` | Starts that binary with the overlay JAR first on the classpath |
| `Apply-To-OpenPnP-Test.ps1` | Applies the source changes to an OpenPnP `test` checkout |
| `docs/installation-guide-ja.md` | Detailed installation, setup, and trial-run instructions in Japanese |
| `gui-preview.svg` / `gui-preview.png` | Editable UI concept and rendered image |

## Part recognition modes

Enable **Recognize the fed part with the top camera**, then select one of the following modes:

| Mode | Behavior |
| --- | --- |
| **Every feed** | Recognizes the presented part after each feed, including a skipped physical feed. This was the behavior of the first implementation. |
| **First feed in job** | Recognizes once on the first feed after OpenPnP prepares this feeder for a job. Later feeds in that job use the three-point corrected nominal position. |
| **Manual only** | Does not recognize during feed. Use **Locate part now** after presenting a part and before picking it. Continuous jobs do not pause automatically for this action; use manual or step operation. |

Part vision is initially disabled. The supplied pipeline is OpenPnP's `ReferenceLoosePartFeeder` pipeline; adapt it to the actual part, lighting, and background before enabling recognition. It must return `RotatedRect` results. The closest result within the configured distance limit supplies XY. The feeder retains the configured Z and corrected nominal rotation.

## Calibration behavior

Each fiducial has a nominal X/Y/Z/Rotation location entered with OpenPnP's location controls. The configured fiducial Part is selected from a dropdown backed by OpenPnP's Parts list. Detection uses `ReferenceFiducialLocator`, so the fiducial's Z and rotation are used for camera positioning and vision setup. Only XY is transformed for the pick location.

Calibration is queued after homing and performed again for each feeder used at job start. If periodic calibration is enabled, it runs when its interval has elapsed and the machine is idle, or before the next feed. A failed or out-of-limit measurement invalidates the previous transform and prevents that feeder from preparing or feeding until calibration succeeds. Multiple feeders are calibrated independently.

## Supported builds and installation

- Source baseline: [`openpnp/openpnp` `test`](https://github.com/openpnp/openpnp/tree/test) at `e6274b38f9d6f25e98677f75edde6c4bc7a9ee71`.
- Bundled overlay JAR: the installed OpenPnP 2.7 binary with manifest `Implementation-Version: 2026-07-03_22-12-05.50dcdce`. The launcher rejects another build.
- Java source and the overlay JAR are compiled for Java 11.

To use the matching installed binary without modifying its program files:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\Start-CustomOpenPnP.ps1 -InstallDir 'C:\Program Files\OpenPnP'
```

Follow [the installation guide](docs/installation-guide-ja.md) for machine backup, fiducial Part setup, feeder configuration, the three recognition modes, and cautious trial runs. For a different OpenPnP binary, apply the source patch to the corresponding source revision and build a matching version.

## Verification and limits

- `ThreePointAffineTest` passes, including the three-point mapping and rejection of collinear marks.
- The added feeder, wizard, and registered `ReferenceMachine` compile against the installed OpenPnP 2.7 JAR and libraries with `javac --release 11`.
- `BinaryOverlaySmokeTest` confirms that the overlay classes take precedence and that OpenPnP lists the new feeder type.
- Camera recognition accuracy and machine motion have not been tested on hardware.

The added source is GPL-3.0-or-later, matching OpenPnP's license.
