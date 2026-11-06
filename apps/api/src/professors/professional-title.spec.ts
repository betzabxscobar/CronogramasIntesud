import {
  abbreviateProfessionalTitle,
  expandProfessionalTitle,
  professionalTitlePrefix
} from "./professional-title";

describe("abbreviateProfessionalTitle", () => {
  it.each([
    ["Ingeniero", "Ing."],
    ["Ingeniera en Sistemas", "Ing. en Sistemas"],
    ["Magíster en Educación", "Mtr. en Educación"],
    ["Máster en Docencia", "Mtr. en Docencia"],
    ["Magister Scientiae en Informática", "MSc. en Informática"],
    ["Master of Science in Tourism", "MSc. in Tourism"],
    ["Licenciado en Turismo", "Lcdo. en Turismo"],
    ["Licenciada en Enfermería", "Lcda. en Enfermería"],
    ["Doctor en Ciencias", "Dr. en Ciencias"],
    ["Doctor of Philosophy en Educación", "PhD. en Educación"],
    ["Arquitecta", "Arq."],
    ["Tecnólogo", "Tnlgo."],
    ["Tecnóloga", "Tnlga."],
    ["Tecnólogo Superior Universitario en Software", "Tnlgo. S.U. en Software"],
    ["Tecnóloga Superior Universitaria en Turismo", "Tnlga. S.U. en Turismo"],
    ["Psicóloga Clínica", "Psic. Clínica"],
    ["Médico General", "Méd. General"],
    ["Odontóloga", "Odont."],
    ["Profesora", "Profa."],
    ["Ing. en Software", "Ing. en Software"]
  ])("convierte %s en %s", (title, expected) => {
    expect(abbreviateProfessionalTitle(title)).toBe(expected);
  });
});

describe("expandProfessionalTitle", () => {
  it.each([
    ["Mtr.", "Magíster"],
    ["Mtr. en Educación", "Magíster en Educación"],
    ["Lcda. en Turismo", "Licenciada en Turismo"],
    ["Tnlgo. S.U. en Software", "Tecnólogo Superior Universitario en Software"],
    ["Ingeniero en Sistemas", "Ingeniero en Sistemas"]
  ])("expande %s como %s", (title, expected) => {
    expect(expandProfessionalTitle(title)).toBe(expected);
  });
});

describe("professionalTitlePrefix", () => {
  it.each([
    ["Magíster en Educación", "Mtr."],
    ["MSc. en Turismo", "MSc."],
    ["Tecnóloga Superior Universitaria", "Tnlga. S.U."],
    ["Licenciado en Turismo", "Lcdo."],
    ["Especialista en Seguridad", null]
  ])("obtiene %s como %s", (title, expected) => {
    expect(professionalTitlePrefix(title)).toBe(expected);
  });
});
