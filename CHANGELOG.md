<!-- Formato: https://keepachangelog.com. A seção da versão em gradle.properties (pluginVersion) vira o "O que há de novo" do plugin. -->

# T3W Productivity

## [Unreleased]

### Novidades

### Alterações

### Correções

## [0.3.0] - 2026-11-24

### Novidades

- "O que há de novo": depois de atualizar o plugin, uma notificação leva às novidades da versão; elas também aparecem
  na tela de atualização da IDE e no menu T3W Productivity.

## [0.2.0] - 2026-11-24

### Novidades

- Web components do Vaadin reconhecidos no HTML e no CSS (tags, atributos, `::part()`, atributos de estado e
  custom properties do Lumo), sem precisar de `node_modules`. Funciona no IntelliJ Ultimate.
- Introspecção automática do banco: depois que o Flyway roda, só o banco migrado é atualizado na janela Database, e
  bancos do escopo que nunca foram introspectados são introspectados ao abrir o projeto. Só data sources locais.
- Avisos de migration (não bloqueiam): edição de migration já mergeada, versão fora de ordem ou duplicada (com
  "Renumerar para agora") e função nova sem o prefixo `t3w_`.
- Matriz de ambientes (aba "Ambientes" da Tool Window): valor de cada ambiente para cada `switch (this)` do enum
  `Ambiente`, com filtro por ambiente e destaque para o que vem do `default`.
- Isolamento por worktree (desligado por padrão): porta e cookie de sessão próprios para apps rodando em worktrees.

## [0.1.0]

### Novidades

- Criar e ajustar migrations Flyway e selecionar a última migration na Project View.
- Presets de variáveis de ambiente por projeto/worktree, injetados nas run configurations.
- Refactor de PreparedStatement para parâmetros nomeados.
