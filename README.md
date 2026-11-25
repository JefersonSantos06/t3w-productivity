## IDEs

- **IntelliJ IDEA** (com ou sem assinatura Ultimate, e o antigo Community): todas as funções. A introspecção precisa do
  plugin Database, que não existe no Community 2025.2.
- **DataGrip**: migrations Flyway (criar, ajustar nome e avisos, com a pasta do projeto aberta), introspecção automática
  e "O que há de novo". Variáveis de ambiente, isolamento por worktree, matriz de ambientes, o refactor de
  PreparedStatement e os pontos de exceção dependem de Java e não aparecem.

## Funcionalidades

- **Migrations Flyway**: criar migration (`V<yyyyMMddHHmm>__<descricao>.sql`), ajustar nome de uma migration existente e
  selecionar a última migration na Project View. Versões duplicadas no mesmo minuto são evitadas automaticamente.
- **Variáveis de ambiente** injetadas nas run configurations do tipo `Application`, resolvidas em camadas com
  prioridade **Override do projeto > Preset ativo > Global**.
  - *Global* e *Presets* valem para todos os projetos/worktrees (Settings → Tools → T3W Productivity).
  - Cada projeto/worktree escolhe o preset ativo no combobox da Tool Window "T3W Productivity".
  - Editar um valor na Tool Window cria um override só daquele projeto; "Salvar Overrides Como Preset" transforma os
    overrides em um preset reutilizável em outras worktrees.
  - A lista de variáveis visíveis na Tool Window é global (vazia = mostra todas).
  - "Gerar Presets do Ambiente" (página de Presets, aberta a partir do projeto jbear) lê o enum `Ambiente` pelo índice
    do IntelliJ e cria/atualiza um preset por ambiente (exceto TDD e DEV) com `T3W_DEV_AMBIENTE_OVERRIDE=<CODIGO>` e
    `T3W_DEV_DB_NAME=<codigo>`. Regerar só atualiza essas duas chaves; nada é removido.
- **Avisos de migration** (só avisam, nada é bloqueado), comparando com a branch padrão do remoto (`origin/HEAD`,
  `origin/master` ou `origin/main`, como está no último fetch):
  - Editar uma migration que já está na base: ela não chega aos bancos que já a rodaram. Atalhos: criar nova migration e
    ver a diferença com a base.
  - Migration da branch com versão anterior à última da base (o Flyway vai ignorá-la) ou com versão duplicada (o Flyway
    falha). Atalho "Renumerar para agora"; depois de um merge/pull também aparece uma notificação.
  - Função nova sem o prefixo `t3w_` (funções que já existem na base são ignoradas).
- **Isolamento por worktree** (desligado por padrão; ative em Settings → Tools → T3W Productivity): cada worktree recebe um slot
  (1, 2, ...; o checkout principal é 0 e não muda nada). Ao rodar um app numa worktree, o plugin lê o
  `application.properties` do módulo e injeta `SERVER_PORT` (porta + 100 × slot), `SERVER_SERVLET_SESSION_COOKIE_NAME`
  (cookie com sufixo `_<slot>`) e `T3W_DEV_DB_APPNAME_PREFIX` (`<worktree>-`). Valores definidos em presets/overrides
  sempre vencem. Com ele ligado, o slot aparece (e pode ser trocado) na Tool Window, e o console da execução mostra
  porta e cookie.
- **Matriz de ambientes** (aba "Ambientes" da Tool Window): valor de cada ambiente para cada método do enum `Ambiente`
  que decide por `switch (this)`, além dos argumentos do construtor. Valores vindos do `default` em cinza itálico e
  ambientes não tratados em vermelho. Filtros: um ambiente específico (mostra todos os valores dele), busca por
  método/valor e "só valores vindos do default". Duplo clique abre o código.
- **Migrar PreparedStatement para Named**: refactor do trecho selecionado no editor.
- **Pontos de exceção** (debug Java, só no IntelliJ): simulam uma exceção numa linha sem mexer no código, para testar o
  tratamento de erros.
  - No editor, na linha desejada: botão direito → T3W Productivity → Adicionar Ponto De Exceção. A exceção é uma
    expressão Java avaliada no contexto da linha (pode usar variáveis locais); o padrão é
    `new RuntimeException("T3W: exceção de teste")`. Para mudar a expressão, rode a ação de novo na mesma linha ou edite
    o campo "Evaluate and log" do breakpoint (como num logpoint, mas a expressão é lançada em vez de logada).
  - Rodando em debug, toda vez que a linha vai executar a exceção é lançada na thread e o app segue sem pausar (o
    breakpoint é "não suspender"). Condição e contagem de passagens do breakpoint também valem.
  - O ponto aparece com o ícone de raio no gutter e no grupo "Pontos De Exceção T3W" da janela Breakpoints
    (⌘⇧F8 / Ctrl+Shift+F8). Para desligar, desmarque o checkbox do ponto ou do grupo, inclusive com a sessão rodando.
    Para remover, apague o breakpoint.
  - Exceções checadas são lançadas mesmo que o método não as declare (`throws`). A JVM não confere isso, então o
    chamador pode receber, por exemplo, uma `IOException` que o compilador diria impossível.
  - Usa o mesmo mecanismo da ação nativa "Throw Exception" do debugger (`ThreadReference.stop` do JDWP), que continua
    funcionando no JDK 20+ (onde `Thread.stop()` só lança `UnsupportedOperationException`). Testado com o app no JDK 21
    e no 25, em threads comuns e virtuais.
  - Limitação do JDK: numa thread comum (de plataforma), a JVM deixa a thread interrompida junto com a exceção. Depois
    de tratá-la, a próxima chamada bloqueante dessa thread (`sleep`, `wait`, `join`, `BlockingQueue.take`...) lança
    `InterruptedException`. Threads virtuais não ficam interrompidas. A ação nativa "Throw Exception" tem o mesmo efeito.
- **Introspecção automática do banco** (precisa do plugin Database), só para data sources PostgreSQL locais
  (`localhost`/`127.0.0.1`):
  - Quando o log de uma execução mostra o resultado do Flyway, o banco migrado é atualizado (refresh incremental, só
    aquele banco). Sem migrations novas, atualiza uma vez por sessão da IDE quando a versão do Flyway muda.
  - Funciona entre IDEs: o app rodando no IntelliJ atualiza também os data sources do DataGrip (com o plugin instalado
    nos dois). A IDE que roda o app grava o resultado em `~/.t3w-productivity/flyway-events.log`, e as IDEs com o plugin
    Database verificam o arquivo a cada 2 s.
  - Cerca de 1 minuto depois de abrir o projeto, bancos do escopo que nunca foram introspectados (ou perderam o cache)
    são introspectados.
  - Um refresh por vez, esperando a sincronização em andamento; nada roda se a porta do banco/proxy estiver fechada.
    O andamento aparece em notificações e no `idea.log` ("Introspecção agendada").
- **Web components do Vaadin** (`vaadin-*`) reconhecidos pela IDE, sem precisar de `node_modules`:
  - HTML: autocomplete de tags e atributos, documentação rápida e sem aviso de tag/atributo desconhecido.
  - CSS: tags em seletores, `::part(...)`, atributos de estado (`[has-value]`, `[focused]`...) e as custom
    properties do Lumo em `var(--lumo-...)`.
  - Funciona no IntelliJ Ultimate (precisa dos plugins JavaScript e CSS habilitados). No Community o plugin carrega
    normalmente, mas este recurso não tem efeito.
  - Os dados ficam em `src/main/resources/web-types/vaadin.web-types.json`, gerado para uma versão do Vaadin com
    `node scripts/generate-vaadin-web-types.mjs <versão>` (padrão 24.8.6).
- **O que há de novo**: as novidades da versão (seção do `CHANGELOG.md`) aparecem na tela de atualização da IDE, numa
  notificação na primeira abertura de projeto depois de atualizar e em Tools → T3W Productivity → O Que Há De Novo.

## Desenvolvimento

- Rodar plugin em sandbox: `./gradlew runIde` (Windows: `gradlew.bat runIde`)
- Rodar testes: `./gradlew test`
- Rodar o plugin / os testes que valem para o IntelliJ Community: `./gradlew runIdeCommunity` / `./gradlew testCommunity`
- Rodar o plugin / os testes que valem para o DataGrip: `./gradlew runIdeDataGrip` / `./gradlew testDataGrip` (o
  teste confere que o plugin carrega sem Java; código que usa Java fica no `t3w-java.xml`)
- Verificar compatibilidade das APIs com o DataGrip: `./gradlew verifyPlugin`
- Empacotar plugin: `./gradlew buildPlugin`
- Lançar uma versão:
  1. Descrever as mudanças em `## [Unreleased]` no `CHANGELOG.md` (grupos Novidades, Alterações, Correções).
  2. Subir `pluginVersion` no `gradle.properties` e rodar `./gradlew patchChangelog` (o Unreleased vira a seção da
     versão; é ela que aparece no "O que há de novo").
  3. Fazer merge na `main` e rodar o workflow "Build and Publish Plugin to R2" no GitHub Actions.
- Instalar o plugin (IntelliJ ou DataGrip: Settings → Plugins → ⚙ → Manage Plugin Repositories):
  `https://pub-10748318660e44e292dde0d6ee0310b3.r2.dev/updatePlugins.xml` 
