#!/usr/bin/env node
// Gera src/main/resources/web-types/vaadin.web-types.json a partir dos web-types publicados nos pacotes npm do Vaadin.
//
// Os web-types oficiais só descrevem tags, atributos e eventos. As informações de estilo (shadow parts, custom
// properties e atributos de estado) existem apenas como tabelas markdown nas descrições, então são extraídas daqui
// e gravadas nas seções "css" que o IntelliJ entende. As custom properties globais do Lumo vêm do código do tema.
//
// Uso: node scripts/generate-vaadin-web-types.mjs [versão-da-plataforma]   (padrão: 24.8.6)
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PLATFORM_VERSION = process.argv[2] ?? '24.8.6';
const OUTPUT = resolve(dirname(fileURLToPath(import.meta.url)), '../src/main/resources/web-types/vaadin.web-types.json');
const CDN = 'https://cdn.jsdelivr.net/npm';
const LUMO_PACKAGE = '@vaadin/vaadin-lumo-styles';
// Arquivos do Lumo que declaram as custom properties globais do tema.
const LUMO_FILES = ['color.js', 'sizing.js', 'spacing.js', 'style.js', 'typography.js', 'font-icons.js', 'user-colors.js',
  'badge.js', 'mixins/input-field-shared.js', 'mixins/required-field.js'];
// Primeira coluna do cabeçalho da tabela -> tipo de informação de estilo.
const TABLE_KINDS = [
  [/^part name$/i, 'parts'],
  [/^attribute$/i, 'states'],
  [/^custom (css )?property$/i, 'properties'],
];

async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${response.status} ao baixar ${url}`);
  }
  return response.text();
}

const fetchJson = async (url) => JSON.parse(await fetchText(url));
const byName = (a, b) => a.name.localeCompare(b.name);
const cells = (line) => line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((cell) => cell.trim());

async function componentPackages() {
  const versions = await fetchJson(`https://raw.githubusercontent.com/vaadin/platform/${PLATFORM_VERSION}/versions.json`);
  const packages = new Map();
  for (const entry of [...Object.values(versions.core), ...Object.values(versions.vaadin)]) {
    // Algumas entradas trazem placeholders como "{{version}}" em vez de uma versão real.
    if (entry.npmName && /^\d/.test(entry.jsVersion ?? '')) {
      packages.set(entry.npmName, entry.jsVersion);
    }
  }
  return packages;
}

async function loadElements(name, version) {
  const pkg = await fetchJson(`${CDN}/${name}@${version}/package.json`);
  // A variante ".lit" usa a sintaxe de bindings do Lit (.prop, @event), que não vale em HTML puro.
  const files = [pkg['web-types'] ?? []].flat().filter((file) => !file.includes('.lit.'));
  const webTypes = await Promise.all(files.map((file) => fetchJson(`${CDN}/${name}@${version}/${file.replace(/^\.\//, '')}`)));
  return webTypes.flatMap((json) => json.contributions?.html?.elements ?? []);
}

function rowNames(cell) {
  const quoted = [...cell.matchAll(/`([^`]+)`/g)].map((match) => match[1]);
  return (quoted.length ? quoted : [cell]).filter((name) => /^(--)?[a-z][\w-]*$/i.test(name));
}

// Tag citada na frase que introduz a tabela, ex.: "The following shadow DOM parts of the `<vaadin-notification-card>`".
function tableOwner(lines, headerIndex) {
  for (let i = headerIndex - 1; i >= Math.max(0, headerIndex - 3); i--) {
    const match = lines[i].match(/ of the `<([a-z0-9-]+)>`/);
    if (match) {
      return match[1];
    }
  }
  return null;
}

function parseStylingTables(description) {
  const lines = description.split('\n');
  const tables = [];
  for (let i = 1; i < lines.length; i++) {
    if (!/^\s*\|?\s*-{3,}\s*\|/.test(lines[i])) {
      continue;
    }
    const header = cells(lines[i - 1]);
    const kind = TABLE_KINDS.find(([pattern]) => pattern.test(header[0]))?.[1];
    if (!kind) {
      continue;
    }
    const defaultColumn = header.findIndex((column) => /^default$/i.test(column));
    const table = { kind, owner: tableOwner(lines, i - 1), rows: [] };
    for (i++; i < lines.length && lines[i].includes('|'); i++) {
      const row = cells(lines[i]);
      for (const name of rowNames(row[0])) {
        table.rows.push({ name, description: row[1] ?? '', defaultValue: row[defaultColumn] ?? '' });
      }
    }
    tables.push(table);
  }
  return tables;
}

function addUnique(list, item) {
  if (!list.some((existing) => existing.name === item.name)) {
    list.push(item);
  }
}

function addStyling(element, elementsByName) {
  for (const table of parseStylingTables(element.description ?? '')) {
    let target = element;
    if (table.owner && table.owner !== element.name) {
      // Elementos internos (wrappers, cards) que só são estilizados via CSS não vêm nos web-types oficiais.
      target = elementsByName.get(table.owner) ?? {
        name: table.owner,
        description: `Elemento interno de \`<${element.name}>\`, exposto para estilização.`,
        attributes: [],
      };
      elementsByName.set(target.name, target);
    }
    target.css ??= {};
    for (const row of table.rows) {
      if (table.kind === 'parts') {
        addUnique(target.css.parts ??= [], { name: row.name, description: row.description });
      } else if (table.kind === 'properties') {
        const defaultValue = row.defaultValue.replace(/`/g, '');
        addUnique(target.css.properties ??= [], {
          name: row.name,
          description: defaultValue ? `${row.description}\n\nPadrão: \`${defaultValue}\`` : row.description,
        });
      } else {
        addUnique(target.attributes ??= [], {
          name: row.name,
          description: `Atributo de estado, definido pelo próprio componente (use em seletores CSS). ${row.description}`,
          value: { kind: 'plain', type: 'string', required: false },
        });
      }
    }
  }
}

// Componentes que só apontam para a documentação de estilo de outro (ex.: vaadin-email-field -> vaadin-text-field).
function inheritStyling(element, elementsByName) {
  const parentName = element.description?.match(/See \[`<([a-z0-9-]+)>`\]\([^)]*\) for the styling documentation/)?.[1];
  const parent = elementsByName.get(parentName);
  if (element.css || !parent?.css) {
    return;
  }
  element.css = structuredClone(parent.css);
  for (const attribute of parent.attributes ?? []) {
    addUnique(element.attributes ??= [], attribute);
  }
}

async function loadLumoProperties(version) {
  const properties = new Map();
  for (const file of LUMO_FILES) {
    const source = await fetchText(`${CDN}/${LUMO_PACKAGE}@${version}/${file}`);
    // A primeira declaração é a do tema claro; as seguintes (dark, compact) são sobrescritas.
    for (const [, name, value] of source.matchAll(/(--(?:lumo|vaadin)-[a-z0-9-]+)\s*:\s*([^;]+);/g)) {
      const defaultValue = value.trim().replace(/\s+/g, ' ');
      if (!properties.has(name)) {
        properties.set(name, {
          name,
          description: defaultValue.includes('${') ? `Lumo (\`${file}\`)` : `Lumo (\`${file}\`). Padrão: \`${defaultValue}\``,
        });
      }
    }
  }
  return [...properties.values()].sort(byName);
}

const packages = await componentPackages();
const elementsByName = new Map();
for (const elements of await Promise.all([...packages].map(([name, version]) => loadElements(name, version)))) {
  for (const element of elements) {
    if (!elementsByName.has(element.name)) {
      elementsByName.set(element.name, element);
    }
  }
}
for (const element of [...elementsByName.values()]) {
  addStyling(element, elementsByName);
}
for (const element of elementsByName.values()) {
  inheritStyling(element, elementsByName);
}

const webTypes = {
  $schema: 'https://json.schemastore.org/web-types',
  name: 't3w-vaadin-web-types',
  version: PLATFORM_VERSION,
  'description-markup': 'markdown',
  contributions: {
    html: { elements: [...elementsByName.values()].sort(byName) },
    css: { properties: await loadLumoProperties(packages.get(LUMO_PACKAGE)) },
  },
};

mkdirSync(dirname(OUTPUT), { recursive: true });
writeFileSync(OUTPUT, `${JSON.stringify(webTypes, null, 2)}\n`);
const elements = webTypes.contributions.html.elements;
console.log(`Vaadin ${PLATFORM_VERSION}: ${elements.length} elementos, `
  + `${elements.filter((element) => element.css?.parts).length} com parts, `
  + `${webTypes.contributions.css.properties.length} custom properties globais -> ${OUTPUT}`);
