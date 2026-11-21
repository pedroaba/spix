# Layout aprovado → Android

Referência: sessão compartilhada do **Spix — Mobile Design**, consultada em 05/11/2026, página **01 · Telas e protótipo**. O arquivo local de referência está em `design/spix-mobile.figcraft`. O desenho foi consultado; não foi alterado.

| Frame no FigCraft | Implementação |
| --- | --- |
| 01 / Início · biblioteca vazia (6) | Home com título, campo de link nativo, ação principal, filtros e estado vazio. |
| 02 / Início · com músicas (67) | Progresso real do download acima da biblioteca e lista compacta com capa, título, autor, duração, indicador offline e opções. |
| 03 / Adicionar · prévia do link (194) | Tela própria de conferência com link editável, prévia, destino, qualidade e ações Baixar / Trocar link. Também comporta playlists. |
| 04 / Downloads · fila e recuperação (236) | Abas Em andamento / Concluídos; grupos de fila e itens interrompidos; cartões com progresso, bytes, velocidade, ETA, cancelamento e nova tentativa. |
| 05 / Biblioteca · acervo offline (326) | Capas de 56 dp, filtros, ordenação por nome / mais recentes, indicador offline, menu e rodapé informativo. |
| 07 / Busca · resultados (497) | Campo de busca, contagem de resultados, mesmas linhas de conteúdo e ação Adicionar por link. |
| 08 / Validação · link inválido (572) | Erro junto ao campo, botão desabilitado, instruções e ação para limpar o link. |
| 10 / Downloads · concluído (864) | Capa, informações do arquivo, confirmação de conclusão e botão Ver na biblioteca. |
| 11 / Busca · sem resultados (938) | Estado vazio e ação para voltar à inclusão por link. |
| 12 / Opções da música (1001) | Bottom sheet com capa e informações, Ouvir agora, estado offline e Fechar. |

Por solicitação posterior do usuário, o campo de links, a barra inferior Material 3 e seus ícones foram restaurados à versão anterior ao alinhamento com o FigCraft. Margens de conteúdo de 20 dp, superfícies, tipografia e cores acompanham a referência e o tema do app. Os alvos de toque continuam com área de pelo menos 48 dp onde aplicável. Textos reais longos são abreviados nas listas; a folha de opções mostra mais informações.

O player e o miniplayer aprovados foram preservados. Configurações continuam acessíveis pela engrenagem no cabeçalho; como não há tela de configurações no documento aprovado, seus controles funcionais existentes foram mantidos.

O protótipo mostra pausa de download, mas o motor atual oferece cancelamento e nova tentativa. A interface apresenta essas ações com seus nomes e ícones reais. Não simula pausa, retomada byte a byte, tamanho estimado ou duração que o provedor de metadados não informou. Capas e conteúdos ilustrativos do protótipo não são inseridos no acervo real.

## Verificação

Build debug, lint e 28 testes JVM passaram. No Galaxy A52 foram conferidos início nos dois temas, biblioteca, opções de conteúdo, busca com e sem resultados, validação de link, prévia de conteúdo, downloads concluídos e remoção de um item de teste. Um download real mostrou 42% na home, concluiu e foi removido com seu arquivo. A navegação foi verificada partindo da aba Concluídos: um novo envio seleciona Em andamento, e a aba Início volta para a home sem restaurar a prévia. Os 19 registros originais foram preservados. O arquivo do player permaneceu idêntico ao início deste ajuste.
