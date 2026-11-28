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

## Como a Home funciona na TV (já configurado)
- O botão Home da TCL abre o launcher original (`com.google.android.tvlauncher`), que tem prioridade
  maior que a nossa Home. O comando `set-home-activity` não vence essa prioridade.
- Solução: `HomeService` (serviço de acessibilidade) escuta só eventos de janela desse pacote e abre
  o Minimal TV por cima. Não lê conteúdo da tela e não usa permissão de sobreposição.
- Ligar o serviço (console do ADB TV, app Google TV no iPhone como teclado):
  `settings put secure enabled_accessibility_services com.alex.minimaltv/com.alex.minimaltv.HomeService`
  e `settings put secure accessibility_enabled 1`.
  O Android apaga o nome se o APK instalado não tiver o serviço (conferir `versionName`).
- Projectivy está desativado (reserva): `pm enable com.spocky.projengmenu`.
  Launcher da TCL continua ligado (reserva).
- O Play Protect bloqueia a instalação pelo Downloader (serviço de acessibilidade). Atualizar assim:
  Downloader baixa e o usuário toca em Done; depois, no console:
  `cp "/sdcard/Download/Downloader/$(ls -t /sdcard/Download/Downloader | head -1)" /data/local/tmp/mtv.apk`
  e `pm install -r /data/local/tmp/mtv.apk` (o sistema não lê o /sdcard direto).
- Desfazer o serviço: `settings put secure enabled_accessibility_services ""`.

## Logos de marca (protótipo e futuro app)
- Vieram do Brandfetch (conector do Claude) e ficam em `design/logos/`. São marcas registradas das empresas:
  uso pessoal neste launcher; não redistribuir como produto.
- Têm logo próprio: Netflix, Prime Video, YouTube, Google Play. Sem logo utilizável: Globoplay (só selo
  "100 anos"), Alexa (só Amazon), MegaTV, Downloader e apps de sistema. Esses usam o ícone do app.
- A TV nunca busca logo na internet: os arquivos são embutidos no app em tempo de build.
