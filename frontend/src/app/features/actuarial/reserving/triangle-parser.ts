/**
 * Lecture d'un triangle de développement collé depuis un tableur.
 *
 * Une ligne par année d'origine ; valeurs séparées par tabulation (copier-coller
 * depuis Excel), point-virgule ou espaces ; décimales avec virgule ou point.
 * Les séparateurs de milliers (espaces, points) ne sont possibles qu'avec un
 * séparateur de colonnes tabulation ou point-virgule.
 * Une première colonne d'années (ex. « 1988 ») est reconnue et retirée.
 * Les cellules futures peuvent être absentes ou vides.
 */
export interface ParsedTriangle {
  triangle: Array<Array<number | null>>;
  origins?: number[];
}

export class TriangleParseError extends Error {}

const YEAR = /^(19|20)\d{2}$/;

function parseNumber(raw: string, line: number): number | null {
  const text = raw.trim();
  if (text === '' || text === '-' || text.toLowerCase() === 'null') {
    return null;
  }
  // « 1 234,5 » ou « 1.234,5 » → 1234.5 ; « 1234.5 » reste tel quel.
  const normalized = text.includes(',')
    ? text.replace(/[\s  .]/g, '').replace(',', '.')
    : text.replace(/[\s  ]/g, '');
  const value = Number(normalized);
  if (!Number.isFinite(value)) {
    throw new TriangleParseError(`Ligne ${line} : « ${text} » n'est pas un nombre.`);
  }
  return value;
}

type Separator = '\t' | ';' | 'whitespace';

/**
 * Séparateur choisi une fois pour tout le texte : la dernière ligne d'un
 * triangle n'a qu'une valeur, donc aucun séparateur à elle seule.
 */
function detectSeparator(lines: string[]): Separator {
  if (lines.some(line => line.includes('\t'))) {
    return '\t';
  }
  if (lines.some(line => line.includes(';'))) {
    return ';';
  }
  return 'whitespace';
}

function splitCells(line: string, separator: Separator): string[] {
  return separator === 'whitespace' ? line.trim().split(/\s+/) : line.split(separator);
}

export function parseTriangle(text: string): ParsedTriangle {
  // Lignes conservées telles quelles : des tabulations finales signalent des cellules vides.
  const lines = text.split(/\r?\n/).filter(line => line.trim() !== '');
  if (lines.length < 3) {
    throw new TriangleParseError('Collez au moins 3 lignes (une par année d\'origine).');
  }

  const separator = detectSeparator(lines);
  const rows = lines.map(line => splitCells(line, separator).map(cell => cell.trim()));
  const hasYears = rows.every(cells => YEAR.test(cells[0] ?? ''));
  const origins = hasYears ? rows.map(cells => Number(cells[0])) : undefined;

  const triangle = rows.map((cells, index) => {
    const values = (hasYears ? cells.slice(1) : cells).map(cell => parseNumber(cell, index + 1));
    while (values.length > 0 && values[values.length - 1] === null) {
      values.pop();
    }
    if (values.length === 0) {
      throw new TriangleParseError(`Ligne ${index + 1} : aucune valeur.`);
    }
    return values;
  });
  return origins ? { triangle, origins } : { triangle };
}

/** Exemple de référence : triangle de Taylor & Ashe (1983), montants cumulés. */
export const TAYLOR_ASHE_EXAMPLE = [
  [357848, 1124788, 1735330, 2218270, 2745596, 3319994, 3466336, 3606286, 3833515, 3901463],
  [352118, 1236139, 2170033, 3353322, 3799067, 4120063, 4647867, 4914039, 5339085],
  [290507, 1292306, 2218525, 3235179, 3985995, 4132918, 4628910, 4909315],
  [310608, 1418858, 2195047, 3757447, 4029929, 4381982, 4588268],
  [443160, 1136350, 2128333, 2897821, 3402672, 3873311],
  [396132, 1333217, 2180715, 2985752, 3691712],
  [440832, 1288463, 2419861, 3483130],
  [359480, 1421128, 2864498],
  [376686, 1363294],
  [344014]
]
  .map(row => row.join('\t'))
  .join('\n');
