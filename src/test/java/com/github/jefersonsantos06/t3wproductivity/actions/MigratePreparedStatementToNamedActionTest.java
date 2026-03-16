package com.github.jefersonsantos06.t3wproductivity.actions;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class MigratePreparedStatementToNamedActionTest extends BasePlatformTestCase {

    public void testMigratesTextBlockAndIndexedSetters() {
        final var source = """
                @Override
                public Cliente update(final Cliente cliente) throws SQLException {
                    String sql = ""\"
                            UPDATE cliente.cliente
                            SET documento_tipo=?,
                                cgc=?,
                                ativo=?
                            WHERE registro = ?
                            RETURNING *
                        ""\";
                
                    try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql)) {
                        pstmt.setString(1, cliente.getDocumentoTipo() != null ? cliente.getDocumentoTipo().getCodigo() : null);
                        pstmt.setString(2, cliente.getDocumentoTipo() != null && StringUtils.isNotBlank(cliente.getCGC()) ? cliente.getDocumentoTipo().desformata(cliente.getCGC()) : null);
                        pstmt.setString(3, cliente.isAtivo() ? "S" : "N");
                        pstmt.setInt(4, cliente.getRegistro());
                
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                        }
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "SET documento_tipo=:");
        assertContains(result.content(), "cgc=:");
        assertContains(result.content(), "ativo=:");
        assertContains(result.content(), "registro = :");
        assertContains(result.content(), "pstmt.setString(\"");
        assertContains(result.content(), "pstmt.setInt(\"");
        assertNotContains(result.content(), "pstmt.setString(1");
        assertNotContains(result.content(), "pstmt.setInt(4");
    }

    public void testMigratesStringBuilderSql() {
        final var source = """
                @Override
                public List<CFOP> findAllByPrefixo(final String prefixo) throws SQLException {
                    final StringBuilder sql = new StringBuilder();
                    sql.append("SELECT * ");
                    sql.append("FROM financeiro.cfop ");
                    sql.append("WHERE LENGTH(TRIM(id_cfo)) = 4 ");
                    sql.append("  AND SUBSTR(id_cfo, 1, 1) = ? ");
                    sql.append("  AND SUBSTR(id_cfo, 3, 2) NOT IN ('00', '50') ");
                    sql.append("ORDER BY id_cfo ");
                
                    try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql.toString())) {
                        pstmt.setString(1, prefixo);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return parseAll(rs);
                        }
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "SUBSTR(id_cfo, 1, 1) = :prefixo");
        assertContains(result.content(), "pstmt.setString(\"prefixo\", prefixo)");
        assertNotContains(result.content(), "pstmt.setString(1, prefixo)");
    }

    public void testMigratesInlinePrepareStatementSql() {
        final var source = """
                @Override
                public Cliente atualizaPonto(final String cgc, final String latitude, final String longitude) throws SQLException {
                    try (PreparedStatement pstmt = super.getConnection().prepareStatement("UPDATE cliente.cliente SET gmaps_latitude = ?, gmaps_longitude = ? WHERE cgc = ? RETURNING *")) {
                        pstmt.setString(1, latitude);
                        pstmt.setString(2, longitude);
                        pstmt.setString(3, cgc);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                        }
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertContains(result.content(), "SET gmaps_latitude = :latitude, gmaps_longitude = :longitude WHERE cgc = :cgc");
        assertContains(result.content(), "pstmt.setString(\"latitude\", latitude)");
        assertContains(result.content(), "pstmt.setString(\"longitude\", longitude)");
        assertContains(result.content(), "pstmt.setString(\"cgc\", cgc)");
    }

    public void testReusesNamedSetterWhenExpressionRepeats() {
        final var source = """
                public List<CinemaSiteFilme> findByInput(final String input, final int limit) throws SQLException {
                    final var sql = ""\"
                            SELECT f.*
                            FROM cinemasite.filme f
                            WHERE f.codigo_antigo = ?
                               OR unaccent(f.titulo_portugues) ILIKE '%'||unaccent(?)||'%'
                               OR unaccent(f.titulo_ingles) ILIKE '%'||unaccent(?)||'%'
                            LIMIT ?
                        ""\";
                
                    try (final var pstmt = getConnection().prepareStatement(sql)) {
                        pstmt.setString(1, input);
                        pstmt.setString(2, input);
                        pstmt.setString(3, input);
                        pstmt.setInt(4, limit);
                        try (final var rs = pstmt.executeQuery()) {
                            return parseAll(rs);
                        }
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertContains(result.content(), "f.codigo_antigo = :input");
        assertContains(result.content(), "ILIKE '%'||unaccent(:input)||'%'");
        assertContains(result.content(), "LIMIT :limit");
        assertEquals(1, countOccurrences(result.content(), "pstmt.setString(\"input\", input)"));
        assertContains(result.content(), "pstmt.setInt(\"limit\", limit)");
    }

    public void testPreservesSetObjectTypeArgument() {
        final var source = """
                public void updateMx(final Cliente cliente) throws SQLException {
                    final var sql = ""\"
                            UPDATE cliente.cliente
                            SET mx_regime_fiscal=?
                            WHERE registro = ?
                            ""\";
                    try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                        pstmt.setObject(1, cliente.getMxRegimeFiscal() != null ? cliente.getMxRegimeFiscal().getCodigo() : null, Types.SMALLINT);
                        pstmt.setInt(2, cliente.getRegistro());
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertContains(result.content(), "mx_regime_fiscal=:");
        assertContains(result.content(), "pstmt.setObject(\"mxRegimeFiscal\", cliente.getMxRegimeFiscal() != null ? cliente.getMxRegimeFiscal().getCodigo() : null, Types.SMALLINT)");
        assertContains(result.content(), "pstmt.setInt(\"registro\", cliente.getRegistro())");
    }

    public void testFallbackWhenNoIndexedSetterExists() {
        final var source = """
                public void noop() {
                    String sql = "SELECT 1";
                    System.out.println(sql);
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertFalse(result.changed());
        assertEquals(source, result.content());
        assertEquals("Nenhum setter indexado encontrado.", result.reason());
    }

    public void testUsesFallbackNameWhenPlaceholdersOutnumberSetters() {
        final var source = """
                public void mismatch(final String codigoBanco) throws SQLException {
                    final var sql = ""\"
                            SELECT *
                            FROM financeiro.movimentacao
                            WHERE id_banc = ?
                              AND id_nume = ?
                            ""\";
                    try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                        pstmt.setString(1, codigoBanco);
                    }
                }
                """;

        String expected = """
                public void mismatch(final String codigoBanco) throws SQLException {
                    final var sql = ""\"
                            SELECT *
                            FROM financeiro.movimentacao
                            WHERE id_banc = :codigoBanco
                              AND id_nume = :param2
                            ""\";
                    try (final var pstmt = this.prepareNamedStatement(sql)) {
                        pstmt.setString("codigoBanco", codigoBanco);
                    }
                }
                """;

        var result = MigratePreparedStatementToNamedAction.migrate(source);

        assertTrue(result.changed());
        assertEquals(expected, result.content());
        assertContains(result.content(), "id_banc = :codigoBanco");
        assertContains(result.content(), "id_nume = :param2");
        assertContains(result.content(), "pstmt.setString(\"codigoBanco\", codigoBanco)");
    }

    public void testSelection1() {
        final var selection = """
                @Override
                public Cliente update(final Cliente cliente) throws SQLException {
                    String sql = ""\"
                            UPDATE cliente.cliente
                            SET documento_tipo=?,
                                cgc=?,
                                razaosocial=?,
                                nomefantasia=?,
                                inscricaoestadual=?,
                                inscricaomunicipal=?,
                                fundacao=?,
                                id_tipo=?,
                                observacao=?,
                                observacaonf=?,
                                bloqueio=?,
                                ativo=?,
                                id_banc=?,
                                gmaps_latitude=?,
                                gmaps_longitude=?,
                                advalorem=?,
                                id_rede=?,
                                valorfaturamentominimocinema=?,
                                recebimento_tipo_preferencial=?::recebimentotipo,
                                id_transportadora=?,
                                prazo_pagamento_dias=?,
                                contribuicaoicmsst=?,
                                id_estabelecimento_faturamento=?,
                                id_empresa_direitos=?,
                                mx_regime_fiscal=?,
                                mx_forma_pago=?,
                                mx_metodo_pago=?,
                                mx_uso_del_cfdi=?,
                                id_cliente_faturamento=?,
                                id_copia_devolucao_metodo_preferencial=?,
                                cl_giro=?,
                                ar_dac=?,
                                ar_sagai=?,
                                ar_id_condicion_iva_manual=?
                            WHERE registro = ?
                            RETURNING *
                        ""\";
                
                    try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql)) {
                        pstmt.setString(1, cliente.getDocumentoTipo() != null ? cliente.getDocumentoTipo().getCodigo() : null);
                        pstmt.setString(2, cliente.getDocumentoTipo() != null && StringUtils.isNotBlank(cliente.getCGC()) ? cliente.getDocumentoTipo().desformata(cliente.getCGC()) : null);
                        pstmt.setString(3, StringUtils.trim(cliente.getRazaoSocial()));
                        pstmt.setString(4, StringUtils.trim(cliente.getNomeFantasia()));
                        pstmt.setString(5, cliente.getInscricaoEstadual());
                        pstmt.setString(6, cliente.getInscricaoMunicipal());
                        pstmt.setDate(7, cliente.getDataFundacao() != null ? java.sql.Date.valueOf(cliente.getDataFundacao()) : null);
                        pstmt.setInt(8, cliente.getTipoCliente() != null ? cliente.getTipoCliente().getCodigo() : ClienteTipo.EMPRESA_PRIVADA.getCodigo());
                        pstmt.setString(9, StringUtils.trimToNull(cliente.getObservacao()));
                        pstmt.setString(10, cliente.getObservacaoNF());
                        pstmt.setString(11, cliente.isBloqueio() ? "S" : "N");
                        pstmt.setString(12, cliente.isAtivo() ? "S" : "N");
                        pstmt.setString(13, cliente.getBancoConta() != null ? cliente.getBancoConta().getCodigo() : null);
                        pstmt.setString(14, cliente.getMapaPonto() != null ? cliente.getMapaPonto().getLatitude() : null);
                        pstmt.setString(15, cliente.getMapaPonto() != null ? cliente.getMapaPonto().getLongitude() : null);
                        pstmt.setBoolean(16, cliente.isAdValorem());
                        pstmt.setObject(17, cliente.getRede() != null && !Rede.INDEPENDENTES.equals(cliente.getRede()) ? cliente.getRede().getRegistro() : null);
                        pstmt.setBigDecimal(18, cliente.getValorFaturamentoMinimoCinema());
                        pstmt.setString(19, cliente.getRecebimentoTipoPreferencial().getCodigo());
                        pstmt.setObject(20, cliente.getTransportadora() != null ? cliente.getTransportadora().getRegistro() : null);
                        pstmt.setObject(21, cliente.getPrazoPagamentoDias());
                        pstmt.setObject(22, cliente.getContribuicaoICMSST().getCodigo());
                        pstmt.setLong(23, cliente.getEstabelecimentoFaturamento().getRegistro());
                        pstmt.setObject(24, cliente.getEmpresaDireitos() != null ? cliente.getEmpresaDireitos().getRegistro() : null, Types.INTEGER);
                        pstmt.setObject(25, cliente.getMxRegimeFiscal() != null ? cliente.getMxRegimeFiscal().getCodigo() : null, Types.SMALLINT);
                        pstmt.setObject(26, cliente.getMxFormaPago() != null ? cliente.getMxFormaPago().getCodigo() : null, Types.SMALLINT);
                        pstmt.setObject(27, cliente.getMxMetodoPago() != null ? cliente.getMxMetodoPago().getCodigo() : null, Types.VARCHAR);
                        pstmt.setObject(28, cliente.getMxUsoDelCFDI() != null ? cliente.getMxUsoDelCFDI().getCodigo() : null, Types.VARCHAR);
                        pstmt.setObject(29, cliente.getClienteFaturamento() != null ? cliente.getClienteFaturamento().getRegistro() : null, Types.INTEGER);
                        pstmt.setObject(30, cliente.getCopiaDevolucaoMetodoPreferencial() != null ? cliente.getCopiaDevolucaoMetodoPreferencial().getRegistro() : null, Types.INTEGER);
                        pstmt.setString(31, cliente.getClGiro());
                        pstmt.setBoolean(32, cliente.isArDac());
                        pstmt.setBoolean(33, cliente.isArSagai());
                        pstmt.setObject(34, cliente.getArCondicionIvaManual() != null ? cliente.getArCondicionIvaManual().getCodigo() : null, Types.SMALLINT);
                        pstmt.setInt(35, cliente.getRegistro());
                
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                        }
                    }
                }
                """;

        final var expected = """
                @Override
                public Cliente update(final Cliente cliente) throws SQLException {
                    String sql = ""\"
                            UPDATE cliente.cliente
                            SET documento_tipo=:documentoTipo,
                                cgc=:cgc,
                                razaosocial=:razaosocial,
                                nomefantasia=:nomefantasia,
                                inscricaoestadual=:inscricaoestadual,
                                inscricaomunicipal=:inscricaomunicipal,
                                fundacao=:fundacao,
                                id_tipo=:clienteTipoEmpresaPrivada,
                                observacao=:observacao,
                                observacaonf=:observacaonf,
                                bloqueio=:bloqueio,
                                ativo=:ativo,
                                id_banc=:idBanc,
                                gmaps_latitude=:gmapsLatitude,
                                gmaps_longitude=:gmapsLongitude,
                                advalorem=:advalorem,
                                id_rede=:idRede,
                                valorfaturamentominimocinema=:valorfaturamentominimocinema,
                                recebimento_tipo_preferencial=:recebimentoTipoPreferencial::recebimentotipo,
                                id_transportadora=:idTransportadora,
                                prazo_pagamento_dias=:prazoPagamentoDias,
                                contribuicaoicmsst=:contribuicaoicmsst,
                                id_estabelecimento_faturamento=:idEstabelecimentoFaturamento,
                                id_empresa_direitos=:idEmpresaDireitos,
                                mx_regime_fiscal=:mxRegimeFiscal,
                                mx_forma_pago=:mxFormaPago,
                                mx_metodo_pago=:mxMetodoPago,
                                mx_uso_del_cfdi=:mxUsoDelCfdi,
                                id_cliente_faturamento=:idClienteFaturamento,
                                id_copia_devolucao_metodo_preferencial=:idCopiaDevolucaoMetodoPreferencial,
                                cl_giro=:clGiro,
                                ar_dac=:arDac,
                                ar_sagai=:arSagai,
                                ar_id_condicion_iva_manual=:arIdCondicionIvaManual
                            WHERE registro = :registro
                            RETURNING *
                        ""\";
                
                    try (final var pstmt = this.prepareNamedStatement(sql)) {
                        pstmt.setString("documentoTipo", cliente.getDocumentoTipo() != null ? cliente.getDocumentoTipo().getCodigo() : null);
                        pstmt.setString("cgc", cliente.getDocumentoTipo() != null && StringUtils.isNotBlank(cliente.getCGC()) ? cliente.getDocumentoTipo().desformata(cliente.getCGC()) : null);
                        pstmt.setString("razaosocial", StringUtils.trim(cliente.getRazaoSocial()));
                        pstmt.setString("nomefantasia", StringUtils.trim(cliente.getNomeFantasia()));
                        pstmt.setString("inscricaoestadual", cliente.getInscricaoEstadual());
                        pstmt.setString("inscricaomunicipal", cliente.getInscricaoMunicipal());
                        pstmt.setDate("fundacao", cliente.getDataFundacao() != null ? java.sql.Date.valueOf(cliente.getDataFundacao()) : null);
                        pstmt.setInt("clienteTipoEmpresaPrivada", cliente.getTipoCliente() != null ? cliente.getTipoCliente().getCodigo() : ClienteTipo.EMPRESA_PRIVADA.getCodigo());
                        pstmt.setString("observacao", StringUtils.trimToNull(cliente.getObservacao()));
                        pstmt.setString("observacaonf", cliente.getObservacaoNF());
                        pstmt.setString("bloqueio", cliente.isBloqueio() ? "S" : "N");
                        pstmt.setString("ativo", cliente.isAtivo() ? "S" : "N");
                        pstmt.setString("idBanc", cliente.getBancoConta() != null ? cliente.getBancoConta().getCodigo() : null);
                        pstmt.setString("gmapsLatitude", cliente.getMapaPonto() != null ? cliente.getMapaPonto().getLatitude() : null);
                        pstmt.setString("gmapsLongitude", cliente.getMapaPonto() != null ? cliente.getMapaPonto().getLongitude() : null);
                        pstmt.setBoolean("advalorem", cliente.isAdValorem());
                        pstmt.setObject("idRede", cliente.getRede() != null && !Rede.INDEPENDENTES.equals(cliente.getRede()) ? cliente.getRede().getRegistro() : null);
                        pstmt.setBigDecimal("valorfaturamentominimocinema", cliente.getValorFaturamentoMinimoCinema());
                        pstmt.setString("recebimentoTipoPreferencial", cliente.getRecebimentoTipoPreferencial().getCodigo());
                        pstmt.setObject("idTransportadora", cliente.getTransportadora() != null ? cliente.getTransportadora().getRegistro() : null);
                        pstmt.setObject("prazoPagamentoDias", cliente.getPrazoPagamentoDias());
                        pstmt.setObject("contribuicaoicmsst", cliente.getContribuicaoICMSST().getCodigo());
                        pstmt.setLong("idEstabelecimentoFaturamento", cliente.getEstabelecimentoFaturamento().getRegistro());
                        pstmt.setObject("idEmpresaDireitos", cliente.getEmpresaDireitos() != null ? cliente.getEmpresaDireitos().getRegistro() : null, Types.INTEGER);
                        pstmt.setObject("mxRegimeFiscal", cliente.getMxRegimeFiscal() != null ? cliente.getMxRegimeFiscal().getCodigo() : null, Types.SMALLINT);
                        pstmt.setObject("mxFormaPago", cliente.getMxFormaPago() != null ? cliente.getMxFormaPago().getCodigo() : null, Types.SMALLINT);
                        pstmt.setObject("mxMetodoPago", cliente.getMxMetodoPago() != null ? cliente.getMxMetodoPago().getCodigo() : null, Types.VARCHAR);
                        pstmt.setObject("mxUsoDelCfdi", cliente.getMxUsoDelCFDI() != null ? cliente.getMxUsoDelCFDI().getCodigo() : null, Types.VARCHAR);
                        pstmt.setObject("idClienteFaturamento", cliente.getClienteFaturamento() != null ? cliente.getClienteFaturamento().getRegistro() : null, Types.INTEGER);
                        pstmt.setObject("idCopiaDevolucaoMetodoPreferencial", cliente.getCopiaDevolucaoMetodoPreferencial() != null ? cliente.getCopiaDevolucaoMetodoPreferencial().getRegistro() : null, Types.INTEGER);
                        pstmt.setString("clGiro", cliente.getClGiro());
                        pstmt.setBoolean("arDac", cliente.isArDac());
                        pstmt.setBoolean("arSagai", cliente.isArSagai());
                        pstmt.setObject("arIdCondicionIvaManual", cliente.getArCondicionIvaManual() != null ? cliente.getArCondicionIvaManual().getCodigo() : null, Types.SMALLINT);
                        pstmt.setInt("registro", cliente.getRegistro());
                
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                        }
                    }
                }
                """;
        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expected, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "documento_tipo=:documentoTipo");
        assertContains(result.content(), "cgc=:cgc");
        assertContains(result.content(), "WHERE registro = :registro");
        assertContains(result.content(), "pstmt.setString(\"documentoTipo\"");
        assertContains(result.content(), "pstmt.setInt(\"registro\", cliente.getRegistro())");
    }

    public void testSelection2() {
        final var selection = """
                    @Override
                    public List<CFOP> findAllByPrefixo(final String prefixo) throws SQLException {
                        final StringBuilder sql = new StringBuilder();
                        sql.append("SELECT * ");
                        sql.append("FROM financeiro.cfop ");
                        sql.append("WHERE LENGTH(TRIM(id_cfo)) = 4 ");
                        sql.append("  AND SUBSTR(id_cfo, 1, 1) = ? ");
                        sql.append("  AND SUBSTR(id_cfo, 3, 2) NOT IN ('00', '50') ");
                        sql.append("ORDER BY id_cfo ");
                
                        try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql.toString())) {
                            pstmt.setString(1, prefixo);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var expected = """
                    @Override
                    public List<CFOP> findAllByPrefixo(final String prefixo) throws SQLException {
                        final var sql = ""\"
                                SELECT *
                                FROM financeiro.cfop
                                WHERE LENGTH(TRIM(id_cfo)) = 4
                                  AND SUBSTR(id_cfo, 1, 1) = :prefixo
                                  AND SUBSTR(id_cfo, 3, 2) NOT IN ('00', '50')
                                ORDER BY id_cfo
                                ""\";
                
                        try (final var pstmt = this.prepareNamedStatement(sql)) {
                            pstmt.setString("prefixo", prefixo);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expected, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "SUBSTR(id_cfo, 1, 1) = :prefixo");
        assertContains(result.content(), "pstmt.setString(\"prefixo\", prefixo)");
    }

    public void testSeleciont3() {
        final var selection = """
                    @Override
                    public List<Movimentacao> findByBancoDocumentoDataOperacao(final String codigoBanco, final String documento, final LocalDate dataLancamento, final OperacaoConta operacaoConta) throws SQLException {
                        final StringBuilder sql = new StringBuilder();
                        sql.append("SELECT * ");
                        sql.append("FROM financeiro.movimentacao ");
                        sql.append("WHERE id_banc = ? ");
                        sql.append("  AND id_nume = ? ");
                        sql.append("  AND lancamento = ? ");
                        sql.append("  AND id_opco = ? ");
                        sql.append("ORDER BY registro ");
                
                        try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql.toString())) {
                            pstmt.setString(1, codigoBanco);
                            pstmt.setString(2, documento);
                            pstmt.setDate(3, new java.sql.Date(WMXDataUtil.localDateToDate(dataLancamento).getTime()));
                            pstmt.setString(4, operacaoConta.getCodigo());
                
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var expected = """
                    @Override
                    public List<Movimentacao> findByBancoDocumentoDataOperacao(final String codigoBanco, final String documento, final LocalDate dataLancamento, final OperacaoConta operacaoConta) throws SQLException {
                        final var sql = ""\"
                                SELECT *
                                FROM financeiro.movimentacao
                                WHERE id_banc = :codigoBanco
                                  AND id_nume = :documento
                                  AND lancamento = :lancamento
                                  AND id_opco = :idOpco
                                ORDER BY registro
                                ""\";
                
                        try (final var pstmt = this.prepareNamedStatement(sql)) {
                            pstmt.setString("codigoBanco", codigoBanco);
                            pstmt.setString("documento", documento);
                            pstmt.setDate("lancamento", new java.sql.Date(WMXDataUtil.localDateToDate(dataLancamento).getTime()));
                            pstmt.setString("idOpco", operacaoConta.getCodigo());
                
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;
        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expected, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "WHERE id_banc = :codigoBanco");
        assertContains(result.content(), "AND id_opco = :idOpco");
        assertContains(result.content(), "pstmt.setString(\"idOpco\", operacaoConta.getCodigo())");
    }

    public void testSelection4() {
        final var selection = """
                    @Override
                    public List<CinemaSiteFilme> findByInput(final String input, final int limit) throws SQLException {
                        final var sql = ""\"
                                SELECT f.*
                                FROM cinemasite.filme f
                                WHERE f.codigo_antigo = ?
                                   OR unaccent(f.titulo_portugues) ILIKE '%'||unaccent(?)||'%'
                                   OR unaccent(f.titulo_ingles) ILIKE '%'||unaccent(?)||'%'
                                LIMIT ?
                            ""\";
                
                        try (final var pstmt = getConnection().prepareStatement(sql)) {
                            pstmt.setString(1, input);
                            pstmt.setString(2, input);
                            pstmt.setString(3, input);
                            pstmt.setInt(4, limit);
                            try (final var rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var expected = """
                    @Override
                    public List<CinemaSiteFilme> findByInput(final String input, final int limit) throws SQLException {
                        final var sql = ""\"
                                SELECT f.*
                                FROM cinemasite.filme f
                                WHERE f.codigo_antigo = :input
                                   OR unaccent(f.titulo_portugues) ILIKE '%'||unaccent(:input)||'%'
                                   OR unaccent(f.titulo_ingles) ILIKE '%'||unaccent(:input)||'%'
                                LIMIT :limit
                            ""\";
                
                        try (final var pstmt = this.prepareNamedStatement(sql)) {
                            pstmt.setString("input", input);
                
                            pstmt.setInt("limit", limit);
                            try (final var rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expected, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "f.codigo_antigo = :input");
        assertContains(result.content(), "LIMIT :limit");
        assertEquals(1, countOccurrences(result.content(), "pstmt.setString(\"input\", input)"));
    }

    public void testSelection5() {
        final var selection = """
                    @Override
                    public List<CinemaSiteFilme> findComMateriaisNoPeriodo(final LocalDateTime startDate, final LocalDateTime endDate) throws SQLException {
                        final var sql = ""\"
                                WITH filmes as (
                                    SELECT f.registro
                                    FROM cinemasite.filme f
                                    LEFT JOIN cinemasite.materialtrailer mt ON mt.registro = ANY(f.materiais_trailers)
                                    LEFT JOIN cinemasite.materialdigital md ON md.registro = ANY(f.materiais_midias)
                                    WHERE (mt.data_criacao BETWEEN ? AND ?)
                                       OR (md.data_criacao BETWEEN ? AND ?)
                                    GROUP BY f.registro
                                )
                                SELECT f.*
                                FROM cinemasite.filme f, filmes
                                WHERE f.registro IN (filmes.registro)
                            ""\";
                        try (final var pstmt = prepareStatement(sql)) {
                            pstmt.setTimestamp(1, Timestamp.valueOf(startDate));
                            pstmt.setTimestamp(2, Timestamp.valueOf(endDate));
                            pstmt.setTimestamp(3, Timestamp.valueOf(startDate));
                            pstmt.setTimestamp(4, Timestamp.valueOf(endDate));
                            try (final var rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;

        final var expectedResult = """
                    @Override
                    public List<CinemaSiteFilme> findComMateriaisNoPeriodo(final LocalDateTime startDate, final LocalDateTime endDate) throws SQLException {
                        final var sql = ""\"
                                WITH filmes as (
                                    SELECT f.registro
                                    FROM cinemasite.filme f
                                    LEFT JOIN cinemasite.materialtrailer mt ON mt.registro = ANY(f.materiais_trailers)
                                    LEFT JOIN cinemasite.materialdigital md ON md.registro = ANY(f.materiais_midias)
                                    WHERE (mt.data_criacao BETWEEN :startDate AND :endDate)
                                       OR (md.data_criacao BETWEEN :startDate AND :endDate)
                                    GROUP BY f.registro
                                )
                                SELECT f.*
                                FROM cinemasite.filme f, filmes
                                WHERE f.registro IN (filmes.registro)
                            ""\";
                        try (final var pstmt = this.prepareNamedStatement(sql)) {
                            pstmt.setTimestamp("startDate", Timestamp.valueOf(startDate));
                            pstmt.setTimestamp("endDate", Timestamp.valueOf(endDate));
                
                            try (final var rs = pstmt.executeQuery()) {
                                return parseAll(rs);
                            }
                        }
                    }
                """;
        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expectedResult, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "BETWEEN :startDate AND :endDate");
        assertEquals(1, countOccurrences(result.content(), "pstmt.setTimestamp(\"startDate\", Timestamp.valueOf(startDate))"));
        assertEquals(1, countOccurrences(result.content(), "pstmt.setTimestamp(\"endDate\", Timestamp.valueOf(endDate))"));
    }

    public void testSelection6() {
        final var selection = """
                    @Override
                    public List<CinemaContato> findDisponiveisParaPromotor(long registroPromotor) throws SQLException {
                        String sql = "SELECT DISTINCT fe.*, " +
                            "  cn.registro id_cinema " +
                            "FROM foneemail fe " +
                            "JOIN cliente.cliente c ON (c.id_clie = fe.id_fone) " +
                            "JOIN cinema.cinema cn ON (c.id_clie = cn.id_clie) " +
                            "JOIN cinema.cinema_promotor cp ON (cp.id_cinema = cn.registro) " +
                            "WHERE cp.id_promotor = ? " +
                            "  AND fe.tipo = ? " +
                            "  AND c.ativo = 'S' " +
                            "ORDER BY id_cinema ASC ";
                
                        try (PreparedStatement pstm = getConnection().prepareStatement(sql)) {
                            pstm.setLong(1, registroPromotor);
                            pstm.setString(2, FoneEmailTipo.CLIENTE.getCodigo());
                
                            try (ResultSet rs = pstm.executeQuery()) {
                                final List<CinemaContato> contatos = new ArrayList<>();
                                while (rs.next()) {
                                    final CinemaContato contato = new CinemaContato();
                                    contato.setCinema(new Cinema(rs.getLong("id_cinema")));
                                    contato.setFoneEmail(PostgresFoneEmailDAO.parse(rs));
                                    contato.getFoneEmail().setTelefone(WMXUtil.somenteNumerico(rs.getString("fone"))); //quebrando no ipad
                                    contatos.add(contato);
                                }
                                return contatos;
                            }
                        }
                    }
                """;

        final var expectedResult = """
                    @Override
                    public List<CinemaContato> findDisponiveisParaPromotor(long registroPromotor) throws SQLException {
                        final var sql = ""\"
                                SELECT DISTINCT fe.*,
                                  cn.registro id_cinema
                                FROM foneemail fe
                                JOIN cliente.cliente c ON (c.id_clie = fe.id_fone)
                                JOIN cinema.cinema cn ON (c.id_clie = cn.id_clie)
                                JOIN cinema.cinema_promotor cp ON (cp.id_cinema = cn.registro)
                                WHERE cp.id_promotor = :registroPromotor
                                  AND fe.tipo = :foneEmailTipoCliente
                                  AND c.ativo = 'S'
                                ORDER BY id_cinema ASC
                                ""\";
                
                        try (final var pstm = this.prepareNamedStatement(sql)) {
                            pstm.setLong("registroPromotor", registroPromotor);
                            pstm.setString("foneEmailTipoCliente", FoneEmailTipo.CLIENTE.getCodigo());
                
                            try (ResultSet rs = pstm.executeQuery()) {
                                final List<CinemaContato> contatos = new ArrayList<>();
                                while (rs.next()) {
                                    final CinemaContato contato = new CinemaContato();
                                    contato.setCinema(new Cinema(rs.getLong("id_cinema")));
                                    contato.setFoneEmail(PostgresFoneEmailDAO.parse(rs));
                                    contato.getFoneEmail().setTelefone(WMXUtil.somenteNumerico(rs.getString("fone"))); //quebrando no ipad
                                    contatos.add(contato);
                                }
                                return contatos;
                            }
                        }
                    }
                """;
        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expectedResult, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(sql)");
        assertContains(result.content(), "cp.id_promotor = :registroPromotor");
        assertContains(result.content(), "fe.tipo = :foneEmailTipoCliente");
        assertContains(result.content(), "pstm.setLong(\"registroPromotor\", registroPromotor)");
        assertContains(result.content(), "pstm.setString(\"foneEmailTipoCliente\", FoneEmailTipo.CLIENTE.getCodigo())");
    }

    public void testSelection7() {
        final var selection = """
                    @Override
                    public Cliente atualizaPonto(final String cgc, final String latitude, final String longitude) throws SQLException {
                        try (PreparedStatement pstmt = super.getConnection().prepareStatement("UPDATE cliente.cliente SET gmaps_latitude = ?, gmaps_longitude = ? WHERE cgc = ? RETURNING *")) {
                            pstmt.setString(1, latitude);
                            pstmt.setString(2, longitude);
                            pstmt.setString(3, cgc);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                            }
                        }
                    }
                """;

        final var expectedResult = """
                    @Override
                    public Cliente atualizaPonto(final String cgc, final String latitude, final String longitude) throws SQLException {
                        try (final var pstmt = this.prepareNamedStatement("UPDATE cliente.cliente SET gmaps_latitude = :latitude, gmaps_longitude = :longitude WHERE cgc = :cgc RETURNING *")) {
                            pstmt.setString("latitude", latitude);
                            pstmt.setString("longitude", longitude);
                            pstmt.setString("cgc", cgc);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                return rs.next() ? PostgresClienteDAO.parseRs(rs) : null;
                            }
                        }
                    }
                """;

        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expectedResult, result.content());
        assertContains(result.content(), "this.prepareNamedStatement(\"UPDATE cliente.cliente SET gmaps_latitude = :latitude, gmaps_longitude = :longitude WHERE cgc = :cgc RETURNING *\")");
        assertContains(result.content(), "pstmt.setString(\"latitude\", latitude)");
        assertContains(result.content(), "pstmt.setString(\"longitude\", longitude)");
        assertContains(result.content(), "pstmt.setString(\"cgc\", cgc)");
    }

    public void testSelection8() {
        final var selection = """
                @Override
                public Movimentacao insert(final Movimentacao movimentacao) throws SQLException {
                    final StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO financeiro.movimentacao (id_nume, id_banc, situacao, id_opco, lancamento, valor, id_extratoconciliacao, id_arquivoretorno, id_cobrancaretorno) ");
                    sql.append("VALUES (?,?,?,?,?,?,?,?,?) ");
                    sql.append("RETURNING * ");
                
                    try (PreparedStatement pstmt = super.getConnection().prepareStatement(sql.toString())) {
                        pstmt.setString(1, movimentacao.getNumeroDocumento());
                        pstmt.setString(2, movimentacao.getBancoConta().getCodigo());
                        pstmt.setString(3, movimentacao.getSituacao().getCodigo());
                        pstmt.setString(4, movimentacao.getOperacaoConta().getCodigo());
                        pstmt.setDate(5, java.sql.Date.valueOf(movimentacao.getDataLancamento()));
                        pstmt.setBigDecimal(6, movimentacao.getValor());
                        pstmt.setObject(7, movimentacao.getConciliacao() != null ? movimentacao.getConciliacao().getRegistro() : null, Types.OTHER);
                        pstmt.setObject(8, movimentacao.getArquivoRetorno() != null ? movimentacao.getArquivoRetorno().getRegistro() : null, Types.OTHER);
                        pstmt.setObject(9, movimentacao.getCobrancaRetorno() != null ? movimentacao.getCobrancaRetorno().getRegistro() : null, Types.OTHER);
                
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresMovimentacaoDAO.parse(rs) : null;
                        }
                    }
                }
                """;

        final var expectedResult = """
                @Override
                public Movimentacao insert(final Movimentacao movimentacao) throws SQLException {
                    final var sql = ""\"
                        INSERT INTO financeiro.movimentacao (id_nume, id_banc, situacao, id_opco, lancamento, valor, id_extratoconciliacao, id_arquivoretorno, id_cobrancaretorno)
                        VALUES (idNume, idBanc, situacao, idOpco, lancamento, valor, idExtratoconciliacao, idArquivoretorno, idCobrancaretorno)
                        RETURNING *
                        ""\";
                
                    try (final var pstmt = this.prepareNamedStatement(sql)) {
                        pstmt.setString("idNume", movimentacao.getNumeroDocumento());
                        pstmt.setString("idBanc", movimentacao.getBancoConta().getCodigo());
                        pstmt.setString("situacao", movimentacao.getSituacao().getCodigo());
                        pstmt.setString("idOpco", movimentacao.getOperacaoConta().getCodigo());
                        pstmt.setDate("lancamento", java.sql.Date.valueOf(movimentacao.getDataLancamento()));
                        pstmt.setBigDecimal("valor", movimentacao.getValor());
                        pstmt.setObject("idExtratoconciliacao", movimentacao.getConciliacao() != null ? movimentacao.getConciliacao().getRegistro() : null, Types.OTHER);
                        pstmt.setObject("idArquivoretorno", movimentacao.getArquivoRetorno() != null ? movimentacao.getArquivoRetorno().getRegistro() : null, Types.OTHER);
                        pstmt.setObject("idCobrancaretorno", movimentacao.getCobrancaRetorno() != null ? movimentacao.getCobrancaRetorno().getRegistro() : null, Types.OTHER);
                
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() ? PostgresMovimentacaoDAO.parse(rs) : null;
                        }
                    }
                }
                """;
        final var result = MigratePreparedStatementToNamedAction.migrate(selection);
        assertTrue(result.changed());
        assertEquals(expectedResult, result.content());
    }

    private static int countOccurrences(final String content, final String searchTerm) {
        int count = 0;
        int index = 0;
        while (true) {
            index = content.indexOf(searchTerm, index);
            if (index < 0) {
                return count;
            }
            count++;
            index += searchTerm.length();
        }
    }

    private static void assertContains(final String content, final String searchTerm) {
        assertTrue(content.contains(searchTerm));
    }

    private static void assertNotContains(final String content, final String searchTerm) {
        assertFalse(content.contains(searchTerm));
    }
}