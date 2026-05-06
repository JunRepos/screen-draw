# 화면필기 (ScreenDraw)

수업 중 갤럭시 태블릿에서 **어떤 앱 위에서든 화면에 바로 필기**할 수 있는 안드로이드 오버레이 앱.
캡처-필기 워크플로우 없이 라이브 화면에 바로 그립니다.

## 동작 방식

1. 앱 실행 → "플로팅 버튼 켜기" → 권한 1회 허용
2. 작은 펜 모양 버블이 화면 위에 떠 있음 (드래그로 위치 이동)
3. 버블 탭 → 풀스크린 필기 모드 진입 (이때 밑 앱 조작은 잠김)
4. 상단 툴바: `닫기 / 검정·빨강·파랑·노랑 / 얇게·중간·굵게 / 지우개 / 전체지우기`
5. 닫기 → 다시 버블 모드 (필기 내용은 사라짐)

## 빌드 (Android Studio)

1. **Android Studio** 설치 (최소 Hedgehog 2023.1+, 권장 Iguana 이상)
2. `File → Open` 으로 `screen-draw-android` 폴더 열기
3. 처음 열면 Gradle Sync 자동 실행 → wrapper / SDK 자동 다운로드
4. `Run → Run 'app'` 또는 상단 ▶︎ 버튼 → 연결된 태블릿에 바로 설치

### Gradle 직접 빌드 (CLI)

Android SDK 환경변수(`ANDROID_HOME`)가 설정되어 있다면:

```powershell
# 프로젝트 폴더에서
.\gradlew.bat assembleDebug
# 결과 APK: app\build\outputs\apk\debug\app-debug.apk
```

> wrapper(`gradlew.bat`, `gradle/wrapper/`)는 Android Studio가 처음 sync할 때 자동 생성됩니다.
> CLI만으로 시작하려면 시스템에 Gradle 8.7+ 설치 후 `gradle wrapper`로 생성.

## 태블릿 설치 (사이드로드)

가장 간단한 배포 방식. Play Store 안 거쳐도 됨.

1. 빌드된 `app-debug.apk` 를 태블릿으로 옮기기 (USB / 구글드라이브 / 카톡 자기와의대화 등)
2. 태블릿: **설정 → 앱 → 특수 액세스 → 출처를 알 수 없는 앱 설치** → 사용 중인 파일 관리자/브라우저 허용
3. 태블릿에서 APK 파일 탭 → 설치
4. 앱 첫 실행 시 **다른 앱 위에 표시** 권한 허용
5. 알림 권한도 허용 (포어그라운드 서비스 표시용)

### 업데이트

새 버전 빌드 → 같은 APK로 덮어쓰기 설치. 시그니처가 같으면(=같은 PC에서 빌드) 무중단 업그레이드.

### 외부에 배포하고 싶다면

- **GitHub Releases**: 레포에 `app-debug.apk` 첨부 → 다른 사람이 태블릿 브라우저에서 다운로드해 설치
- **Play Store 내부 테스트**: 개발자 계정 1회 $25, 100명까지 비공개 배포 (앱 심사 X). 본인만 쓰면 오버킬

## GitHub Actions로 자동 빌드 (PC에 SDK 설치 없이 APK 받기)

`.github/workflows/build-screen-draw-apk.yml` 가 같이 들어있습니다.

- `screen-draw-android/` 안의 파일을 수정해 push → 자동으로 클라우드에서 APK 빌드
- Actions 탭에서 `screen-draw-debug-apk` artifact 다운로드 → 태블릿에 설치
- `git tag v1.0 && git push --tags` 로 태그 푸시 시 → APK가 자동으로 **Release**에 첨부됨

이러면 PC에 Android Studio도 SDK도 안 깔고, 코드만 고쳐서 푸시 → 태블릿에서 설치 가능.

## 프로젝트 구조

```
screen-draw-android/
├── build.gradle.kts          # 프로젝트 레벨
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts      # 모듈 레벨 (SDK 26~34, Kotlin 1.9)
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/jun/screendraw/
        │   ├── MainActivity.kt        # 권한 요청 + 서비스 시작/종료
        │   ├── OverlayService.kt      # 포어그라운드 서비스, 버블↔캔버스 토글
        │   ├── FloatingBubble.kt      # 드래그 가능한 작은 펜 버튼
        │   ├── DrawingOverlay.kt      # 풀스크린 캔버스 + 툴바
        │   └── DrawingCanvasView.kt   # Path 기반 필기 (지우개 = PorterDuff.CLEAR)
        └── res/...
```

## 핵심 기술

- `SYSTEM_ALERT_WINDOW` 권한 + `WindowManager.addView` 로 다른 앱 위에 뷰 띄우기
- `TYPE_APPLICATION_OVERLAY` (Android 8+ 필수)
- 포어그라운드 서비스 (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`, Android 14+)
- 필기: `Path.quadTo` 로 부드러운 곡선
- 지우개: `PorterDuffXfermode(CLEAR)` + `LAYER_TYPE_SOFTWARE`
- 버블 드래그: `MotionEvent.rawX/rawY` 로 윈도우 좌표 갱신

## 알려진 제약

- 시스템 바(상태바·내비게이션바) 영역은 OS 보안 정책상 오버레이로 가리지 못함 (그 외 앱 영역 전부 잠김)
- 필기 내용은 메모리에만 — 닫기 시 사라짐. 저장이 필요하면 v2에서 `Bitmap` 캡처 추가
- S펜 필압은 v1에서 미반영 (균일 굵기). 필요시 `MotionEvent.getPressure()` 로 세그먼트별 굵기 다르게 그리기

## 라이선스

개인 사용 자유. 필요하면 MIT 등 명시.
