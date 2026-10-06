# バイナリ版 OpenPnP 2 への導入手順

## 1. 対象版とバックアップを確認

同梱の追加 JAR は OpenPnP 2.7 の `50dcdce` 向けです。`C:\Program Files\OpenPnP\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar` の `META-INF/MANIFEST.MF` にある `Implementation-Version` が `2026-07-03_22-12-05.50dcdce` の場合に使用できます。起動スクリプトが版を検証します。

OpenPnP を終了し、OpenPnP の設定ディレクトリ（通常はユーザーの `.openpnp2`）を別の場所にコピーしてください。既存フィーダーやジョブを変更する前に、`machine.xml` もバックアップしてください。

## 2. 既存バイナリを変更せず起動

PowerShell を開き、本リポジトリのフォルダーで次を実行します。

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\Start-CustomOpenPnP.ps1 -InstallDir 'C:\Program Files\OpenPnP'
```

このスクリプトは追加 JAR、標準 JAR、`lib` の順にクラスパスを設定し、OpenPnP の同梱 Java で起動します。既存の `OpenPnP.exe` とプログラムファイルを書き換えません。OpenPnP をこの方法で起動したときだけ、新しいフィーダーが選択できます。起動時に版不一致のメッセージが出た場合は、**その版に追加 JAR を流用しないでください**。後述のソース適用手順で対応版をビルドします。

## 3. フィデューシャル認識用 Part を用意

1. 上側カメラを焦点、高さ、ピクセル寸法まで通常の OpenPnP 手順で校正します。
2. `Parts` で固定マークを表す Part を作成します。3点が同じ形なら1つの Part ID を共有できます。円形マークの場合、実寸に合った円形フットプリントを持つ Package を設定します。
3. その Part の Fiducial Vision Settings を有効にし、上側カメラで3点それぞれが安定して認識できるようにパイプラインを調整します。フィーダー校正は OpenPnP 標準の Fiducial Locator を呼び出します。
4. `fid_C` を原点復帰後に撮像できることを確認します。マシン座標の原点そのものがカメラの可動範囲外なら、撮像可能な右奥の固定マークを `fid_C` とし、そのマークの実際の公称座標を入力します。

## 4. フィーダーを設定

1. `Feeders` で `New` を押し、`ReferenceFiducialAutoFeeder` を追加します。既存の `ReferenceAutoFeeder` はそのまま残せます。
2. 通常の自動フィーダーと同様に、Part、フィード用アクチュエータ、必要に応じた Post Pick アクチュエータ、ピック位置 X/Y/Z/回転を設定します。このピック位置は補正前の公称値です。
3. `fid_A` 左手前、`fid_B` 右手前、`fid_C` 右奥の公称位置を、OpenPnP 標準の X/Y/Z/Rotation 欄と位置操作ボタンで入力します。カメラを各マークの中心・撮像高さに動かして座標取得ボタンを使えます。X/Y は同じマシン座標系とし、3点が一直線にならないようにします。Z と Rotation はフィデューシャルの撮像・認識に使い、ピック位置の Z は変換しません。GUI イメージの数値は例です。
4. `Fiducial Part` のドロップダウンから手順3で作成した Part を選びます。マークの最大移動量と倍率・せん断の上限を実機に合わせます。初期値はそれぞれ 3 mm、1% です。
5. 原点復帰後、`Calibrate now` で手動校正します。ログと最終校正表示を確認します。原点復帰後は自動校正も予約されます。ジョブ開始時には、ジョブで使うフィーダーが再校正されます。
6. 温度ドリフトを補正する場合、`Enable periodic calibration` を有効にし、間隔を分単位で設定します。初期値は5分です。待機中のタイマー、または次の給材前に期限切れを検出して校正します。カメラ移動が必要なため、作業時間が増えます。

## 5. 上側カメラによる部品位置補正

1. 先に通常の給材と3点補正だけで、ノズルが公称位置へ安全に到達することを確認します。
2. `Edit part pipeline` を開き、対象部品の照明・背景・形状に合わせます。初期パイプラインは OpenPnP の `ReferenceLoosePartFeeder` のものです。最終 `results` が `RotatedRect` のリストになるようにします。
3. `Recognize the fed part with the top camera` を有効にし、`Recognition mode` で動作を選びます。

   | Mode | 動作 |
   | --- | --- |
   | `Every feed` | 給材のたびに認識します。物理的な給材をスキップした場合も、提示済み部品を認識します。 |
   | `First feed in job` | ジョブでこのフィーダーの準備が行われた後、最初の給材時だけ認識します。その後は3点補正済みの公称位置を使います。 |
   | `Manual only` | 給材時には自動認識しません。部品を提示した後、ピック前に `Locate part now` を押して認識します。連続ジョブでは自動停止しないため、手動操作やステップ実行で使用します。 |

4. 認識時はカメラが補正済み公称位置へ移動し、最も近い部品を選びます。`Maximum part shift`（初期値1 mm）を超えた場合はエラーになります。手動認識結果は次の給材で破棄されるため、新しい部品では再実行してください。
5. 低速の単発ジョブで、画像の検出点とピック位置を確認してから通常運転に移ります。

## 6. `test` ブランチから自分の版をビルドする場合

`test` の取得時コミットは `e6274b38f9d6f25e98677f75edde6c4bc7a9ee71` です。別のバイナリ版には、その版に対応する OpenPnP ソースを取得して登録用のパッチと追加ソースを適用し、Maven でビルドします。

```powershell
git clone --branch test https://github.com/openpnp/openpnp.git openpnp-test
.\Apply-To-OpenPnP-Test.ps1 -OpenPnPCheckout .\openpnp-test
Set-Location .\openpnp-test
mvn -DskipTests package
```

生成された `target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar` と `target\lib` を、専用のテスト用ディレクトリから一緒に起動してください。既存のバイナリインストールへ異なる版の JAR を単独で上書きしないでください。OpenPnP 本体には汎用プラグイン登録がないため、ソース適用時は `ReferenceMachine` のフィーダー一覧への1行追加が必要です。

## 補正の範囲と失敗時の挙動

3点の公称座標と実測座標から XY アフィン変換を求めます。これにより平行移動、回転、X/Y の倍率差、せん断を補正します。フィデューシャルの Z/Rotation は撮像に用いますが、ピック位置の Z は変換しません。各マークのずれ、倍率・せん断の上限、部品検出位置のずれを検査します。マーク検出失敗や上限超過では、古い補正を使い続けず校正を無効にしてフィーダーを停止します。

この実装は設定と校正結果をフィーダーごとに持ちます。複数フィーダーで同じマークを使う場合は同じ位置・Fiducial Part を個別に選択します。3点校正のためにカメラが各フィーダーについて移動します。
