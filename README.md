# Massivas Fai

App Android para organizar os VINs de **massivas** (veículos com liberação urgente) do setor de montagem.

## Baixar / instalar

Vá em **Releases** deste repositório e baixe o `MassivasFai-1.0.X.apk` mais recente.
Na primeira instalação o Android pede para permitir "instalar apps desconhecidos".

Depois disso o próprio app avisa quando sai uma versão nova (ou menu ⋮ → **Verificar atualizações**),
baixa e instala **por cima**, mantendo todos os VINs salvos.

## O que o app faz

- Adicionar VINs rapidamente (**Salvar e próximo** mantém o modelo e limpa o VIN).
- Separa automaticamente por modelo, na ordem S202 (Uni-T), C655 (CS55), C928 (CS75), T1E ADAS (Tiggo 7),
  T1E ICE LOW (Tiggo 7 Ice Low), T19 ICE LOW (Tiggo 5 Ice Low), T18 (Tiggo 8 Ice), e em ordem numérica do VIN.
- Cores do veículo: Azul, Cinza, Preto, Branco.
- Estações de reparo, cada uma com sua cor: Pintura, Polimento, EX-OK, Reparo de Shower, Reparo de Pista,
  Funilaria, Elétrica, Alinhamento, Teste de Rolo, Underbody, Adas (com descrição e "concluído" por reparo).
- Status: No processo, Túnel de liberação, Liberado ✅ (segure o card para mudar rápido).
- Opcionais: previsão de reparo (data e hora, fica vermelho se atrasar) e probabilidade de liberação
  (Fácil = amarelo, Médio = laranja, Difícil = vermelho).
- Filtros por status, modelo e estação; busca por VIN ou texto do reparo.
- **Importar lista** colando o texto do WhatsApp e **Compartilhar lista** no mesmo formato.

## Como os builds funcionam

Cada push dispara o GitHub Actions (`.github/workflows/android.yml`), que roda os testes e gera o APK.
Na branch principal ele também cria uma **Release** `v1.0.<número da execução>`, que é de onde o app
puxa as atualizações. O `versionCode` sobe a cada build e todos os APKs são assinados com a mesma chave,
por isso a atualização instala por cima da versão antiga.

### Chave de assinatura (obrigatória para publicar Releases)

A chave **não fica no repositório** (ele é público). Cadastre em
*Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o arquivo `.jks` em base64 |
| `KEYSTORE_PASSWORD` | senha da chave |
| `KEY_ALIAS` | `massivasfai` |
| `KEY_PASSWORD` | senha da chave |

Sem esses secrets o build ainda gera um APK de teste (artifact), mas não publica Release.
**Guarde a chave com segurança e nunca a troque**: com outra chave o Android recusa instalar por cima.
