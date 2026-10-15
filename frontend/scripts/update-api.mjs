// Baixa o contrato da API do backend rodando e regrava o snapshot versionado (openapi.json).
// Depois rode `npm run api:generate` para atualizar os tipos em src/app/api/schema.ts.
// Uso: npm run api:update            (backend em http://localhost:8080)
//      API_URL=http://host:porta npm run api:update
import { writeFile } from 'node:fs/promises';

const base = process.env.API_URL ?? 'http://localhost:8080';
const response = await fetch(`${base}/v3/api-docs`);
if (!response.ok) {
  console.error(`GET ${base}/v3/api-docs respondeu ${response.status}. O backend está rodando?`);
  process.exit(1);
}
const spec = await response.json();
await writeFile(new URL('../openapi.json', import.meta.url), JSON.stringify(spec, null, 2) + '\n');
console.log(`openapi.json atualizado (${Object.keys(spec.paths).length} rotas)`);
