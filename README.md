# Spix para Android

Base nativa em Kotlin e Jetpack Compose, criada a partir do design em [`design/`](design/README.md). Abra **esta pasta** no Android Studio e aguarde o Gradle Sync.

## Executar

- Android 8.0 ou superior (API 26); compile/target SDK 37.
- JDK 17 ou superior compatível com o Gradle incluído. Neste ambiente foi usado o JBR 25 do Android Studio.
- Instale o SDK 37 e Build Tools 36.0.0 pelo SDK Manager.
- Configure `ANDROID_HOME` ou `sdk.dir` em `local.properties` (arquivo local, ignorado pelo Git).
- Selecione um aparelho/emulador e execute a configuração `app` no Android Studio.

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

APK de desenvolvimento: `app/build/outputs/apk/debug/app-debug.apk`.

## Implementado nesta base

- Compose com Material 3 e temas claro e escuro próprios: vermelho `#C30027`, foco azul `#065FD4`, seleção `#FCE8EC` e superfícies neutras.
- Ícones vetoriais completos, navegação inferior, início, downloads, biblioteca, busca e configurações.
- Colagem pelo clipboard, validação local de links YouTube e tratamento de erro junto ao campo.
- Fila de links salva no aparelho, normalização de URLs, proteção contra duplicados e remoção com confirmação.
- `ViewModel`, fluxos observados com ciclo de vida e restauração do campo e da busca com `SavedStateHandle`.
- Layout com rolagem, insets do sistema e teclado. Sem músicas ou progresso fictícios em execução.

O botão **Baixar vídeo** confirma a gravação e inicia um download em segundo plano. A fila mostra progresso, cancelamento e nova tentativa; vídeos concluídos aparecem na biblioteca e podem ser abertos em um player do celular. Os dados ilustrativos do FigCraft não são inseridos na biblioteca real.

## Organização

```text
app/src/main/java/com/pedroaba/spix/
  MainActivity.kt              Entrada Android e edge-to-edge
  SpixApplication.kt           Composição das dependências
  domain/                     Modelos e validação pura de URL
  data/                       Contrato e repositório local da fila
  ui/SpixViewModel.kt          Estado, ações e eventos
  ui/SpixApp.kt                Scaffold, navegação e diálogos
  ui/components/              Componentes e ícones reutilizáveis
  ui/screens/                 Telas sem acesso direto ao armazenamento
  ui/theme/                   Cores e tema
```

As versões estão centralizadas em `gradle/libs.versions.toml`. O AGP 9 usa Kotlin integrado; não aplique `org.jetbrains.kotlin.android` junto dele. O compilador Compose está alinhado com Kotlin 2.2.10. O wrapper verifica o SHA-256 da distribuição Gradle.

## Próximas etapas

1. Integrar Media3, player, miniplayer e MediaSession para reprodução dentro do app.
2. Adicionar escolha de qualidade e extração de áudio.
3. Evoluir para Room se a fila crescer; aprimorar a retomada de arquivos parciais entre reinícios.

## Referências de configuração

- [Compatibilidade do AGP 9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Kotlin integrado no AGP](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)

## Verificação realizada

Em 02/11/2026: APK debug gerado; dois testes JVM de validação de URLs passaram; lint sem erros, com seis avisos de versões/configuração. No emulador Android API 37 foram verificados erro de link, confirmação e inclusão, persistência após encerrar o processo, remoção e navegação para biblioteca/busca. A fila de teste foi removida ao final. Captura real: [`design/android-inicio.png`](design/android-inicio.png). Download e reprodução não fazem parte dessa validação.

## Tema escuro

Em Configurações → Aparência, escolha Sistema, Claro ou Escuro. A opção Sistema acompanha a preferência do Android; as opções Claro e Escuro têm prioridade e ficam salvas ao reiniciar. `SpixTheme(darkTheme = ...)` também permite forçar um tema nas previews. As telas usam papéis semânticos de `MaterialTheme.colorScheme`, com superfícies neutras, texto secundário adaptado, foco azul claro e seleção em vermelho suave. A marca e o botão de download mantêm `#C30027`. O tema nativo de inicialização tem recursos `values-night`, e as barras do sistema acompanham a configuração. Captura: [`design/android-inicio-escuro.png`](design/android-inicio-escuro.png).

## Configurações e movimento

Abra o ícone de configurações no cabeçalho de qualquer aba.

- **Tema**: Sistema, Claro ou Escuro, aplicado imediatamente e persistido. As barras do Android também acompanham o tema escolhido.
- **Onde salvar os vídeos**: pasta pública Downloads/Spix por padrão, ou pasta escolhida com o seletor nativo do Android. O acesso concedido é persistido e verificado ao voltar à tela. Cancelar a escolha mantém o local anterior. É possível retornar à pasta padrão. A mudança vale para novos arquivos; não move ou apaga os existentes.
- **Animações de navegação**: transições com fade e deslocamento curto entre abas e telas de detalhe, com entrada/saída animada da barra inferior. A preferência pode ser desligada. As animações do Compose também seguem a escala de animação do sistema.
- **Sobre**: versão e estágio atual do aplicativo.

`SettingsRepository` centraliza as preferências. `VideoStorage` grava na pasta pública usando MediaStore no Android 10+, sem permissão ampla, ou na pasta escolhida via SAF. No Android 8/9, solicita permissão de escrita para o destino público.

### Testar o armazenamento no emulador

```sh
./gradlew :app:connectedDebugAndroidTest
```

Execute em um emulador de teste. Os testes gravam, leem e removem pequenos arquivos temporários. Um teste verifica a pasta do app e a criação de nomes distintos; outro verifica a pasta escolhida pelo usuário com a permissão persistida. Para exercitar o segundo, selecione uma pasta em Configurações antes de executar (sem uma pasta selecionada, ele é ignorado). A preferência anterior é restaurada ao final do teste da pasta padrão.

Referência: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) e [animações em Compose](https://developer.android.com/develop/ui/compose/animation/quick-guide).

### Verificação desta etapa

Build debug, testes JVM e lint concluídos sem erros. Dois testes de armazenamento passaram no emulador API 37, sem ignorados: gravação/leitura na pasta do app e na pasta escolhida com permissão persistida. Também conferidos tema escuro forçado com reinício do processo, tema claro, desativação das transições, cancelamento do seletor e persistência da pasta selecionada. As capturas `design/android-configuracoes.png` e `design/android-configuracoes-escuro.png` mostram a interface real no emulador.

## Ícones Fluent

A interface usa [Fluent UI System Icons da Microsoft](https://github.com/microsoft/fluentui-system-icons), Regular 24px e Filled 24px na aba ativa. Os vetores oficiais são convertidos em ImageVector, preservando geometria e regras de preenchimento; o tema aplica as cores. As fontes SVG, versão fixada e avisos de licença estão em `design/icons/fluent/`. Licença e NOTICE também são incluídos nos assets do APK. Reimporte com `python3 scripts/import_fluent_icons.py`.

A troca de ícones foi compilada e revisada no emulador, incluindo a navegação com aba ativa e os temas claro/escuro. Capturas atuais: `design/android-inicio.png`, `design/android-configuracoes.png` e `design/android-configuracoes-escuro.png`.

## Dados reais do YouTube

Cole um link válido na tela inicial. Após uma pausa de 600 ms, o app consulta o endpoint HTTPS `https://www.youtube.com/oembed`, sem chave de API, e mostra **título, canal e miniatura**. Também é possível acionar a busca pela ação do teclado. O botão “Baixar vídeo” mantém seu rótulo e fica desabilitado até a prévia estar pronta.

- Skeleton com miniatura e linhas de texto, brilho suave durante a consulta e transição para os dados encontrados. A opção de animações desativada mantém os placeholders estáticos e remove as transições.
- Erros de conexão e vídeo indisponível aparecem no card com a ação “Tentar novamente”.
- Editar o link cancela a consulta anterior e invalida a prévia imediatamente. Respostas antigas não substituem o vídeo atual.
- Cache em memória para até 20 vídeos por até cinco minutos; miniaturas carregadas pelo Coil.
- Confirmação com o título/canal consultados antes de salvar na fila. Esses dados e a URL da miniatura são persistidos e restaurados ao reiniciar.
- Links antigos na fila continuam legíveis. Consultar e confirmar um vídeo já salvo atualiza seus metadados sem duplicar o item.
- O oEmbed não retorna duração nem tamanho do arquivo; esses campos não são estimados. Não há scraping de HTML nem execução do iframe retornado pelo serviço.

A permissão `INTERNET` permite a consulta e os downloads descritos abaixo.

Validação: nove testes JVM passaram, incluindo HTTP simulado, cache, JSON inválido, cancelamento, debounce, tentativa após falha e proteção contra resultados atrasados. No emulador, um vídeo público real exibiu título/canal/miniatura, foi salvo na fila e teve os metadados restaurados após encerrar o processo. O item de teste foi removido ao final. Capturas: `design/android-video-preview.png` e `design/android-fila-com-metadados.png`.

Referências: [oEmbed](https://oembed.com/), [rede no Android](https://developer.android.com/develop/connectivity/network-ops/connecting) e [Coil](https://coil-kt.github.io/coil/getting_started/).

## Downloads e repositório local

O Git está inicializado na branch `main`, sem remoto. O commit `f208cbf` preserva a interface anterior à implementação dos downloads. Builds, caches, caminhos locais e chaves ficam ignorados.

- Motor Android `youtubedl-android`/FFmpeg 0.18.1; extrator oficial yt-dlp **2026.10.26**, incluído em `app/src/main/res/raw/ytdlp`. O recurso pode ser restaurado com `python3 scripts/vendor_ytdlp.py`. O SHA-256 é `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`, conferido com o release oficial. Não há atualização de executáveis em segundo plano.
- WorkManager mantém tarefas únicas por vídeo, exige conexão e espaço disponível e publica uma notificação de serviço `dataSync`. O motor processa um vídeo por vez. A fila persiste metadados, estado, progresso e URI do arquivo.
- MP4 com áudio, até 720p, limite de 2 GB. Lives e vídeos que exigem autenticação não são suportados. Bloqueios do YouTube aparecem como erro com nova tentativa, sem marcar o item como concluído.
- A extração e a junção ocorrem no cache interno. Ao concluir, a gravação usa `MediaStore.Downloads` com `RELATIVE_PATH=Download/Spix/` e `IS_PENDING` no Android 10+. A publicação ocorre só depois de fechar a gravação. No Android 8/9, usa a mesma pasta pública com permissão de escrita.
- A pasta padrão é **Downloads/Spix**. A pasta escolhida em Configurações usa a permissão SAF persistida. Mudar o destino não move vídeos existentes. Cancelamento ou falha remove arquivos públicos incompletos e o cache temporário dessa tarefa.
- Cancelar interrompe o processo do extrator e limpa auxiliares nativos do mesmo UID; essa limpeza ocorre com o motor serializado, para não atingir outro download. Tentar novamente começa uma nova tentativa. O trabalho pode ser reexecutado pelo Android após interrupção; não há promessa de retomada byte a byte entre processos. Forçar a parada do app interrompe o serviço até o Android permitir nova execução.
- A biblioteca contém somente arquivos concluídos. “Abrir” entrega a URI com permissão temporária a um player instalado. Remover da lista preserva o arquivo na pasta pública.

A primeira preparação descompacta Python e FFmpeg; o APK universal de desenvolvimento inclui bibliotecas nativas de várias arquiteturas. Notificações são solicitadas no Android 13+; negá-las não impede a tarefa. No Android 8/9, negar armazenamento impede a gravação padrão e apresenta orientação.

A licença GPL-3.0 da biblioteca está incluída em `assets/licenses/`. Fontes e avisos dos componentes: [wrapper Android](https://github.com/yausername/youtubedl-android), [yt-dlp fixado](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26). O projeto permanece apenas local.

Nota de ferramentas: o detector `WebViewApiAvailability` do lint do AGP 9.2.1 apresentou uma falha interna ao inspecionar símbolos Kotlin das novas dependências. Esse detector está desativado; o app não usa WebViews. As demais checagens continuam ativas.

Validação dos downloads: build debug, dez testes JVM e lint passaram. No emulador API 37, passaram os testes de persistência da biblioteca, gravação/leitura em Downloads/Spix sem sobrescrita e exclusão de arquivo parcial após erro. O teste de pasta SAF foi ignorado por não haver pasta personalizada selecionada nesta rodada. Um vídeo público real foi baixado e permaneceu concluído na biblioteca após encerrar e abrir o app. O MP4 foi conferido com ffprobe: 19,06 s, trilhas de vídeo e áudio, 533.915 bytes. Capturas: `design/android-download-concluido.png` e `design/android-biblioteca-video.png`.
