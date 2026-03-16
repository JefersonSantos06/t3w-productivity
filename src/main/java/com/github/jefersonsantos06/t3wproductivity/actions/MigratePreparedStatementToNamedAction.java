package com.github.jefersonsantos06.t3wproductivity.actions;

import com.github.jefersonsantos06.t3wproductivity.util.ParameterNamingUtils;
import com.github.jefersonsantos06.t3wproductivity.util.SqlSnippetUtils;
import com.github.jefersonsantos06.t3wproductivity.util.TextParsingUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigratePreparedStatementToNamedAction extends AnAction {

    private static final Pattern SUPER_CONNECTION_PREPARE_STATEMENT_CALL_PATTERN = Pattern.compile(
            "\\bsuper\\s*\\.\\s*getConnection\\s*\\(\\s*\\)\\s*\\.\\s*prepareStatement\\s*\\("
    );
    private static final Pattern CONNECTION_PREPARE_STATEMENT_CALL_PATTERN = Pattern.compile(
            "\\b(?:this\\s*\\.\\s*)?getConnection\\s*\\(\\s*\\)\\s*\\.\\s*prepareStatement\\s*\\("
    );
    private static final Pattern UNQUALIFIED_PREPARE_STATEMENT_CALL_PATTERN = Pattern.compile(
            "(?<!\\.)\\bprepareStatement\\s*\\("
    );
    private static final Pattern TYPE_REWRITE_PATTERN = Pattern.compile(
            "(?m)(^\\s*try\\s*\\(\\s*)(?:final\\s+)?PreparedStatement\\s+(\\w+)\\s*=\\s*"
    );
    private static final Pattern SETTER_CALL_START_PATTERN = Pattern.compile("(\\b\\w+\\b)\\.(set\\w+)\\s*\\(");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern PREPARE_NAMED_TO_STRING_PATTERN = Pattern.compile(
            "this\\.prepareNamedStatement\\((\\w+)\\.toString\\(\\)\\)"
    );
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "(?is)INSERT\\s+INTO\\s+[A-Za-z0-9_.]+\\s*\\(([^)]*)\\)\\s*.*?VALUES\\s*\\(([^)]*)\\)"
    );
    private static final Pattern STRING_BUILDER_SQL_PATTERN = Pattern.compile(
            "(?ms)^([ \\t]*)final\\s+StringBuilder\\s+(\\w+)\\s*=\\s*new\\s+StringBuilder\\(\\)\\s*;\\R((?:\\1\\s*\\2\\.append\\(\"(?:[^\"\\\\]|\\\\.)*\"\\)\\s*;\\R)+)"
    );
    private static final Pattern CONCAT_SQL_PATTERN = Pattern.compile(
            "(?ms)^([ \\t]*)String\\s+(\\w+)\\s*=\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+\\s*\\R?\\s*)+\"(?:[^\"\\\\]|\\\\.)*\"\\s*);"
    );
    private static final String THIS_PREPARE_NAMED_STATEMENT = "this.prepareNamedStatement(";


    @Override
    public void actionPerformed(final AnActionEvent event) {
        final Project project = event.getProject();
        final Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        final var selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            Messages.showWarningDialog(
                    project,
                    "Selecione um trecho de codigo com PreparedStatement para migrar.",
                    "Migrar PreparedStatement Para Named"
            );
            return;
        }

        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();
        final String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.isBlank()) {
            Messages.showWarningDialog(
                    project,
                    "A selecao esta vazia.",
                    "Migrar PreparedStatement Para Named"
            );
            return;
        }

        final var result = migrate(selectedText);
        if (!result.changed()) {
            Messages.showInfoMessage(
                    project,
                    result.reason() == null ? "Nenhuma alteracao necessaria." : result.reason(),
                    "Migrar PreparedStatement Para Named"
            );
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () ->
                editor.getDocument().replaceString(start, end, result.content())
        );
    }

    @Override
    public void update(final AnActionEvent event) {
        final var project = event.getProject();
        final var editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    public static MigrationResult migrate(final String source) {
        if (source == null || source.isBlank()) {
            return new MigrationResult(source, false, "Entrada vazia.");
        }

        final var setterCalls = collectIndexedSetterCalls(source);
        if (setterCalls.isEmpty()) {
            return new MigrationResult(source, false, "Nenhum setter indexado encontrado.");
        }

        final var namingContext = buildNamingContext(setterCalls, extractSqlHintsByIndex(source));
        var migrated = rewriteSetterCalls(source, setterCalls, namingContext);
        migrated = rewritePrepareStatementCalls(migrated);
        migrated = rewriteTryResourceTypes(migrated);
        migrated = rewriteSqlPlaceholders(migrated, namingContext);
        migrated = rewriteSqlDeclarations(migrated);
        migrated = cleanupExtraBlankLines(migrated);

        final var changed = !Objects.equals(source, migrated);
        return new MigrationResult(migrated, changed, changed ? null : "Nao foi possivel aplicar transformacao.");
    }

    private static NamingContext buildNamingContext(final List<SetterCall> setterCalls, final Map<Integer, String> sqlHintsByIndex) {
        final var indexToName = new LinkedHashMap<Integer, String>();
        final var expressionToName = new HashMap<String, String>();
        final var usedNames = new HashSet<String>();

        for (SetterCall call : setterCalls) {
            if (indexToName.containsKey(call.index())) {
                continue;
            }

            final var expressionKey = call.valueExpression().trim();
            String candidate = expressionToName.get(expressionKey);
            if (candidate == null) {
                final var sqlHint = sqlHintsByIndex.get(call.index());
                final var expressionPreferred = ParameterNamingUtils.extractExpressionPreferredName(call.valueExpression());
                final var getterBased = ParameterNamingUtils.extractGetterBasedName(call.valueExpression());

                if (expressionPreferred != null && !expressionPreferred.isBlank()) {
                    candidate = expressionPreferred;
                } else if (sqlHint != null && !sqlHint.isBlank()) {
                    candidate = sqlHint;
                } else if (getterBased != null && !getterBased.isBlank()) {
                    candidate = getterBased;
                } else {
                    candidate = "param" + call.index();
                }
                candidate = ParameterNamingUtils.sanitizeParameterName(candidate, call.index());
                candidate = ParameterNamingUtils.ensureUniqueName(candidate, usedNames);
                expressionToName.put(expressionKey, candidate);
            }
            usedNames.add(candidate);
            indexToName.put(call.index(), candidate);
        }
        return new NamingContext(indexToName);
    }

    private static String rewritePrepareStatementCalls(final String source) {
        String migrated = SUPER_CONNECTION_PREPARE_STATEMENT_CALL_PATTERN.matcher(source)
                .replaceAll(THIS_PREPARE_NAMED_STATEMENT);
        migrated = CONNECTION_PREPARE_STATEMENT_CALL_PATTERN.matcher(migrated)
                .replaceAll(THIS_PREPARE_NAMED_STATEMENT);
        return UNQUALIFIED_PREPARE_STATEMENT_CALL_PATTERN.matcher(migrated)
                .replaceAll(THIS_PREPARE_NAMED_STATEMENT);
    }

    private static String rewriteTryResourceTypes(final String source) {
        return TYPE_REWRITE_PATTERN.matcher(source).replaceAll("$1final var $2 = ");
    }

    private static String rewriteSqlPlaceholders(final String source, final NamingContext namingContext) {
        final StringBuilder out = new StringBuilder(source.length() + 64);
        final int[] placeholderIndex = { 1 };
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("\"\"\"", i)) {
                final int end = source.indexOf("\"\"\"", i + 3);
                if (end < 0) {
                    out.append(source.substring(i));
                    break;
                }
                out.append(replaceQuestionMarks(source.substring(i, end + 3), placeholderIndex, namingContext));
                i = end + 3;
                continue;
            }

            final char current = source.charAt(i);
            if (current == '"') {
                final int end = TextParsingUtils.findStringLiteralEnd(source, i + 1);
                if (end < 0) {
                    out.append(source.substring(i));
                    break;
                }
                out.append(replaceQuestionMarks(source.substring(i, end + 1), placeholderIndex, namingContext));
                i = end + 1;
                continue;
            }
            out.append(current);
            i++;
        }
        return out.toString();
    }

    private static String replaceQuestionMarks(final String text, final int[] placeholderIndex, final NamingContext namingContext) {
        if (text.indexOf('?') < 0) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            final char current = text.charAt(i);
            if (current == '?') {
                out.append(':').append(namingContext.nameForIndex(placeholderIndex[0]));
                placeholderIndex[0]++;
            } else {
                out.append(current);
            }
        }
        return out.toString();
    }

    private static String rewriteSetterCalls(final String source, final List<SetterCall> setterCalls, final NamingContext namingContext) {
        final StringBuilder out = new StringBuilder(source.length() + 64);
        int cursor = 0;
        final Set<String> seenAssignments = new HashSet<>();

        for (SetterCall call : setterCalls) {
            if (call.startOffset() < cursor || call.endOffset() > source.length()) {
                continue;
            }

            out.append(source, cursor, call.startOffset());
            final String parameterName = namingContext.nameForIndex(call.index());
            final List<String> args = new ArrayList<>(call.arguments());
            args.set(0, "\"" + parameterName + "\"");
            final String rewritten = call.variableName() + "." + call.methodName() + "(" + String.join(", ", args) + ");";

            final String dedupeKey = call.variableName() + "|" + call.methodName() + "|" + parameterName + "|"
                                     + ParameterNamingUtils.normalizedExpression(call.valueExpression());
            if (seenAssignments.add(dedupeKey)) {
                out.append(rewritten);
            }
            cursor = call.endOffset();
        }
        out.append(source.substring(cursor));
        return out.toString();
    }

    private static List<SetterCall> collectIndexedSetterCalls(final String source) {
        final var calls = new ArrayList<SetterCall>();
        final var matcher = SETTER_CALL_START_PATTERN.matcher(source);
        while (matcher.find()) {
            final int openParenIndex = source.indexOf('(', matcher.start());
            if (openParenIndex < 0) {
                continue;
            }
            final int closeParenIndex = TextParsingUtils.findMatchingParenthesis(source, openParenIndex);
            if (closeParenIndex < 0) {
                continue;
            }
            final List<String> args = TextParsingUtils.splitTopLevelArguments(source.substring(openParenIndex + 1, closeParenIndex));
            if (args.isEmpty()) {
                continue;
            }
            final String firstArg = args.getFirst().trim();
            if (!DIGIT_PATTERN.matcher(firstArg).matches()) {
                continue;
            }

            int endOffset = closeParenIndex + 1;
            while (endOffset < source.length() && Character.isWhitespace(source.charAt(endOffset))) {
                endOffset++;
            }
            if (endOffset < source.length() && source.charAt(endOffset) == ';') {
                endOffset++;
            }

            calls.add(new SetterCall(
                    matcher.start(),
                    endOffset,
                    matcher.group(1),
                    matcher.group(2),
                    Integer.parseInt(firstArg),
                    args,
                    args.size() > 1 ? args.get(1).trim() : ""
            ));
        }
        return calls;
    }

    private static Map<Integer, String> extractSqlHintsByIndex(final String source) {
        final var hints = new HashMap<Integer, String>();
        int placeholderIndex = 0;

        final StringBuilder token = new StringBuilder();
        final List<String> tokens = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            final char current = source.charAt(i);
            if (!inString) {
                if (current == '"') {
                    inString = true;
                    token.setLength(0);
                    tokens.clear();
                }
                continue;
            }

            if (escaped) {
                token.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                token.append(current);
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = false;
                token.setLength(0);
                tokens.clear();
                continue;
            }

            if (current == '?') {
                placeholderIndex++;
                final String hint = SqlSnippetUtils.inferHintFromSqlTokens(tokens);
                if (hint != null && !hint.isBlank()) {
                    hints.put(placeholderIndex, hint);
                }
                token.setLength(0);
                continue;
            }

            if (Character.isLetterOrDigit(current) || current == '_' || current == '.') {
                token.append(current);
                continue;
            }

            if (!token.isEmpty()) {
                tokens.add(token.toString());
                if (tokens.size() > 12) {
                    tokens.removeFirst();
                }
                token.setLength(0);
            }
            if (current == '=') {
                tokens.add("=");
                if (tokens.size() > 12) {
                    tokens.removeFirst();
                }
            }
        }

        hints.putAll(extractInsertValuesHintsByIndex(source, hints));
        return hints;
    }

    private static Map<Integer, String> extractInsertValuesHintsByIndex(
            final String source,
            final Map<Integer, String> existingHints
    ) {
        final var insertHints = new HashMap<Integer, String>();
        final Matcher matcher = INSERT_PATTERN.matcher(source);
        while (matcher.find()) {
            final List<String> columns = SqlSnippetUtils.splitCommaSeparated(matcher.group(1));
            final List<String> valueItems = SqlSnippetUtils.splitCommaSeparated(matcher.group(2));
            if (columns.isEmpty() || valueItems.isEmpty()) {
                continue;
            }

            int sqlPlaceholderIndex = SqlSnippetUtils.countSqlPlaceholdersUntil(source, matcher.start()) + 1;
            final int limit = Math.min(columns.size(), valueItems.size());
            for (int i = 0; i < limit; i++) {
                if (valueItems.get(i).indexOf('?') < 0) {
                    continue;
                }
                final String columnHint = ParameterNamingUtils.toLowerCamel(SqlSnippetUtils.stripQualifier(columns.get(i)));
                if (columnHint.isBlank()) {
                    sqlPlaceholderIndex++;
                    continue;
                }
                final String previous = existingHints.get(sqlPlaceholderIndex);
                if (previous == null || SqlSnippetUtils.isLowSignalSqlHint(previous)) {
                    insertHints.put(sqlPlaceholderIndex, columnHint);
                }
                sqlPlaceholderIndex++;
            }
        }
        return insertHints;
    }

    private static String rewriteSqlDeclarations(final String source) {
        String migrated = rewriteStringBuilderDeclarations(source);
        migrated = rewriteConcatenatedStringDeclarations(migrated);
        return PREPARE_NAMED_TO_STRING_PATTERN.matcher(migrated).replaceAll("this.prepareNamedStatement($1)");
    }

    private static String rewriteStringBuilderDeclarations(final String source) {
        Matcher matcher = STRING_BUILDER_SQL_PATTERN.matcher(source);
        String result = source;
        while (matcher.find()) {
            final String indent = matcher.group(1);
            final String varName = matcher.group(2);
            final List<String> fragments = SqlSnippetUtils.extractQuotedFragments(matcher.group(3));
            if (fragments.isEmpty()) {
                continue;
            }

            final boolean insertStatement = fragments.stream()
                    .map(String::stripLeading)
                    .anyMatch(fragment -> fragment.toUpperCase(Locale.ROOT).startsWith("INSERT INTO"));

            final StringBuilder replacement = new StringBuilder();
            replacement.append(indent).append("final var ").append(varName).append(" = \"\"\"\n");
            final String contentIndent = indent + (insertStatement ? "    " : "        ");
            for (String fragment : fragments) {
                replacement.append(contentIndent)
                        .append(SqlSnippetUtils.normalizeInsertValuesLine(fragment.stripTrailing(), insertStatement))
                        .append("\n");
            }
            replacement.append(contentIndent).append("\"\"\";\n");

            result = result.substring(0, matcher.start()) + replacement + result.substring(matcher.end());
            matcher = STRING_BUILDER_SQL_PATTERN.matcher(result);
        }
        return result;
    }

    private static String rewriteConcatenatedStringDeclarations(final String source) {
        Matcher matcher = CONCAT_SQL_PATTERN.matcher(source);
        String result = source;
        while (matcher.find()) {
            final String indent = matcher.group(1);
            final String varName = matcher.group(2);
            final List<String> fragments = SqlSnippetUtils.extractQuotedFragments(matcher.group(3));
            if (fragments.isEmpty()) {
                continue;
            }

            final StringBuilder replacement = new StringBuilder();
            replacement.append(indent).append("final var ").append(varName).append(" = \"\"\"\n");
            final String contentIndent = indent + "        ";
            for (String fragment : fragments) {
                replacement.append(contentIndent).append(fragment.stripTrailing()).append("\n");
            }
            replacement.append(contentIndent).append("\"\"\";\n");

            result = result.substring(0, matcher.start()) + replacement + result.substring(matcher.end());
            matcher = CONCAT_SQL_PATTERN.matcher(result);
        }
        return result;
    }

    private static String cleanupExtraBlankLines(final String source) {
        return source.replaceAll("(?m)(^[ \\t]*\\R){2,}", "\n");
    }

    private record NamingContext(Map<Integer, String> indexToName) {
        private String nameForIndex(final int index) {
            return indexToName.getOrDefault(index, "param" + index);
        }
    }

    private record SetterCall(
            int startOffset,
            int endOffset,
            String variableName,
            String methodName,
            int index,
            List<String> arguments,
            String valueExpression
    ) {
    }

    public record MigrationResult(String content, boolean changed, String reason) {
    }
}
