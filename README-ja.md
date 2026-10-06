# OpenPnP 用 3 点フィデューシャル自動フィーダー

**言語:** [English](README.md) | 日本語

`ReferenceFiducialAutoFeeder` は OpenPnP の `ReferenceAutoFeeder` を拡張したフィーダーです。マシン共通のカタログに、再利用できるフィデューシャル座標を保存します。設置面ごとに保存済みマークを `fid_A`、`fid_B`、`fid_C` に割り当て、同じ設置面を選んだすべてのフィーダーがその3点を共有します。上側カメラの実測値から求めたアフィン変換で、各フィーダーの公称ピック位置の XY を補正します。必要に応じて、同じカメラによる部品認識でも XY を補正できます。

![Configuration タブのイメージ](gui-preview-configuration.png)

![Calibration タブのイメージ](gui-preview.png)

**Configuration** タブには OpenPnP 標準の `ReferenceAutoFeeder` 設定画面を使用します。Part、Feed/Pick Retry Count、公称ピック位置 X/Y/Z/Rotation、Feed と Post Pick のアクチュエータ・値・テストボタン、**Move before feed**、**Recycle supported** を残しています。**Calibration** タブには位置補正関連の設定をまとめました。設置面を選び、3つのドロップダウンで保存済みマークを割り当てます。X/Y 欄には OpenPnP 標準のカメラ移動・座標取得ボタンを使います。**Save new** は取得座標を保存し、**Update** は選択中の共有マークを更新し、**Delete** はそのマークを参照する全設置面から削除します。**Use machine origin (X=0, Y=0)** をチェックすると `fid_C` をマシン座標原点に固定し、その選択とカメラ操作を無効にします。画像中の数値は例であり、実機の設定値ではありません。追加した GUI の表示言語は英語です。

## ファイル

| パス | 内容 |
| --- | --- |
| `overlay/src/main/java/...` | フィーダー、共有フィデューシャルカタログ、3点変換、設定画面のソース |
| `register-feeder.patch` | `ReferenceMachine` に新しいフィーダー型を登録するパッチ |
| `openpnp-fiducial-auto-feeder-50dcdce.jar` | OpenPnP 2.7 の `50dcdce` ビルド用の追加クラス |
| `Start-CustomOpenPnP.ps1` | 追加 JAR をクラスパスの先頭に置いて対象バイナリを起動するスクリプト |
| `Apply-To-OpenPnP-Test.ps1` | OpenPnP `test` のソースへ変更を適用するスクリプト |
| `.github/workflows/artifacts.yml` | 手動で `test` の基準版をビルドし、実行用の成果物をアップロードするワークフロー |
| `README.md` | 英語版 README |
| `docs/installation-guide-en.md` / `docs/installation-guide-ja.md` | 英語・日本語の導入、設定、試運転手順 |
| `gui-preview-configuration.svg` / `.png`、`gui-preview.svg` / `.png` | Configuration と Calibration タブの GUI イメージ |

## 部品認識モード

**Recognize the fed part with the top camera** を有効にし、次のモードから選びます。

| モード | 動作 |
| --- | --- |
| **Every feed** | 給材のたびに提示済み部品を認識します。物理的な給材をスキップした場合も認識します。初期実装の動作です。 |
| **First feed in job** | OpenPnP がジョブ用にこのフィーダーを準備した後、最初の給材時だけ認識します。同じジョブの後続の給材では、3点補正済みの公称位置を使います。 |
| **Manual only** | 給材中には自動認識しません。部品を提示した後、ピック前に **Locate part now** を実行します。連続ジョブはこの操作を待って自動停止しないため、手動操作またはステップ実行で使います。 |

部品認識は初期状態では無効です。付属のパイプラインは OpenPnP の `ReferenceLoosePartFeeder` 用です。認識を有効にする前に、対象部品、照明、背景に合わせて調整してください。`RotatedRect` の結果が必要です。設定した距離の範囲内で最も近い検出結果を XY に使い、設定済みの Z と3点補正した公称回転は維持します。

## 校正の動作

共有カタログは `machine.xml` のマシンプロパティとして保存されます。マークの表示名と保存名は mm 単位・小数3桁の `Fid_<X>_<Y>` 形式で、例は `Fid_-250.000_-200.000` です。**Update** で座標を変えると名前も自動で変わります。参照には固定の内部 ID を使用するため、名前が変わっても設置面からの参照は維持されます。小数3桁に丸めた X/Y が同じマークは重複登録できません。新しいフィーダーは既存の最初の設置面を選びます。旧版でフィーダーごとに保存した座標は、設定画面を開くか校正を実行すると、共有マークと対応する設置面に移行されます。

各設置面には名前と A/B/C の割当があります。同じ面のフィーダーは `fid_A` と `fid_B` を含む3点すべてを共有します。別の A/B/C の組には新しい設置面を作成します。マークを更新すると、参照しているすべての設置面・フィーダーに反映され、その校正は無効になります。マークを削除すると割当も解除されるため、ジョブを再開する前に代わりのマークを選びます。設置面を削除した場合も、その面のフィーダーは別の面を選ぶまで使用できません。設置面とカタログの変更はメモリ上ですぐに有効になります。再起動後も保持するには OpenPnP のマシン設定を保存してください。

認識に使う `Fiducial Part` は OpenPnP の Parts 一覧から選びます。フィデューシャルの撮像には上側カメラの Default Z を使用し、フィデューシャルの Z と Rotation は指定も使用もしません。設置面ごとの **Use machine origin** で `fid_C` の有効座標を X=0、Y=0 にできます。チェック中もカスタムマークの割当は保持されます。物理マークがマシン座標原点以外にある場合はチェックを外してください。ピック位置には XY 変換のみを適用します。

原点復帰後に校正を予約し、ジョブ開始時には使用する各フィーダーを再校正します。定期校正を有効にした場合は、間隔が過ぎ、マシンが待機中のとき、または次の給材前に実行します。マークを検出できない場合や許容範囲を超えた場合は古い変換を無効にし、成功するまでそのフィーダーの準備・給材を停止します。共有マークも現行実装では各フィーダーが個別に撮像します。

## 対象ビルドと導入

- ソースの基準: [`openpnp/openpnp` の `test`](https://github.com/openpnp/openpnp/tree/test)、コミット `e6274b38f9d6f25e98677f75edde6c4bc7a9ee71`。
- 同梱の追加 JAR: マニフェストの `Implementation-Version` が `2026-07-03_22-12-05.50dcdce` の OpenPnP 2.7 バイナリ用です。起動スクリプトは異なる版を拒否します。
- Java ソースと追加 JAR の対象は Java 11 です。

一致するバイナリをプログラムファイルの変更なしで起動するには、次を実行します。

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\Start-CustomOpenPnP.ps1 -InstallDir 'C:\Program Files\OpenPnP'
```

マシン設定のバックアップ、フィデューシャル Part、フィーダー設定、3種類の認識モード、試運転については[日本語の導入手順](docs/installation-guide-ja.md)または[英語の導入手順](docs/installation-guide-en.md)を参照してください。別のバイナリ版には、対応するソースへ変更を適用し、その版に合う JAR をビルドしてください。

ソースからビルドした OpenPnP 一式が必要な場合は、GitHub の **Actions → Artifacts → Run workflow** を手動で実行してください。手順書に記載した `test` の基準コミットにカスタムフィーダーを適用し、JAR と `lib` をダウンロード可能な成果物にします。push 時には自動実行されません。

## 検証と制限

- `ThreePointAffineTest` は、3点の座標変換と一直線上のマークの拒否を確認しています。
- フィーダー、設定画面、登録済み `ReferenceMachine` は、インストール済み OpenPnP 2.7 の JAR とライブラリを使って `javac --release 11` でコンパイルできています。
- `BinaryOverlaySmokeTest` は追加 JAR の優先読み込み、新しいフィーダー型の登録、共有マークと設置面のシリアライズ、GUI の選択・原点操作を確認しています。
- カメラ認識精度と実機の動作は未検証です。

追加ソースのライセンスは、OpenPnP と同じ GPL-3.0-or-later です。
