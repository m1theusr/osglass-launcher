# OSGlass

Launcher leve para **Android TV / Google TV** com visual de vidro, inspirado no tvOS.
Feito em Kotlin + Jetpack Compose, pensado para TVs modestas (testado numa TV com 2 GB de RAM e Android 12).

> *English summary below.*

## Recursos

- **Destaque em tela cheia (Top Shelf):** mostra o que os apps publicam na TV (YouTube, Netflix, Prime Video…), com "Retomar", sinopse e progresso. O vidro passa a desfocar a própria imagem do destaque.
- **Dock e ícones de vidro:** favoritos numa faixa de vidro simétrica; cards com brilho no topo e borda de luz.
- **Continuar assistindo**, grade de apps com **pastas**, favoritos reordenáveis (segure OK › Mover) e apps ocultos.
- **Central de controle** (no relógio): perfis, StandBy, rede, Bluetooth, entradas HDMI, ajustes, busca por voz, apps recentes, liberar memória, **modo claro/escuro** e desligar.
- **Ajustes** no estilo iOS/tvOS, com busca: volume, rede, aparência, relógio e clima, StandBy, recomendações por app, desempenho e atalhos para as telas do Android.
- **Perfis** com favoritos, apps ocultos e papel de parede próprios; **backup e restauração** dos ajustes.
- **StandBy** com modo *Aleatório* (relógio, clima, fotos dos destaques e papel de parede) e início automático.
- **Clima** (Open-Meteo, sem cadastro), **ilha dinâmica** com a música tocando e **apps recentes** com uso de RAM.
- **Boas-vindas** no padrão do assistente de configuração da Apple.
- **Modo leve** para TVs mais fracas.

### Como o vidro fica leve

O Android 12 não tem `RuntimeShader`, e desfoque ao vivo é caro numa TV. O OSGlass desfoca o papel de parede (ou o destaque) **uma vez**, numa cópia pequena, e cada painel de vidro só desenha o pedaço dessa cópia que fica atrás dele, com uma leve lente, película e borda de luz. O custo por quadro é praticamente zero.

## Instalação

Requisitos: JDK 21+ e Android SDK (compileSdk 37).

```bash
./gradlew :app:assembleRelease
adb connect IP_DA_TV:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

O build de release é assinado com a chave de debug, para instalar via ADB. Para distribuir, configure sua própria chave.

### Permissões (uma vez)

O Google TV sempre abre o próprio launcher no botão Home. O OSGlass usa um serviço de acessibilidade que só observa a tela inicial do Google TV e traz o OSGlass para frente (a mesma técnica de outros launchers). Dá para ativar pela tela de boas-vindas ou pelo ADB:

```bash
# Botão Home abre o OSGlass (e libera Desligar/Timer)
adb shell settings put secure enabled_accessibility_services com.osglass.launcher/.HomeOverrideService
# Apps recentes
adb shell appops set com.osglass.launcher GET_USAGE_STATS allow
# Ilha dinâmica (música tocando)
adb shell cmd notification allow_listener com.osglass.launcher/com.osglass.launcher.data.MediaListener
```

A permissão de ler os destaques dos apps (`READ_TV_LISTINGS`) é pedida pelo próprio app.

> Atualizar o app por `adb install -r` pode desligar o serviço de acessibilidade em algumas TVs; rode o primeiro comando de novo se o botão Home voltar para o Google TV.

### Papéis de parede próprios

Coloque imagens em `Android/data/com.osglass.launcher/files/wallpapers/` e escolha em Ajustes › Aparência.

## Limitações conhecidas

- As recomendações "Para você" do Google TV vêm do servidor do Google e não ficam disponíveis para outros launchers. O OSGlass mostra só o que os apps publicam localmente.
- No Android 12 um app não liga/desliga Wi-Fi nem muda o modo de imagem; esses itens abrem a tela certa do Android.

## Privacidade

Nada é enviado para servidores do projeto. O clima usa a API pública da [Open-Meteo](https://open-meteo.com/); com a cidade em "automática", a localização aproximada vem do IP via [ipwho.is](https://ipwho.is/). Defina uma cidade nos Ajustes para não usar essa consulta.

## Créditos

- Fonte [Dancing Script](https://github.com/googlefonts/DancingScript) (SIL Open Font License 1.1, ver `app/src/main/assets/OFL-DancingScript.txt`).
- OSGlass não tem relação com a Apple. tvOS é marca da Apple Inc.

## English summary

OSGlass is a lightweight glass-style launcher for Android TV / Google TV, built with Kotlin and Jetpack Compose and inspired by tvOS: full-screen Top Shelf from app-published content, glass dock and icons, folders, Control Center, iOS-style Settings, profiles, StandBy, weather, now-playing island, recent apps and light/dark mode. The glass effect pre-blurs the backdrop once, so it runs smoothly on low-end TVs (Android 12, 2 GB RAM). See the install and permission commands above.

## Licença

[MIT](LICENSE)
