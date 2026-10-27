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

## Desenvolvimento

- Rodar plugin em sandbox: `./gradlew runIde` (Windows: `gradlew.bat runIde`)
- Rodar testes: `./gradlew test`
- Verificar compatibilidade: `./gradlew verifyPlugin`
- Empacotar plugin: `./gradlew buildPlugin`
- Instalar o plugin: `https://pub-10748318660e44e292dde0d6ee0310b3.r2.dev/updatePlugins.xml` 
