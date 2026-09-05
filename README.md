# EarllnCam

실행하면 바로 전면/후면을 번갈아 자동 촬영하는 안드로이드 앱입니다.

## 화면

- **메인**: 실행 즉시 촬영 시작. 아래 왼쪽 버튼은 **촬영 기록**, 오른쪽 버튼은 **설정**.
  화면 아래에 `EarllnCam v1.2.0 by Earlln.com` 형태로 버전이 표시됩니다.
- **촬영 기록**: 지금까지 EarllnCam이 저장한 사진/동영상을 최신순으로 보여줍니다.
  촬영 시각·길이·용량이 함께 나오고, 항목을 누르면 기본 사진/동영상 앱으로 열립니다.
  앱을 실행하면 바로 촬영이 시작되므로, 촬영 없이 기록만 볼 때는 홈 화면의
  **`EarllnCam 기록`** 아이콘(또는 앱 아이콘 길게 누르기 → 촬영 기록)을 사용하세요.
- **설정**: 촬영 매수, 사진/동영상 모드, 클립 길이, 최신 결과만 남기기 옵션.

> 설정에서 *'기존 사진/동영상은 항상 지우고 최신 결과만 남기기'* 가 켜져 있으면
> 새로 촬영할 때 이전 기록이 지워집니다. 예전 촬영본을 계속 보관하려면 이 옵션을 끄세요.

## 버전 관리

버전은 `version.properties` **한 곳**에서만 관리합니다.

```properties
versionMajor=1
versionMinor=2
versionPatch=0
```

- `versionName` = `major.minor.patch`
- `versionCode` = `major * 10000 + minor * 100 + patch` (자동 계산)
- 앱 화면의 버전 표시도 이 값을 그대로 사용합니다.

새 버전을 낼 때는 이 값을 올리고 [`CHANGELOG.md`](CHANGELOG.md)에 내용을 추가하세요.

## 설치 파일(APK) 받기

GitHub Actions가 APK를 만들어 줍니다.

- **태그로 릴리즈**: `v1.2.0` 같은 태그를 푸시하면 빌드 후
  [Releases](../../releases)에 `EarllnCam-v1.2.0.apk` 가 첨부됩니다.
- **수동 릴리즈**: Actions 탭 → *Release APK* → *Run workflow* 에서
  `릴리즈 태그`에 `v1.2.0` 처럼 입력하면 빌드한 커밋에 태그를 달고 Release에 APK를 첨부합니다.
  (이미 있는 태그를 적으면 그 릴리즈를 지우고 다시 만듭니다.)
  비워두면 빌드만 하고, 실행 결과의 Artifacts에서 APK를 받을 수 있습니다.

APK를 폰에 복사한 뒤 '출처를 알 수 없는 앱 설치'를 허용하고 설치하면 됩니다.
서명 키가 없는 상태에서는 디버그 서명 APK가 만들어지므로, 이전에 다른 키로 설치한
앱이 있으면 먼저 삭제한 뒤 설치하세요.

정식 서명을 쓰려면 저장소 시크릿에 `EARLLNCAM_KEYSTORE_BASE64`,
`EARLLNCAM_KEYSTORE_PASSWORD`, `EARLLNCAM_KEY_ALIAS`, `EARLLNCAM_KEY_PASSWORD`
를 등록하면 워크플로가 서명된 release APK를 만듭니다.

## 직접 빌드

Android Studio에서 열거나, 안드로이드 SDK가 설치된 환경에서:

```bash
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```
