# Spix — design mobile

Criado em 02/11/2026 no FigCraft pelo MCP, com base nas notas e nos dois concepts em `Projetos/spix` no Obsidian. Direção visual YouTube, já escolhida no contexto do projeto.

## Arquivos

- `spix-mobile.figcraft`: documento nativo editável, com três páginas.
- `spix-preview.png`: comparação entre o primeiro uso e a biblioteca preenchida.
- `spix-telas.png`: visão geral das telas e estados.
- `spix-componentes.png`: fundamentos e biblioteca de componentes.
- `spix-tokens.json`: variáveis para reimportar no FigCraft.
- `spix-tokens.css`: exportação de tokens como propriedades CSS; não é implementação do aplicativo.

## Documento

1. **Telas e protótipo** — 11 telas de 390 × 844 e um bottom sheet: início vazio, início preenchido, prévia de link, downloads, biblioteca, player, busca, link inválido, player pausado, downloads concluídos e busca sem resultados.
2. **Componentes e tokens** — 28 componentes, incluindo 16 ícones vetoriais; 23 variáveis semânticas e 9 instâncias compartilhadas nas telas.
3. **Contexto e referências** — concepts originais incorporados, decisões, comportamento esperado e critérios para implementação.

## Revisar o protótipo

Abra `spix-mobile.figcraft` no FigCraft. Na primeira página, selecione uma tela e use **Present** ou **Preview**. Há quatro fluxos iniciais: primeiro uso, acervo, validação e busca sem resultados.

- Campo de link ou botão de download no início → prévia fixa de conteúdo.
- Confirmar o download → fila de downloads.
- Aba Concluídos → exemplo de download concluído → biblioteca.
- Abas inferiores → início, downloads e biblioteca.
- Miniplayer ou a música Depois do sol → player; o controle central alterna os estados de reprodução e pausa.
- Menu da música Depois do sol → bottom sheet com reprodução e fechamento.
- Busca → exemplo de resultado; tocar no campo demonstra um exemplo sem resultados.

São 64 conexões de navegação. Os exemplos não recebem texto, baixam arquivos, reproduzem áudio ou persistem dados. Os outros controles representam a especificação visual e ainda não têm comportamento funcional.

## Decisões

Superfície branca, texto #0F0F0F e apoio #606060. Revisão solicitada pelo usuário: vermelho #C30027 nas ações, no progresso e na navegação ativa; seleção #FCE8EC e foco azul #065FD4. Erro usa #A61C32. Indicadores offline usam circle-check completo; voltar e recolher usam setas completas; Wi-Fi foi redesenhado. Roboto foi usada como fonte disponível no ambiente. As capas são ilustrações vetoriais editáveis, com títulos e artistas ilustrativos dos concepts.

## Verificação

Revisadas renderizações das telas principais, estados e componentes. Conferidos fonte disponível, referências de destino do protótipo, presença das três páginas e ausência de sequências de quebra de linha literal nos textos. Corrigidos texto de apoio e alinhamento do botão de conclusão. Não foram realizados testes em dispositivos, com usuários ou leitor de tela. Plataforma, stack, adaptação responsiva e implementação continuam pendentes.
