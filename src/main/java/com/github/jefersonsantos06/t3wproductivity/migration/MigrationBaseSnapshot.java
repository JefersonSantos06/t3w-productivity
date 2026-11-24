package com.github.jefersonsantos06.t3wproductivity.migration;

import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;

import java.util.Optional;
import java.util.Set;

/**
 * Migrations e funções SQL que já existem na branch base (ex.: origin/master), lidas do git.
 *
 * @param fileNames     nomes dos arquivos de migration na base
 * @param functionNames funções criadas por migrations da base, em minúsculas e sem schema
 */
public record MigrationBaseSnapshot(String baseRef, String commit, Set<String> fileNames, Set<String> functionNames) {

    public Optional<String> latestFileName() {
        return FlywayUtils.findLatestMigration(fileNames);
    }
}
