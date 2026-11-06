const TITLE_ABBREVIATIONS: Array<[RegExp, string]> = [
  [/^magister scientiae\b/i, "MSc."],
  [/^master of science\b/i, "MSc."],
  [/^máster of science\b/i, "MSc."],
  [/^doctor of philosophy\b/i, "PhD."],
  [/^tecn[oó]logo superior universitario\b/i, "Tnlgo. S.U."],
  [/^tecn[oó]loga superior universitaria\b/i, "Tnlga. S.U."],
  [/^t[eé]cnic[oa] superior\b/i, "Téc."],
  [/^ingenier[oa]\b/i, "Ing."],
  [/^licenciado\b/i, "Lcdo."],
  [/^licenciada\b/i, "Lcda."],
  [/^mag[ií]ster\b/i, "Mtr."],
  [/^máster\b/i, "Mtr."],
  [/^master\b/i, "Mtr."],
  [/^doctor\b/i, "Dr."],
  [/^doctora\b/i, "Dra."],
  [/^arquitect[oa]\b/i, "Arq."],
  [/^abogad[oa]\b/i, "Abg."],
  [/^economista\b/i, "Econ."],
  [/^psic[oó]logo\b/i, "Psic."],
  [/^psic[oó]loga\b/i, "Psic."],
  [/^m[eé]dic[oa]\b/i, "Méd."],
  [/^odont[oó]log[oa]\b/i, "Odont."],
  [/^profesor\b/i, "Prof."],
  [/^profesora\b/i, "Profa."],
  [/^tecn[oó]logo\b/i, "Tnlgo."],
  [/^tecn[oó]loga\b/i, "Tnlga."]
];

const ABBREVIATION_EXPANSIONS: Array<[RegExp, string]> = [
  [/^Tnlgo\. S\.U\./i, "Tecnólogo Superior Universitario"],
  [/^Tnlga\. S\.U\./i, "Tecnóloga Superior Universitaria"],
  [/^MSc\./i, "Master of Science"],
  [/^PhD\./i, "Doctor of Philosophy"],
  [/^Tnlgo\./i, "Tecnólogo"],
  [/^Tnlga\./i, "Tecnóloga"],
  [/^Lcdo\./i, "Licenciado"],
  [/^Lcda\./i, "Licenciada"],
  [/^Mtr\./i, "Magíster"],
  [/^Ing\./i, "Ingeniero/a"],
  [/^Dra\./i, "Doctora"],
  [/^Dr\./i, "Doctor"],
  [/^Arq\./i, "Arquitecto/a"],
  [/^Abg\./i, "Abogado/a"],
  [/^Econ\./i, "Economista"],
  [/^Psic\./i, "Psicólogo/a"],
  [/^Méd\./i, "Médico/a"],
  [/^Odont\./i, "Odontólogo/a"],
  [/^Profa\./i, "Profesora"],
  [/^Prof\./i, "Profesor"],
  [/^Téc\./i, "Técnico/a Superior"]
];

export function abbreviateProfessionalTitle(title: string | null | undefined) {
  const normalized = title?.trim();
  if (!normalized) return null;

  for (const [fullTitle, abbreviation] of TITLE_ABBREVIATIONS) {
    if (fullTitle.test(normalized)) {
      return normalized.replace(fullTitle, abbreviation);
    }
  }

  return normalized;
}

export function expandProfessionalTitle(title: string | null | undefined) {
  const normalized = title?.trim();
  if (!normalized) return null;

  for (const [abbreviation, fullTitle] of ABBREVIATION_EXPANSIONS) {
    if (abbreviation.test(normalized)) {
      return normalized.replace(abbreviation, fullTitle);
    }
  }

  return normalized;
}

export function professionalTitlePrefix(title: string | null | undefined) {
  const abbreviated = abbreviateProfessionalTitle(title);
  if (!abbreviated) return null;

  for (const [abbreviation] of ABBREVIATION_EXPANSIONS) {
    const match = abbreviated.match(abbreviation);
    if (match) return match[0];
  }

  return null;
}
