// Devolve um vCard montado a partir da query (?n=&t=&e=&o=&u=). Nada é guardado.
const limpa = (s) => String(s || "").replace(/[\r\n;\\]/g, " ").trim().slice(0, 200);

module.exports = (req, res) => {
  const q = req.query || {};
  const n = limpa(q.n), t = limpa(q.t), e = limpa(q.e), o = limpa(q.o), u = limpa(q.u);
  const l = ["BEGIN:VCARD", "VERSION:3.0", `FN:${n}`, `N:${n};;;;`];
  if (t) l.push(`TEL;TYPE=CELL:${t}`);
  if (e) l.push(`EMAIL:${e}`);
  if (o) l.push(`ORG:${o}`);
  if (u) l.push(`URL:${u}`);
  l.push("END:VCARD", "");
  const arq = (n || "contato").normalize("NFD").replace(/[^\w]+/g, "_") + ".vcf";
  res.setHeader("Content-Type", "text/vcard; charset=utf-8");
  res.setHeader("Content-Disposition", `inline; filename="${arq}"`);
  res.setHeader("Cache-Control", "no-store");
  res.end(l.join("\r\n"));
};
