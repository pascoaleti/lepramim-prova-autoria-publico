# LePraMim Backend

Backend Node.js do LePraMim.

Ele resolve dois pontos importantes do app:

- controla leituras gratis no servidor, sem resetar quando o usuario desinstala e instala de novo;
- valida assinatura mensal/anual na Google Play Developer API.

Tambem pode resumir textos com Gemini quando `GEMINI_API_KEY` estiver configurada. Sem chave de IA, usa resumo local simples.

## Rodar local

```bash
npm install
npm test
npm start
```

Servidor local:

```text
http://127.0.0.1:8080/health
```

## Variaveis obrigatorias para producao

Copie `.env.example` para `.env` no servidor real e configure:

```bash
NODE_ENV=production
PORT=8080
TRUST_PROXY=1
ANDROID_PACKAGE_NAME=com.lepramim.app
ALLOWED_PRODUCT_IDS=lepramim_plus_monthly,lepramim_plus_annual
FREE_USAGE_STORE_PATH=./data/free-usage.json
FREE_SCREEN_READS_PER_DAY=12
FREE_IMAGE_READS_PER_DAY=3
```

## Armazenamento

Para VPS com disco persistente:

```bash
STORAGE_DRIVER=file
FREE_USAGE_STORE_PATH=./data/free-usage.json
```

Para Google Cloud Run, use Firestore. Arquivo local em Cloud Run nao serve para producao, porque o contêiner pode reiniciar e perder dados:

```bash
STORAGE_DRIVER=firestore
FIRESTORE_USAGE_COLLECTION=lepramim_free_usage
FIRESTORE_ENTITLEMENTS_COLLECTION=lepramim_entitlements
```

Para validar assinatura, configure a Google Play Developer API usando uma das opcoes.

### Opcao A: OAuth Android Publisher

Use esta opcao em VPS quando a politica do Google Cloud bloquear criacao de chave de service account:

```bash
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...
GOOGLE_OAUTH_REFRESH_TOKEN=...
```

O refresh token precisa ter o escopo:

```text
https://www.googleapis.com/auth/androidpublisher
```

### Opcao B: service account JSON

Use apenas se a organizacao permitir chave de service account:

```bash
GOOGLE_APPLICATION_CREDENTIALS=/caminho/seguro/service-account.json
```

ou:

```bash
GOOGLE_SERVICE_ACCOUNT_JSON_BASE64=...
```

Nao coloque credenciais reais no repositorio.

## Endpoints

### Health

`GET /health`

### Consultar saldo gratis

`POST /v1/free-usage/status`

```json
{
  "installKey": "hash_sha256_do_aparelho",
  "kind": "screen"
}
```

### Consumir leitura gratis

`POST /v1/free-usage/consume`

```json
{
  "installKey": "hash_sha256_do_aparelho",
  "kind": "screen"
}
```

`kind` pode ser `screen` ou `image`.

### Validar assinatura Google Play

`POST /v1/entitlements/verify-subscription`

```json
{
  "productId": "lepramim_plus_monthly",
  "purchaseToken": "TOKEN_DA_PLAY_STORE"
}
```

O backend nao grava o token bruto. Ele grava apenas hash SHA-256 e o resultado da validacao.

### Resumir leitura

`POST /v1/reading/summarize`

```json
{
  "text": "Texto visivel na tela",
  "context": "WhatsApp",
  "mode": "simple"
}
```

## Ligar o app ao backend

Depois de hospedar o backend em HTTPS, gere o AAB assim:

```bash
gradlew :app:bundleRelease -PlepramimBackendBaseUrl=https://SEU-DOMINIO.com
```

Ou usando variavel de ambiente:

```bash
set LEPRAMIM_BACKEND_BASE_URL=https://SEU-DOMINIO.com
gradlew :app:bundleRelease
```

Enquanto `ENTITLEMENT_BASE_URL` estiver vazio, o app usa contador local. Esse contador local ainda pode ser resetado com desinstalacao. Para fechar o furo, o AAB publicado precisa apontar para o backend HTTPS.

## Deploy com Docker

```bash
docker build -t lepramim-backend .
docker run -p 8080:8080 --env-file .env -v ./data:/app/data lepramim-backend
```

Coloque Nginx ou outro proxy na frente com HTTPS.
