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

O botão **Baixar vídeo** confirma a gravação e inicia um download em segundo plano. A fila mostra progresso, cancelamento e nova tentativa; vídeos concluídos aparecem na biblioteca e podem ser reproduzidos no player interno. Os dados ilustrativos do FigCraft não são inseridos na biblioteca real.

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

1. Persistir fila e posição para retomada após encerrar o processo.
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
- **Animações de navegação**: transições com fade e deslocamento curto entre abas e telas de detalhe. Cabeçalho e barra inferior permanecem estáveis entre abas; a mudança para detalhes ajusta o espaço uma única vez. A preferência pode ser desligada. As animações do Compose também seguem a escala de animação do sistema.
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
- A biblioteca contém somente arquivos concluídos. “Reproduzir” abre o player interno com a fila offline. Excluir do celular remove o arquivo físico e, depois, o registro da biblioteca e os membros das playlists. Se o arquivo não puder ser excluído, o registro é mantido para nova tentativa.

A primeira preparação descompacta Python e FFmpeg; o APK universal de desenvolvimento inclui bibliotecas nativas de várias arquiteturas. Notificações são solicitadas no Android 13+; negá-las não impede a tarefa. No Android 8/9, negar armazenamento impede a gravação padrão e apresenta orientação.

A licença GPL-3.0 da biblioteca está incluída em `assets/licenses/`. Fontes e avisos dos componentes: [wrapper Android](https://github.com/yausername/youtubedl-android), [yt-dlp fixado](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26). O projeto permanece apenas local.

Nota de ferramentas: o detector `WebViewApiAvailability` do lint do AGP 9.2.1 apresentou uma falha interna ao inspecionar símbolos Kotlin das novas dependências. Esse detector está desativado; o app não usa WebViews. As demais checagens continuam ativas.

Validação dos downloads: build debug, dez testes JVM e lint passaram. No emulador API 37, passaram os testes de persistência da biblioteca, gravação/leitura em Downloads/Spix sem sobrescrita e exclusão de arquivo parcial após erro. O teste de pasta SAF foi ignorado por não haver pasta personalizada selecionada nesta rodada. Um vídeo público real foi baixado e permaneceu concluído na biblioteca após encerrar e abrir o app. O MP4 foi conferido com ffprobe: 19,06 s, trilhas de vídeo e áudio, 533.915 bytes. Capturas: `design/android-download-concluido.png` e `design/android-biblioteca-video.png`.

## Navegação e desempenho no aparelho físico

Para avaliar a fluidez, gere e instale a variante otimizada local (R8, recursos reduzidos e sem depuração):

```sh
./gradlew :app:assembleOptimized
adb -s SERIAL install --no-streaming -r app/build/outputs/apk/optimized/app-optimized.apk
```

`optimized` usa a assinatura de desenvolvimento já instalada para preservar os dados durante os testes locais. `performance` instala um app separado (`.benchmark`) com catálogo sintético. `release` continua com a configuração de distribuição separada. O modo `debug` mantém as ferramentas do Compose e serve para depuração.

As abas usam fade-through: a tela anterior desaparece antes de a próxima entrar. As telas de detalhe usam um deslocamento curto na entrada. O cabeçalho compartilhado fica fora do NavHost e a barra inferior não anima a própria altura, evitando recalcular o espaço do Scaffold a cada quadro. A saída tem fade curto e a entrada é adiada para evitar sobreposição de telas opacas. Tocar novamente na aba ativa não recria a navegação.

Cada destino observa seus próprios fluxos de estado, para que digitação e progresso dos downloads não invalidem a estrutura global. Todas as listagens de conteúdo usam LazyColumn com Paging 3, chaves estáveis e consultas paginadas no Room. O shimmer lê a animação no desenho e reutiliza o gradiente; pausa enquanto a tela não está RESUMED. A preferência de animações desativadas continua sendo respeitada.

Coleta reproduzível no aparelho (deixe o Spix aberto e não interaja durante a execução):

```sh
adb -s SERIAL shell cmd package compile -m speed -f com.pedroaba.spix
python3 scripts/measure_navigation.py --serial SERIAL --output design/performance/coleta.json
```

O script faz duas voltas de aquecimento, zera os contadores HWUI e percorre 35 transições entre abas, configurações e busca. Não baixa arquivos nem altera preferências do Spix. Mantém a tela acesa por USB durante a coleta e restaura a configuração anterior do Android ao terminar. A coleta é um diagnóstico de quadros no aparelho, sem afirmar certificação de fluidez ou substituir um Macrobenchmark. Os dados brutos e os resumos ficam em `design/performance/`. A frequência de atualização, temperatura e processos do Android podem variar entre execuções.

Referência: [desempenho no Jetpack Compose](https://developer.android.com/develop/ui/compose/performance).

Validação em 04/11/2026 no Galaxy A52 (Android 14): duas coletas da navegação anterior registraram 7,56–8,50% de quadros atrasados; com o fade-through, as duas coletas ficaram em 6,24–6,60%. O percentil 90 passou de 15–18 ms para 13 ms. Ambas as versões foram otimizadas e compiladas com ART `speed`; os números são diagnósticos desse fluxo e aparelho. Build debug/otimizado, dez testes JVM e lint passaram. As preferências e os arquivos existentes do celular foram preservados.

## Progresso real dos downloads

A fila distingue preparação, transferência, finalização do arquivo e gravação no dispositivo. Durante a transferência, mostra porcentagem, bytes recebidos, tamanho total, velocidade e tempo restante conforme os dados disponíveis. Quando o YouTube entrega vídeo e áudio separados, cada mídia mostra seu próprio avanço. Totais estimados recebem `≈`; sem um tamanho conhecido, a interface mostra os bytes transferidos com barra indeterminada, sem inventar uma porcentagem.

O motor publica hooks JSON com `--progress-template`, placeholder `null` para campos ausentes e os canais stdout/stderr unidos no callback. O app interpreta os números diretamente e não depende do texto da porcentagem/ETA do terminal. A leitura do processo envia eventos a um canal conflado; gravações e notificações acontecem fora do leitor nativo. A UI recebe até uma atualização a cada 400 ms, com atualização imediata ao começar, trocar de mídia/etapa e terminar uma transferência. Notificações também recebem a etapa e o percentual. A conclusão de uma trilha não marca o download inteiro como concluído; a conclusão só ocorre após salvar o arquivo.

Os detalhes de transferência são persistidos junto da fila. Pedidos antigos continuam legíveis. Nova tentativa limpa o progresso anterior.

Validação: 15 testes JVM e lint passaram. Os novos testes cobrem bytes reais, total desconhecido/estimado, etapas de áudio/vídeo, linhas inválidas e entrega dos eventos finais mesmo em transferências rápidas. O check abaixo roda o extrator fixado contra um servidor local lento, gera um MP4 sintético com FFmpeg e verifica eventos intermediários e integridade do arquivo, sem depender do YouTube:

```sh
# Requer Python 3.11+ e FFmpeg no PATH.
python3 scripts/check_download_progress.py
```

Na validação foram observados sete eventos de transferência (de 1.024 até 603.531 bytes) e quatro eventos de pós-processamento.

No Galaxy A52, um download real foi gravado e conferido quadro a quadro: a UI exibiu vídeo (224 kB) e áudio (309 kB) com percentuais, bytes, velocidade e ETA, seguidos das etapas de finalização e gravação. A transferência era curta o suficiente para os estados ocorrerem entre as consultas do UIAutomator, e a gravação confirmou os estados intermediários. A barra acompanha o fim de cada trilha imediatamente, sem esperar a suavização dos valores intermediários. O item e o arquivo criados no teste foram removidos; o download anterior do usuário foi preservado.

## Player interno de música e vídeo

Tocar no card de um vídeo baixado na home, biblioteca, downloads ou busca abre o player e inicia a reprodução. A folha de opções oferece “Ouvir agora”; ações de remover, cancelar e tentar novamente mantêm seu comportamento próprio. O player é inspirado na referência do YouTube Music. A tela acompanha o tema Sistema, Claro ou Escuro do app, incluindo fundo, textos, controles, menu, fila e barras do Android. O player tem seletor Música/Vídeo, capa ou vídeo centralizado, título/canal, progresso com tempos válidos, play/pause, anterior/próximo, aleatório, repetição e fila “A seguir”. O menu permite abrir a fila, reiniciar a faixa ou encerrar a reprodução. Os ícones são Fluent oficiais.

O vídeo ocupa toda a largura disponível, sem margens laterais ou cantos arredondados, e tem altura 25% maior que a proporção natural do arquivo. A imagem preenche essa área com zoom centralizado, sem distorção e com corte nas laterais; vídeos verticais podem aumentar a área rolável. O seletor Música/Vídeo tem altura visual de 36 dp, área de toque de 48 dp e respiro adicional de 16 dp acima do cabeçalho. Títulos e controles mantêm margens de 20 dp. O ajuste foi conferido no Galaxy A52 nos dois modos; build performance, 18 testes JVM e lint passaram (com avisos existentes).

No modo Música, a thumbnail ocupa a mesma área e proporção de exibição do vídeo, com largura total e sem cantos arredondados. A imagem usa ajuste integral (`ContentScale.Fit`), preservando seu conteúdo e proporção; diferenças de formato ficam com o fundo do tema, sem recorte ou distorção. A área de mídia mantém a mesma altura entre os modos.

O Media3 1.11.1 mantém o ExoPlayer em um MediaSessionService com foco de áudio, tratamento da desconexão de fones, wake lock local e controles de mídia do Android. Música desabilita a trilha de vídeo do mesmo MP4; Vídeo reabilita a renderização mantendo a posição. Ao minimizar ou colocar o app em segundo plano, o áudio continua e a renderização de vídeo é desativada. O miniplayer permite reabrir, pausar e encerrar a sessão. A fila é criada a partir dos arquivos concluídos; não há reprodução de links pendentes nem download adicional para escutar.

O progresso é observado em um fluxo separado, somente enquanto a interface o observa, evitando recompor capa, controles e navegação a cada atualização do relógio. A troca de superfície usa a integração do PlayerView com Compose. A retomada persistida após morte do processo continua como etapa futura.

Validação: build debug/otimizado, 18 testes JVM e lint passaram. No Galaxy A52 foram conferidos áudio, vídeo, troca de modo sem reiniciar posição, miniplayer, fila, ciclo de repetição e reinício da faixa. Ao deixar o app em segundo plano, a posição passou de 0:27 para 0:36 e o serviço permaneceu em foreground `mediaPlayback`. Capturas reais: `design/android-player-musica.png` e `design/android-player-video.png`.

Ajustes do player e dos cards: build debug e lint passaram. No Galaxy A52, foram conferidos o toque na thumbnail da home e no título da biblioteca iniciando a reprodução, o player nos temas Claro e Escuro, e a abertura do menu e da fila. A preferência Claro selecionada no aparelho foi restaurada após a conferência.

Referência técnica: [MediaSessionService e reprodução em segundo plano](https://developer.android.com/media/media3/session/background-playback).


## Cancelamento, filtros e notificações

Downloads tem filtros separados para **Em andamento**, **Com erro**, **Concluídos** e **Cancelados**. Cada filtro consulta os estados correspondentes em ordem estável; a paginação termina no último registro. **Cancelar todos** fica disponível na fila e na notificação, e cada transferência também pode ser cancelada individualmente. O cancelamento é persistente, encerra a execução nativa e preserva os arquivos concluídos.

Nos filtros **Com erro** e **Cancelados**, **Tentar todos novamente** reenvia todos os itens elegíveis do filtro, inclusive os que ainda não foram carregados pela paginação. **Selecionar** ativa checkboxes e um botão fixo de **Tentar selecionados novamente**; a seleção permanece ao rolar a lista. O reenvio é transacional, remove IDs repetidos e confere o estado atual de cada item, preservando downloads concluídos ou em andamento. Os cliques ficam bloqueados durante o envio e a tela abre a fila ao terminar.

Validação do retry em lote em 07/11/2026: 50 testes JVM e seis testes instrumentados aprovados, build otimizado e lint sem erros. Conferidos retry de 350 falhas além dos 80 registros da primeira página, seleção com 750 IDs, exclusão de duplicados e proteção de estados concluídos, ativos e cancelados durante o agendamento. Na interface do celular, a seleção e o botão fixo permaneceram disponíveis após rolar várias páginas; a verificação manteve zero downloads ativos e os 34 arquivos concluídos, sem reenviar o histórico pessoal.

A fila exige internet validada. Ao perder a conexão, a transferência é interrompida e o item aguarda conexão na fila. As tentativas do extrator são limitadas. Notificações individuais mostram etapa e porcentagem quando o tamanho está disponível; playlists mostram **1 de N** e o progresso do vídeo atual. A ordem e o tamanho do lote são persistidos no banco para sobreviver ao reinício. A migração de 2 para 3 adiciona esses dados sem apagar o catálogo.

Validação em 07/11/2026: 47 testes JVM, seis testes instrumentados, build otimizado e lint sem erros. Testados cancelamento persistente, perda de conexão por fluxo controlado, paginação de 350 falhas sem IDs repetidos e ordem do lote após reinício. No celular, a notificação real mostrou **1 de 4** e porcentagem; cancelar a fila encerrou os processos nativos e removeu a notificação. O histórico real de 326 falhas chegou ao último item e permaneceu no fim após novas rolagens. A instalação principal permaneceu com zero downloads ativos e 34 arquivos concluídos. Downloads de teste foram feitos na variante lab e seus arquivos foram removidos.

## Downloads de playlists

O campo da tela inicial aceita links de vídeos e playlists do YouTube, incluindo YouTube Music e links compartilhados de vídeos com `list=`. Quando há uma playlist no link, o Spix consulta a lista completa com o extrator já embarcado, mostra o título e a lista com carregamento visual sob demanda de todos os vídeos disponíveis. O nome pode ser alterado antes de **Confirmar playlist e baixar**. Somente a confirmação cria a playlist local e agenda os downloads; uma nova confirmação do mesmo link atualiza o agrupamento sem duplicá-lo. A confirmação informa a pasta de destino e a qualidade de até 720p com áudio.

Cada vídeo disponível recebe uma entrada persistente na fila, com progresso, cancelamento e nova tentativa individuais. Um único consumidor executa as transferências; não é criado um worker para cada vídeo. IDs repetidos na playlist e vídeos já na fila ou concluídos não são duplicados; itens com falha ou cancelados podem ser enviados novamente. Itens privados, excluídos ou identificados como restritos na listagem são ignorados e informados na prévia. Restrições descobertas ao baixar ficam como erro no respectivo vídeo e não impedem os demais downloads. Uma falha ao agendar um item também não interrompe a inclusão dos seguintes; o link é preservado para repetir a tentativa.

A consulta da playlist pode ser refeita em caso de erro e é cancelada quando o link muda. Ela compartilha a exclusão mútua do motor nativo com os downloads, portanto aguarda a transferência atual antes de consultar o YouTube. Não há limite fixo de quantidade de vídeos; paginação é feita pelo extrator. Permanecem os limites de 2 GB por vídeo e a ausência de suporte a lives e conteúdos que exigem autenticação.

Validação desta etapa: 28 testes JVM, build debug e lint passaram. No Galaxy A52 conectado via USB, uma playlist pública com 29 vídeos carregou a prévia e uma segunda playlist com 4 vídeos completou os quatro downloads (8:36, 6:02, 2:42 e 2:34), cada um com URI local e estado concluído. Os quatro registros e arquivos usados no teste foram removidos. Os testes de armazenamento no aparelho tiveram três aprovações e um caso SAF ignorado, por não haver pasta personalizada selecionada. O runner desinstalou o app ao finalizar: a versão final foi reinstalada, o registro da biblioteca anterior foi restaurado a partir do vídeo preservado e sua reprodução foi conferida; as preferências locais voltaram ao padrão. Antes de executar novamente testes conectados em um aparelho pessoal, salvar os dados locais do app e verificar a política de desinstalação do runner.


## Layout aprovado no FigCraft

As telas de início, prévia de link, downloads, biblioteca, busca, validação e opções acompanham a referência compartilhada. O mapeamento dos frames, as adaptações para dados reais e as diferenças de comportamento estão em [`design/layout-implementation.md`](design/layout-implementation.md). A home observa a fila persistente e apresenta o download em andamento com progresso real; tocar nele abre a aba Em andamento. A prévia agora é uma tela própria antes de iniciar a gravação. Player e miniplayer foram preservados.

Após a revisão visual, o usuário solicitou manter o campo de links anterior (OutlinedTextField com ícone de busca), a barra inferior Material 3 com os ícones originais e a engrenagem de configurações no cabeçalho. Esses três elementos foram restaurados; o progresso na home e as demais melhorias de layout permanecem.

## Player no formato YouTube

O player abre com vídeo em 16:9 no topo, preservando a proporção original sem recortar a imagem. Os controles de anterior, reproduzir/pausar e próximo ficam sobre o vídeo; um toque mostra ou oculta os controles, que desaparecem após quatro segundos durante a reprodução. A posição fica na barra de progresso, e o botão de tela cheia abre o vídeo em uma superfície própria com espaço para as barras do Android.

A fila fica visível abaixo do título, com miniaturas horizontais, canal, posição na fila e destaque para o vídeo atual. Tocar em um item troca a reprodução sem fechar a lista. A fila pode ser recolhida e reaberta; repetição e reprodução aleatória continuam disponíveis no cabeçalho. O menu do vídeo permite ouvir somente o áudio, voltar ao vídeo, reiniciar e encerrar a reprodução.

Colada na borda inferior do vídeo, como no YouTube, a barra de progresso e a onda são desenhadas juntas sobre a imagem, sem faixa preta abaixo do player: a parte já reproduzida fica na cor de destaque do tema, a parte restante em branco suave, e a onda inteira pulsa com o volume real do áudio em tempo real. O marcador de posição e o divisor da onda compartilham a mesma coordenada, então ficam sempre alinhados; arrastar sobre a onda também executa a busca. O nível é medido por um processador de áudio passivo encadeado no pipeline do Media3, sem alterar a reprodução; a forma da onda é determinística por vídeo e a animação pausa quando a reprodução para. Um gradiente sutil garante contraste da barra sobre vídeos claros.

Verificação em 07/11/2026: build debug, 28 testes JVM e lint aprovados. Reprodução local e troca pela fila conferidas no celular conectado, com os 19 registros da biblioteca preservados.

Verificação em 10/11/2026: build debug instalado no aparelho conectado, 57 testes JVM e lint aprovados. Reprodução iniciada pela biblioteca com a onda visível sobre a barra de progresso e o áudio ativo pelo pipeline do Media3. Nesta mesma data, após o ajuste de alinhamento: a onda e a barra passaram a ser desenhadas no mesmo canvas sobrepondo o vídeo até a borda inferior, com o indicador e o divisor alinhados; a hierarquia de interface confirmou o slider ocupando a largura total e terminando exatamente na borda do vídeo, com reprodução ativa e sem travamentos. Captura real: [`design/android-player-onda.png`](design/android-player-onda.png).

## Playlists locais

Na aba **Playlists → Criar playlist**, a tela de criação permite informar um nome, buscar e selecionar vídeos ou músicas baixados. Editar permite renomear a playlist e adicionar ou remover membros. Playlists vazias são permitidas. Excluir uma playlist pede confirmação e mantém os arquivos da biblioteca. Os agrupamentos e a ordem dos itens são persistidos no banco Room do catálogo. A criação e a edição permitem escolher a thumbnail de qualquer vídeo selecionado para ser a capa. Não há seleção de imagens da galeria. Sem escolha manual, usamos a primeira thumbnail disponível; se o vídeo escolhido sair da playlist ou for excluído, a capa volta ao modo automático. A migração do banco de 1 para 2 adiciona a capa sem apagar o catálogo existente.

Correção de capas em 07/11/2026: a capa é a thumbnail de um vídeo pertencente à playlist. O seletor mostra somente os vídeos selecionados, incluindo itens pendentes de download. A escolha é preservada ao reimportar a playlist e volta ao modo automático ao remover ou excluir o vídeo usado na capa. Quatro testes instrumentados passaram; escolha de uma thumbnail diferente da primeira e exibição da capa salva foram conferidas no celular. A playlist de validação foi removida, preservando os 19 vídeos.

O menu de três pontinhos dos vídeos no início, biblioteca, busca, detalhe da playlist e downloads concluídos oferece **Salvar na playlist**. A folha permite incluir ou remover o vídeo em várias playlists e criar uma nova com ele já selecionado. O painel de opções também mostra tamanho real em bytes, arquivo, duração, formato, resolução, taxas, codecs, metadados embutidos disponíveis, pasta, datas, link de origem e URI. A leitura ocorre fora da thread de interface e dados ausentes não são inventados. A exclusão pede confirmação e também remove o arquivo do celular, com solicitação do Android quando necessária. As alterações de membros são transacionais e não duplicam vídeos.

Na home, o seletor de ícones ao lado do título da biblioteca alterna entre cards com miniatura ampla e lista compacta. O controle tem borda discreta e destaque neutro na opção ativa. A escolha é salva nas configurações locais. Os itens não exibem mais o ícone de check circular.

Validação da revisão de navegação e playlists em 07/11/2026: APK otimizado gerado, 40 testes JVM aprovados, lint sem erros e teste instrumentado de membros aprovado no Galaxy A52. Conferidos os dois layouts da home, criação, busca e edição, seleção pelo menu, pré-seleção ao criar a partir de um vídeo e persistência após reabrir. A playlist de teste foi excluída e os 19 itens da biblioteca foram preservados.

Validação de capas, detalhes, importação e exclusão em 07/11/2026: 43 testes JVM e quatro testes instrumentados aprovados, APK otimizado gerado e lint sem erros. A migração preservou registros e ordem; a importação preservou a capa personalizada e não duplicou vídeos. No celular, conferidos escolha e exibição da capa, detalhes reais de um MP4, exclusão do arquivo de teste pela interface e remoção de seus membros nas playlists. A prévia de uma playlist pública mostrou os quatro vídeos e aceitou outro nome, sem criar downloads antes da confirmação. Os 19 vídeos da instalação principal foram preservados; os testes de exclusão usaram arquivos temporários na variante lab.

Reproduzir playlist ou tocar em um item dentro dela monta a fila exclusivamente com seus arquivos disponíveis, na ordem salva. O player mostra o nome da playlist e mantém próximo, anterior, aleatório e repetição dentro dessa fila. Abrir um arquivo pela biblioteca geral volta a montar a fila da biblioteca, mesmo se for o mesmo vídeo que já estava tocando.

Validação em 07/11/2026: 33 testes JVM, build e lint aprovados. No celular, conferidos criação com dois vídeos, fila restrita, edição para um vídeo, persistência após encerrar o processo e exclusão do agrupamento. A playlist de teste foi removida e os 19 registros originais da biblioteca permaneceram idênticos.

## Catálogos com mil vídeos

A implementação de paginação, migração e fila limitada está documentada em [docs/performance.md](docs/performance.md), junto dos comandos de teste e benchmark isolado.


## Continuidade dos downloads com tela apagada

A fila iniciada pelo usuário agora pertence a um serviço foreground de download, independente da tela do app. Ele mantém um wake lock durante as transferências e uma notificação compartilhada, que é removida ao terminar, cancelar ou interromper o trabalho. Ao abrir o app, notificações antigas de downloads sem fila ativa também são limpas. O WorkManager continua responsável pelas retomadas persistentes quando o Android interrompe o serviço ou nega sua inicialização em segundo plano.

Erros de socket, timeout, bloqueio de serviço em segundo plano e respostas temporárias do servidor devolvem o vídeo à fila e interrompem o consumo para uma tentativa com backoff. Assim, uma interrupção do sistema não transforma os vídeos seguintes em falhas. Vídeos indisponíveis e outros erros definitivos continuam no filtro Com erro. Fechar a tela do app ou bloquear o celular não cancela a fila; forçar a parada do aplicativo pelo Android exige abri-lo novamente.

Em Configurações há uma seção de downloads em segundo plano com o estado da otimização de bateria e acesso às configurações do Android. Se o aparelho entrar em Doze sem permitir o uso de bateria sem restrição, a fila aguarda a conexão voltar. No Android 15 ou superior, o limite de tempo do serviço dataSync também é tratado com preservação da fila. Referências: [Doze e App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) e [limites de serviços foreground](https://developer.android.com/develop/background-work/services/fgs/timeout).

A seleção nos downloads tem checkbox na extremidade direita, afastado da thumbnail, mantendo o card inteiro como área de seleção. Cards com erro ou cancelados usam uma superfície neutra sem contorno forte; a seleção mantém o destaque de fundo.

Validação em 07/11/2026: 52 testes JVM aprovados, build lab e otimizado e lint sem erros. No Galaxy A52 (Android 14), dois downloads reais terminaram após sair do app e apagar a tela; a notificação desapareceu ao concluir. Outro teste instrumentado confirmou o estado de erro e a remoção da notificação após uma URL inválida. Os testes usaram exclusivamente a variante lab e removeram os registros e arquivos temporários ao terminar.

Conferência visual no Galaxy A52 desbloqueado: checkbox à direita, thumbnail separada e cards sem contorno forte no tema escuro. Selecionar pelo corpo do card atualizou o destaque e a contagem; a seleção permaneceu ao rolar. A seleção foi cancelada ao encerrar a conferência, sem reenviar downloads.
