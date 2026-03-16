package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.ParameterNamingUtils;
import junit.framework.TestCase;

import java.util.HashSet;
import java.util.Set;

public class ParameterNamingUtilsTest extends TestCase {

    public void testExtractExpressionPreferredNameFromPlainIdentifier() {
        assertEquals("codigoBanco", ParameterNamingUtils.extractExpressionPreferredName("codigoBanco"));
    }

    public void testExtractExpressionPreferredNameFromValueOf() {
        assertEquals(
                "dataLancamento",
                ParameterNamingUtils.extractExpressionPreferredName("java.sql.Date.valueOf(dataLancamento)")
        );
    }

    public void testExtractExpressionPreferredNameFromEnumAccessor() {
        assertEquals(
                "foneEmailTipoCliente",
                ParameterNamingUtils.extractExpressionPreferredName("FoneEmailTipo.CLIENTE.getCodigo()")
        );
    }

    public void testExtractExpressionPreferredNameReturnsNullWhenNotMatched() {
        assertNull(ParameterNamingUtils.extractExpressionPreferredName("cliente.getNome()"));
    }

    public void testExtractGetterBasedNameUsesLastGetter() {
        assertEquals("codigo", ParameterNamingUtils.extractGetterBasedName("cliente.getBancoConta().getCodigo()"));
    }

    public void testExtractGetterBasedNameWithIsPrefix() {
        assertEquals("ativo", ParameterNamingUtils.extractGetterBasedName("cliente.isAtivo()"));
    }

    public void testSanitizeParameterName() {
        assertEquals("idBanc", ParameterNamingUtils.sanitizeParameterName("id_banc", 1));
        assertEquals("param2", ParameterNamingUtils.sanitizeParameterName("@@@", 2));
    }

    public void testEnsureUniqueName() {
        Set<String> used = new HashSet<>();
        used.add("param");
        used.add("param2");
        assertEquals("param3", ParameterNamingUtils.ensureUniqueName("param", used));
    }

    public void testCaseConversionHelpers() {
        assertEquals("cfdi", ParameterNamingUtils.toLowerCamel("CFDI"));
        assertEquals("MxUsoDelCfdi", ParameterNamingUtils.toPascalCase("mx_uso_del_cfdi"));
        assertEquals("mxUsoDelCfdi", ParameterNamingUtils.toCamelCaseFromUnderscore("mx_uso_del_cfdi", false));
    }

    public void testNormalizedExpressionRemovesWhitespace() {
        assertEquals("cliente.getNomeFantasia()", ParameterNamingUtils.normalizedExpression(" cliente.getNomeFantasia ( ) "));
    }
}
