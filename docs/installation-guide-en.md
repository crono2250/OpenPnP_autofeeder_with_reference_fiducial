# Installing the custom feeder in the OpenPnP 2 binary

**Language:** English | [日本語](installation-guide-ja.md)

## 1. Check the supported build and back up the configuration

The bundled overlay JAR targets OpenPnP 2.7 build `50dcdce`. Use it when `META-INF/MANIFEST.MF` in `C:\Program Files\OpenPnP\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar` reports `Implementation-Version: 2026-07-03_22-12-05.50dcdce`. The launcher checks this version.

Close OpenPnP and copy its configuration directory, normally `.openpnp2` in your user directory, to another location. Back up `machine.xml` before changing existing feeders or jobs.

## 2. Launch without modifying the installed binary

Open PowerShell in this repository's directory and run:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\Start-CustomOpenPnP.ps1 -InstallDir 'C:\Program Files\OpenPnP'
```

The script places the overlay JAR before the standard JAR and `lib` on the classpath, then starts OpenPnP with its bundled Java. It does not modify `OpenPnP.exe` or installed program files. The new feeder type is available only when OpenPnP is started this way. If the launcher reports a version mismatch, **do not use this overlay JAR with that build**. Build an overlay for the matching source revision as described in section 6.

## 3. Prepare a Part for fiducial recognition

1. Calibrate the top camera's focus, height, and units per pixel using the normal OpenPnP procedure.
2. In `Parts`, create a Part representing the fixed mark. If all three marks have the same shape, they can share one Part ID. For a circular mark, configure a Package with a circular footprint matching its actual dimensions.
3. Enable that Part's Fiducial Vision Settings. Adjust its pipeline until the top camera detects each of the three marks reliably. Feeder calibration calls OpenPnP's standard Fiducial Locator.
4. Make sure the camera can see `fid_C` after homing. If the machine coordinate origin is outside the camera's travel area, use a reachable fixed mark at the rear right for `fid_C` and save that mark's actual nominal coordinates.

## 4. Configure the feeder

1. In `Feeders`, click `New` and add a `ReferenceFiducialAutoFeeder`. Existing `ReferenceAutoFeeder` instances can remain in place.
2. As with a standard auto feeder, configure the Part, feed actuator, optional Post Pick actuator, and the nominal pick location X/Y/Z/rotation. This is the position before correction.
3. Select an `Installation surface`. The first feeder creates `Surface 1`; later new feeders initially join the first existing surface. Use `New surface` for another installation face and `Rename` to change its name. Feeders assigned to the same surface share the selections for `fid_A`, `fid_B`, and `fid_C`. If you use `Delete surface`, its feeders cannot calibrate until another surface is selected.
4. Set the top camera's Default Z. Move the camera to each mark and use the capture button in `Camera controls`. The X/Y fields are populated by the camera and cannot be typed into. Click `Save new` to add a mark to the shared catalog. Its name is generated from the XY coordinates in millimeters with three decimals, for example `Fid_-250.000_-200.000`. In the three dropdowns, assign saved marks to `fid_A` at the front left, `fid_B` at the front right, and `fid_C` at the rear right. Use the same machine coordinate system for all three points, and ensure that they are not collinear. Fiducial Z and Rotation are not entered.
5. To change a saved mark's coordinates, select it in a dropdown, capture the new camera position, and click `Update`. Its displayed name changes with its coordinates, while its internal reference ID remains the same. Every surface and feeder using that mark receives the new position. `Delete` removes a mark from the shared catalog and clears its assignment on every surface. Select a replacement before resuming a job. These changes take effect immediately; save the OpenPnP machine configuration to retain them after a restart.
6. On a surface where `fid_C` is at machine coordinate X=0, Y=0, check `Use machine origin (X=0, Y=0)`. The `fid_C` dropdown, X/Y fields, and camera controls become disabled, and calibration uses the origin coordinates. If the physical mark is elsewhere, leave it unchecked and choose a saved mark. The custom mark selection is retained while the box is checked.
7. Select the Part from section 3 in the `Fiducial Part` dropdown. Adjust the maximum mark shift and maximum scale/shear change for your machine. Their initial values are 3 mm and 1%, respectively.
8. After homing, click `Calibrate now` and check the log and last calibration status. Calibration is also queued after homing. At job start, each feeder used by the job is recalibrated. Even when marks are shared, the current implementation measures all three points separately for each feeder.
9. To compensate for temperature drift, enable `Enable periodic calibration` and set the interval in minutes. The initial interval is 5 minutes. An idle timer or the next feed detects an expired interval and starts calibration. Camera movement adds operating time.

## 5. Correct part position with the top camera

1. First confirm with normal feeding and three-point correction alone that the nozzle reaches the nominal pick position safely.
2. Open `Edit part pipeline` and adapt it to the part's shape, lighting, and background. The initial pipeline comes from OpenPnP's `ReferenceLoosePartFeeder`. Its final `results` must be a list of `RotatedRect` values.
3. Enable `Recognize the fed part with the top camera` and choose a `Recognition mode`:

   | Mode | Behavior |
   | --- | --- |
   | `Every feed` | Recognizes the part on every feed. It also recognizes a part that is already presented when physical feeding is skipped. |
   | `First feed in job` | Recognizes only the first feed after this feeder is prepared for the job. Later feeds use the three-point corrected nominal position. |
   | `Manual only` | Does not recognize automatically during feed. After presenting a part and before picking it, click `Locate part now`. A continuous job does not pause automatically for this action; use manual or step operation. |

4. During recognition, the camera moves to the corrected nominal location and selects the nearest detected part. A result farther than `Maximum part shift` (initially 1 mm) causes an error. A manual recognition result is discarded on the next feed, so repeat it for a new part.
5. Run a single job at low speed and verify the detected image point and pick location before normal operation.

## 6. Build your own version from the `test` branch

The source baseline is commit `e6274b38f9d6f25e98677f75edde6c4bc7a9ee71` of OpenPnP's `test` branch. For another binary build, obtain its corresponding OpenPnP source, apply the registration patch and added source files, then build with Maven:

```powershell
git clone --branch test https://github.com/openpnp/openpnp.git openpnp-test
git -C .\openpnp-test checkout e6274b38f9d6f25e98677f75edde6c4bc7a9ee71
.\Apply-To-OpenPnP-Test.ps1 -OpenPnPCheckout .\openpnp-test
Set-Location .\openpnp-test
mvn -DskipTests package
```

Launch the resulting `target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar` together with `target\lib` from a separate test directory. Do not overwrite a binary installation with a JAR from a different build. OpenPnP has no general plugin registration mechanism for this feeder, so the source patch adds one entry to `ReferenceMachine`'s feeder class list.

## Scope of correction and failure behavior

An XY affine transform is calculated from the three nominal and measured positions. It compensates for translation, rotation, different X/Y scales, and shear. Fiducial recognition uses the top camera's Default Z; the pick position's Z is not transformed. The feeder checks each mark's shift, scale/shear limits, and detected part offset. If a mark cannot be detected or a limit is exceeded, the previous correction is invalidated and the feeder stops until calibration succeeds.

Fiducial coordinates and the A/B/C assignments for each installation surface are shared machine settings. Each feeder stores its chosen surface, calibration result, and Fiducial Part. Coordinates saved per feeder by an older version are migrated into a shared catalog when its configuration panel is first opened or calibration runs. Save the machine configuration after migration.
