# Minimal TV: regras do projeto

## REGRA INEGOCIÁVEL: segredos nunca vão pro Git
- Chave de assinatura (.keystore/.jks/.p12), senhas, tokens e chaves de API **nunca** entram no repositório.
  Vale para repositório privado também e para "só por enquanto".
- Segredos ficam nos **GitHub Actions Secrets** e chegam ao build por variáveis de ambiente
  (hoje: `KEYSTORE_BASE64` e `KEYSTORE_PASSWORD`).
- Antes de todo commit: conferir `git status` e `git diff --cached` procurando arquivo ou texto secreto.
  Nunca escrever senha dentro de `build.gradle.kts` ou de scripts versionados.
- Se algo vazar: tratar como comprometido e **trocar a chave**. Apagar do Git não basta,
  o histórico fica público.
- O workflow `build.yml` falha sozinho se achar keystore versionado.

## Contexto
- Launcher próprio para a TCL 40" S5400A (Android TV 11, só 0,9 GB de RAM: manter leve).
- Dono: Alex, leigo em programação, só tem iPhone. Respostas curtas e diretas, perguntas em pop-up,
  plano antes de codar, decidir o COMO e explicar em 1 linha.
- O repositório é **público** de propósito: o Downloader da TV precisa baixar o APK da Release.
  Por isso a regra acima é ainda mais crítica.
- Build na nuvem (GitHub Actions) publica em `releases/download/latest/minimal-tv.apk`.
  Atalho na TV: `is.gd/minimaltv` (Downloader, digitar `https://` na frente).
- Para digitar na TV: usar o app Google TV no iPhone (teclado remoto). ADB TV tem Console com shell.
- Layouts de referência em `design/` (1920x1080). Medidas do código vêm deles.
