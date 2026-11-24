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
- **Isolamento por worktree** (ligado por padrão; Settings → Tools → T3W Productivity): cada worktree recebe um slot
  (1, 2, ...; o checkout principal é 0 e não muda nada). Ao rodar um app numa worktree, o plugin lê o
  `application.properties` do módulo e injeta `SERVER_PORT` (porta + 100 × slot), `SERVER_SERVLET_SESSION_COOKIE_NAME`
  (cookie com sufixo `_<slot>`) e `T3W_DEV_DB_APPNAME_PREFIX` (`<worktree>-`). Valores definidos em presets/overrides
  sempre vencem. O slot aparece (e pode ser trocado) na Tool Window, e o console da execução mostra porta e cookie.
- **Matriz de ambientes** (aba "Ambientes" da Tool Window): valor de cada ambiente para cada método do enum `Ambiente`
  que decide por `switch (this)`, além dos argumentos do construtor. Valores vindos do `default` em cinza itálico e
  ambientes não tratados em vermelho. Filtros: um ambiente específico (mostra todos os valores dele), busca por
  método/valor e "só valores vindos do default". Duplo clique abre o código.
- **Migrar PreparedStatement para Named**: refactor do trecho selecionado no editor.
- **Introspecção automática do banco** (POC; precisa do plugin Database), só para data sources PostgreSQL locais
  (`localhost`/`127.0.0.1`):
  - Quando o log de uma execução mostra o resultado do Flyway, o banco migrado é atualizado (refresh incremental, só
    aquele banco). Sem migrations novas, atualiza uma vez por sessão da IDE quando a versão do Flyway muda.
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

## Desenvolvimento

- Rodar plugin em sandbox: `./gradlew runIde` (Windows: `gradlew.bat runIde`)
- Rodar testes: `./gradlew test`
- Rodar o plugin / os testes que valem para o IntelliJ Community: `./gradlew runIdeCommunity` / `./gradlew testCommunity`
- Verificar compatibilidade: `./gradlew verifyPlugin`
- Empacotar plugin: `./gradlew buildPlugin`
- Instalar o plugin: `https://pub-10748318660e44e292dde0d6ee0310b3.r2.dev/updatePlugins.xml` 
