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
