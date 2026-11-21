# Catálogo grande e fila de downloads

O catálogo e as playlists locais ficam em `spix.db` (Room). A primeira abertura importa `spix/requests` e `spix_playlists/items` em uma transação. A marca de importação é gravada na mesma transação; os arquivos antigos permanecem intactos. Dados inválidos interrompem a importação e exibem a opção de tentar novamente.

Home, biblioteca, busca, downloads, playlists e editor usam Paging 3 com páginas de 40, carga inicial de 80, antecipação de 12 e janela de até 160 registros, sem placeholders. As linhas têm chave e tipo estáveis. Contagens, filtros, busca e ordenação são consultas SQL; a busca espera 250 ms entre alterações.

O progresso não regrava o catálogo. O registro ativo é publicado separadamente, limitado a uma atualização a cada 400 ms (com exceção de transições e conclusão). Checkpoints de retomada ficam em uma tabela separada, com intervalo de dois segundos. Home e downloads observam o progresso apenas no componente ativo.

Um único consumidor processa a fila em um serviço foreground iniciado pela ação do usuário, independente da Activity. O WorkManager mantém a recuperação persistente e, no fallback, processa até dez vídeos por execução antes de agendar a continuação. Um lote de mil vídeos não cria mil workers. O cancelamento de um item interrompe o processo nativo, limpa arquivos parciais e permite continuar a fila. Reinício do processo recupera itens em execução como pendentes.

A fila do player só é reconstruída quando a timeline muda. O catálogo é consultado fora da thread principal; selecionar outro item na mesma fila reutiliza a timeline. A ordem das playlists é preservada. O visual do player permanece igual.

O Coil compartilha um carregador com cache de memória de 16 MiB e disco de 128 MiB, duas buscas e uma decodificação concorrente. URLs e qualidade das thumbnails foram preservadas.

## Verificação

```sh
./gradlew :app:testLabUnitTest :app:connectedLabAndroidTest :app:lintLab
./gradlew :app:assembleOptimized
```

`lab` instala `com.pedroaba.spix.lab`, preservando a instalação pessoal. O teste de pasta escolhida por SAF requer uma permissão específica na instalação de laboratório; sem ela, esse teste é ignorado explicitamente.

`optimized` usa o ID pessoal e R8; instalar com `adb install -r` preserva os dados. Nunca desinstalar o app pessoal para medir desempenho.

## Benchmark reproduzível

O módulo `macrobenchmark` mede três iterações por cenário e gera traces Perfetto. O alvo é exclusivamente `com.pedroaba.spix.benchmark`, uma instalação separada, otimizada com R8 e profileable. O seeder só existe nessa variante. Ele cria capas distintas de 480 × 360, um pequeno MP4 local e playlists; não baixa mil vídeos da internet.

```sh
./gradlew :app:assemblePerformance :macrobenchmark:assemblePerformance
adb install -r app/build/outputs/apk/performance/app-performance.apk
adb install -r macrobenchmark/build/outputs/apk/performance/macrobenchmark-performance.apk
python3 scripts/benchmark_catalog.py --label after --sizes 19 100 400 1000 --screens home library downloads
```

O script só opera no pacote de benchmark e registra memória e frames. Os traces permitem investigar frames além do prazo. Para comparar versões, instalar o APK de referência com o mesmo ID `.benchmark` e executar o script antes de instalar a versão nova. O aparelho deve estar desbloqueado; mantenha a mesma taxa de atualização e condições térmicas.

## Resultado da rodada de 07/11/2026

- Build de `lab`, `performance` e `optimized`, 40 testes JVM e lint passaram.
- No Galaxy A52, passaram os testes de catálogo, importação transacional, persistência, filtros com acentos, ordenação de playlists, paginação distante e isolamento das invalidações de progresso. O teste de pasta SAF foi ignorado porque a instalação de laboratório não tinha uma permissão de pasta escolhida.
- O teste de UI passou por busca, editor de playlist e player com fila de 1.000 vídeos sintéticos.
- A migração da instalação pessoal foi comparada com o backup: os 19 registros conservaram ID, URL, data, URI do arquivo, duração, status e metadados. Os arquivos originais de preferências ficaram idênticos byte a byte.
- A home observou mediana de aproximadamente 101,6 MiB com 100 itens, 101,4 MiB com 400 e 101,7 MiB com 1.000. São amostras de PSS após rolagem, não uma medição do pico absoluto do processo.
- A meta de menos de 5% de quadros atrasados **não foi atingida** nas rodadas de Macrobenchmark. O reinício do aparelho e a atividade do sistema mudaram as condições entre rodadas; os números não demonstram uma melhoria causal de fluidez. Os traces indicam custo relevante de renderização, além do trabalho de listagem.
- Foi preparado um diagnóstico adicional sem Perfetto para separar a fluidez observada pelo HWUI do custo de tracing: `python3 scripts/benchmark_catalog.py --label hwui --method hwui --sizes 19 100 400 1000`. Essa confirmação ficou pendente após a desconexão USB.
- O APK `optimized` está compilado. A instalação pessoal está na versão debug com os dados migrados e conferidos; a instalação final do APK otimizado ficou pendente porque o aparelho deixou de aparecer no ADB.

Resultados resumidos: [performance-results-2026-11-07.json](performance-results-2026-11-07.json). Os traces completos e relatórios brutos desta sessão ficaram em `/tmp/spix-performance`; os backups pessoais ficaram fora do repositório, em `/tmp/spix-personal-before-final.tar` e `/tmp/spix-personal-after-migration.tar`.

## Instalação final e teste a 60 Hz

O usuário optou por testar 60 FPS e pediu para reduzir a carga dos testes no aparelho. A atividade solicita 60 Hz ao anexar a janela, preservando a resolução. O Android estava com `match-content-frame-rate-pref=0` (`SWITCHING_TYPE_NONE`), expondo somente o modo de 90 Hz ao app. Após autorização explícita, a preferência passou para `1` (troca automática dentro do grupo). Essa preferência de sistema também pode afetar outros apps; o modo padrão/preferido do painel continua sendo 90 Hz.

O APK otimizado com R8 foi instalado por atualização, e os 19 itens foram conferidos na biblioteca. Uma reprodução real carregou a fila com 19 itens, sem erro; foi pausada ao terminar a checagem. A tela foi confirmada no modo físico 2, com 60,000004 Hz e 1080 × 2400 pixels. Duas rolagens curtas, sem Perfetto, registraram 61 quadros, dos quais 2 atrasados (3,28%). Essa amostra pequena não certifica a meta anterior de três rodadas com mil itens a 60 Hz. Não foram repetidas as rodadas de carga, conforme solicitado.

Build otimizado, 40 testes JVM e lint passaram. Para voltar à política anterior de troca do sistema, o valor original era `0`; não foi restaurado porque o usuário autorizou habilitar a troca automática para usar 60 Hz no app. Não executar a matriz completa de benchmarks no aparelho pessoal automaticamente; priorizar checagens curtas e espaçadas.
