# OpenPnP 3点基準フィデューシャル付き自動フィーダー

OpenPnP `test` ブランチの `ReferenceAutoFeeder` を拡張した `ReferenceFiducialAutoFeeder` です。固定マーク `fid_A`（左手前）、`fid_B`（右手前）、`fid_C`（右奥のマシン原点）を上側カメラで測定し、3点から求めた XY アフィン変換をフィーダーの公称ピック位置に適用します。給材後の部品認識にも同じカメラを使用できます。

![OpenPnP 設定画面のイメージ](gui-preview.png)

## 収録内容

| ファイル | 用途 |
| --- | --- |
| `overlay/src/main/java/...` | フィーダー、3点変換、設定 GUI の Java ソース |
| `register-feeder.patch` | `ReferenceMachine` のフィーダー一覧への登録 |
| `openpnp-fiducial-auto-feeder-50dcdce.jar` | この端末にある OpenPnP 2.7 / `50dcdce` 専用の追加クラス |
| `Start-CustomOpenPnP.ps1` | 既存インストールを変更せず、追加 JAR を先に読み込んで起動 |
| `Apply-To-OpenPnP-Test.ps1` | OpenPnP `test` のソースへ変更を適用 |
| `docs/導入手順.md` | バイナリ版の起動、設定、試運転、ソースからのビルド |
| `gui-preview.svg` | GUI のイメージ図。数値は例であり実機の設定値ではありません |

## 対象版

- ソースの基準: [`openpnp/openpnp` の `test`](https://github.com/openpnp/openpnp/tree/test)、取得時のコミット `e6274b38f9d6f25e98677f75edde6c4bc7a9ee71`。
- 同梱 JAR の対象: この端末にインストールされている OpenPnP 2.7、`Implementation-Version: 2026-07-03_22-12-05.50dcdce`。起動スクリプトは異なる版を検出すると停止します。
- Java ソースは Java 11 互換でコンパイルしました。

## 動作概要

原点復帰後に自動測定を予約し、ジョブ開始時には使用するフィーダーを必ず再測定します。定期校正を有効にすると、指定間隔が過ぎたとき、待機中または次の給材前に再測定します。測定に失敗した場合は補正を無効にし、そのフィーダーのジョブ準備または給材をエラーで止めます。Z はアフィン変換せず、フィーダーに設定した値を使います。

部品認識は初期状態では無効です。部品に合わせてパイプラインを調整した後に有効にしてください。パイプラインは `RotatedRect` の `results` を返す必要があります。最も公称位置に近い検出結果の XY を使用し、許容ずれを超える場合はエラーにします。回転角は部品認識で変更しません。

同じ3点を複数のフィーダーで使用する場合、各フィーダーに同じ基準点と Part ID を設定します。ジョブ準備では各フィーダーが個別に3点を再測定します。

## 検証

- 3点変換の単体テスト: `ThreePointAffineTest passed`。
- OpenPnP 2.7 `50dcdce` のインストール済み JAR と依存 JAR をクラスパスにして、追加クラスおよび登録済み `ReferenceMachine` を `javac --release 11` でコンパイルしました。
- 上書き JAR が元の JAR より先に読み込まれることを `BinaryOverlaySmokeTest` で確認しました。
- カメラと実機を使った認識精度・動作確認は未実施です。初回は低速で、ノズルを安全な高さにして試運転してください。

## ライセンス

追加ソースは OpenPnP と同じ GPL-3.0-or-later として提供します。`ReferenceMachine` の変更は元の OpenPnP の GPL に従います。
