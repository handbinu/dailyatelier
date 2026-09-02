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
