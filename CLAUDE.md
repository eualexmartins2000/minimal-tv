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
- Têm logo próprio: Netflix, Prime Video, YouTube, Google Play (Brandfetch) e CDNTV, MEGAFUT, Downloader e Globoplay (imagens que o
  Alex mandou; fontes em `design/logos/*-src.png`, vetorizadas com potrace em `design/logos/cdntv.svg` e `downloader.svg`). Globoplay usa o logo empilhado (card quadrado) e o horizontal (banner), vermelho `#F50234`.
  Alexa (versão nova: "alexa" + sorriso, `alexa-mono.svg`/`alexa-color.svg` #008bd6, originais em `design/logos/drive/alexa/`), Prime Video (empilhado no card, horizontal no banner) e Google Play (triângulo) vêm de SVGs do SVG Repo/selfh.st
  que o Alex salvou no Google Drive (originais em `design/logos/drive/`; versões prontas
  `prime-stacked.svg`, `prime-horizontal.svg`, `google-play-color.svg`, `google-play-mono.svg`).
  Netflix: símbolo "N" (card quadrado) em `netflix-n-color.svg` (vermelho chapado) e `netflix-n-mono.svg` (preto), imagens-fonte em `design/logos/netflix-n-*-src.*`; letreiro NETFLIX no banner.
  CDNTV: o "D" com play é o SÍMBOLO (card quadrado): `cdntv-symbol-color.svg` (laranja #F5611B) e `cdntv-symbol-mono.svg`; logo completo "cDn TV" no banner: `cdntv-full-color.svg` (laranja + cinza #5B6264) e `cdntv-full-mono.svg`. O original é card branco com logo colorido.
  Sem logo utilizável: apps de sistema. Esses usam o ícone do app.
- MEGAFUT(E) é o app que a TV chama de MegaTV. A cor ORIGINAL da marca é o **vermelho `#FF5858`** (confirmado
  pelo Alex), não o verde. Logo no app = "M" (`design/logos/megafut-symbol-red.svg`, card quadrado) + letreiro
  MEGAFUTE (`design/logos/megafute-wordmark-red.svg`, banner), os dois no mesmo vermelho. O verde-limão
  `#C6F831` era outra variante e NÃO deve ser usado (arquivos apagados).
- A TV nunca busca logo na internet: os arquivos são embutidos no app em tempo de build.
