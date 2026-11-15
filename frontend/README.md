# DailyAtelier frontend

DailyAtelier의 React/Vite 프론트엔드입니다. 전체 프로젝트 준비, 백엔드 실행, 테스트와
역할별 데모 흐름은 [루트 README](../README.md)를 참고하세요.

## 빠른 실행

```bash
npm ci
npm run dev
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용할 때는 `.env.example`을
`.env`로 복사하고 `VITE_API_BASE_URL`을 설정합니다. 이 값은 build 시점에 적용됩니다.

```bash
npm test
npm run test:component
npm run lint
npm run build
```

## Playwright UI QA

UI QA는 이미 실행 중인 local-demo backend와 Vite frontend만 대상으로 합니다. Playwright는 두 서버를 시작하거나 종료하지 않습니다.

처음 한 번 Chromium을 설치합니다.

```powershell
npx playwright install chromium
```

backend와 frontend는 각각 별도 터미널에서 먼저 실행합니다.

```powershell
cd D:\program\dailyatelier\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local-demo --dailyatelier.local-demo.enabled=true"
```

```powershell
cd D:\program\dailyatelier\frontend
npm run dev
```

세 번째 터미널에서 local-demo fixture 소유자에 맞춰 역할별 계정 정보를 환경변수로 주입한 다음 QA를 실행합니다. 비밀번호와 로그인 응답 토큰은 저장소에 기록하지 않습니다.

```powershell
$env:UI_QA_BUYER_ID = 'demo-buyer-one'
$env:UI_QA_BUYER_PASSWORD = '...'
$env:UI_QA_ARTIST_ID = 'demo-artist-aria'
$env:UI_QA_ARTIST_PASSWORD = '...'
$env:UI_QA_ADMIN_ID = 'demo-admin-inquiry-qa'
$env:UI_QA_ADMIN_PASSWORD = '...'
npm run qa:ui
```

비밀번호는 Git에서 제외된 `_local/test-accounts.md`의 local-demo 계정 정보를 사용합니다. 기본 대상 주소는 frontend `http://localhost:5173`, backend `http://localhost:8080`입니다. 다른 주소는 `UI_QA_BASE_URL`, `UI_QA_API_BASE_URL`로 지정합니다. 테스트 산출물인 `.playwright/`, `test-results/`, `playwright-report/`와 로컬 환경 파일 `.env.ui-qa.local`은 Git에서 제외됩니다. screenshot과 trace는 실패한 테스트에만 저장하며, 전체 화면 baseline과 visual regression 비교는 포함하지 않습니다.
