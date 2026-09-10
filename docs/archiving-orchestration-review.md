# Orquestração de arquivamento — revisão antes da exclusão em fast

Status: fluxo aprovado pelo usuário com `APROVADO: exclusão-fast-storage`, após esta revisão; implementação e testes sintéticos temporários concluídos. A mensagem está registrada em tasks.md. Referências: AGENTS.md, architecture.md, ADR-0001/0002/0003 e design/specs/tasks da mudança add-forensic-evidence-registration-and-local-archiving.

## Escopo autorizado e limites

Implementar a orquestração síncrona na camada de serviço, com uma única repetição global e estado ERRO na segunda falha. Inclui a cópia segura fast → work (item 5.1, após checkpoint), integração ZIP/cifra, conclusão em ARQUIVADO e retomada das etapas pendentes (itens 5.3/5.4), testes e documentação. Adaptar os serviços existentes somente no necessário para esse fluxo e preservar seus contratos já testados.

Ficam proibidas alterações fora desse escopo: telas/endpoints novos, cadastro/remoção de registros, desarquivamento, restauração/descriptografia, novas transições, hashes adicionais, limpeza automática do .dd de work, troca de stack, novas dependências, jobs/filas, autenticação, integrações externas, mudanças no hook ou nas regras Git. Não executar a operação em dados reais. Não fazer commit, push, merge, deploy ou Docker.

## Regras de negócio

1. Aceitar somente registro em EM_ANALISE com arquivo .dd regular e legível sob fast, sem symlinks ou escape de raiz. Recusar HASH_DIVERGENTE, ERRO e demais estados sem movimentação. Recusar o .zip.enc retornado de simulação, orientando novo cadastro de .dd. Identificador textual nunca será usado como diretório.
2. Confirmar EM_ANALISE → ARQUIVANDO no SQLite antes de movimentar arquivos. Impedir que duas chamadas processem simultaneamente o mesmo registro. A operação não deve manter uma transação de banco aberta durante o trabalho de arquivos.
3. Copiar por streaming para área própria da evidência/operação em work, sem sobrescrever destino desconhecido. Preservar parciais; uma nova tentativa de cópia usa destino novo pertencente à operação.
4. Considerar a cópia confirmada somente após leitura até EOF, escrita e fechamento de ambos os streams sem erro, número de bytes copiados igual ao tamanho observado na origem e tamanho do destino igual a esse tamanho. Revalidar a origem antes da remoção. Não recalcular hash; alterações concorrentes de mesmo tamanho continuam sendo uma limitação.
5. Confirmar no SQLite o path da cópia de trabalho antes de excluir exatamente o .dd original cadastrado em fast. A exclusão exige cópia confirmada nesta operação e raiz/path validados; existência isolada do destino nunca autoriza excluir. Falhas de cópia, fechamento, tamanho ou persistência preservam a origem.
6. Compactar a cópia confirmada. Somente ZIP concluído e fechado segue para a cifra. Uma falha preserva o .dd e o parcial; nova tentativa de ZIP usa artefato separado, sem aceitar parcial como completo ou sobrescrever artefato alheio.
7. Cifrar conforme ADR-0003, com IV novo quando houver nova execução da cifra, temporário separado e publicação somente após finalização GCM e fechamento. Preservar senha, chave, IV, salt, iterações e versão correspondentes à cifra concluída para eventual repetição da persistência na mesma chamada, sem logar segredos.
8. Confirmar metadados e path final no SQLite antes de excluir o ZIP aberto. Persistir ARQUIVADO somente após publicação, metadados confirmados e remoção do ZIP. Manter o .dd de work.
9. Executar no máximo duas passagens totais: tentativa inicial e uma retomada após falha operacional. O orçamento é global, não duas tentativas para cada etapa. A primeira falha mantém ARQUIVANDO; uma segunda falha, mesmo em outra etapa, encerra com ERRO e mensagem sem segredos, quando o banco estiver disponível.
10. Retomar da última etapa confirmada em memória na mesma chamada. Não repetir cópia após origem removida, nem cifra após publicação. Se somente a gravação de metadados, exclusão do ZIP ou transição final falhou, repetir somente o que ainda falta. Ausência de arquivo só conta como remoção concluída se essa conclusão foi registrada pela própria operação.
11. Falha ao persistir ERRO deve retornar erro explícito de persistência e preservar os artefatos; não afirmar que ERRO foi gravado. ERRO não tem transição de saída. A gravação do erro terminal não representa terceira tentativa de arquivamento.
12. Não implementar recuperação automática após queda de processo. Filesystem e SQLite não são atômicos; parciais e estados intermediários podem exigir avaliação humana.

## Fluxo e critério de exclusão submetidos à aprovação

EM_ANALISE → confirmar ARQUIVANDO → copiar fast para work → confirmar EOF/fechamentos/bytes/tamanho → confirmar path de work no SQLite → revalidar origem e excluir somente o .dd original de fast → ZIP → cifra finalizada/fechada → publicação .zip.enc → confirmar metadados → excluir ZIP → confirmar ARQUIVADO.

O contexto da operação registra separadamente os paths e as etapas confirmadas, sem criar estados adicionais na entidade. Esse contexto mantém o artefato cifrado e seus parâmetros até concluir a persistência, inclusive se o SQLite falhar depois da publicação. A implementação separou conclusão da cifra, publicação, persistência e limpeza em um contexto interno de CryptoService; os arrays de chave/senha são limpos quando a operação termina. A chamada pública isolada CryptoService.encrypt preserva seu contrato, recusando artefato já publicado e encerrando seu contexto ao retornar. Não houve alteração de algoritmo ou introdução de restauração.

Se a remoção de fast falhar, a retomada utiliza a cópia já confirmada e tenta a remoção pendente, sem recopiá-la. Se uma etapa posterior falhar depois da remoção bem-sucedida, a retomada usa work e não exige fast. Não haverá limpeza automática adicional dos arquivos parciais.

## Testes JUnit 5 exigidos para aceitar a implementação

Criar e executar os testes abaixo somente com arquivos sintéticos e SQLite em diretórios temporários. A execução concreta proposta inclui criar fast/work/archive dentro de `@TempDir`, copiar os .dd fictícios e excluir apenas suas origens temporárias após confirmação, além dos ZIPs temporários após cifra e commit. Não usar os storages ou o banco local do projeto. A autorização de execução desses testes deve acompanhar a aprovação do checkpoint; operações destrutivas sobre dados reais continuam fora do pedido.

| Cenário/falha injetada | Resultado e evidência exigidos |
|---|---|
| Fluxo completo sem falha | Ordem das etapas; ARQUIVADO confirmado; origem e ZIP ausentes; .dd de work preservado; .zip.enc e metadados correspondentes; nenhuma repetição |
| HASH_DIVERGENTE, ERRO e demais estados inelegíveis | Nenhuma operação de arquivo ou transição indevida |
| .zip.enc, path externo, symlink, diretório ou arquivo ausente | Rejeição sem exclusão; mensagem adequada; hashes inalterados |
| Duas chamadas para o mesmo registro | Apenas uma inicia o processamento; nenhuma duplicação/exclusão indevida |
| Falha ao confirmar ARQUIVANDO | Nenhuma movimentação antes de commit; limite global respeitado |
| Leitura/escrita incompleta na cópia | Origem intacta; parcial não libera exclusão; segunda tentativa em destino novo |
| Fechamento de entrada ou saída falha | Origem intacta, mesmo se tamanhos aparentarem igualdade |
| Tamanho divergente/origem alterada antes de excluir | Nenhuma exclusão; última cópia utilizável preservada |
| Colisão com artefato desconhecido | Conteúdo anterior preservado; nenhuma sobrescrita |
| Falha de commit do path de work | Fast preservado; repetir só persistência da cópia confirmada |
| Falha ao excluir fast | Cópia confirmada retida; retomada sem recópia; segunda falha leva a ERRO |
| ZIP falha após fast removido | Retomada usa .dd de work; nenhum acesso obrigatório à origem removida; parcial não é aceito |
| Cifra, finalização GCM ou fechamento falha | ZIP/.dd preservados; nova cifra usa IV e temporário novos |
| Publicação concluída, SQLite falha | Retomar persistência dos mesmos parâmetros, sem recifrar nem substituir o cifrado |
| Metadados confirmados, exclusão de ZIP falha | Repetir só limpeza pendente; chave/IV/cifrado preservados |
| ZIP removido, commit de ARQUIVADO falha | Repetir somente transição final; nenhuma cifra ou exclusão duplicada |
| Primeira falha recuperável | Sucesso na segunda passagem; nunca uma terceira |
| Duas falhas em etapas diferentes | ERRO na segunda falha; não reiniciar orçamento por etapa |
| Duas falhas na mesma etapa | ERRO e mensagem persistidos, com artefatos preservados |
| SQLite também impede gravar ERRO | Erro de persistência retornado; não afirmar estado não confirmado |
| Segredos e integridade | Nenhum hash posterior; chave/senha ausentes dos logs; testes de cifra sem descriptografia |

Executar também a suíte existente, preservando regressões de cadastro, estados, ZIP e cifra. Registrar comandos realmente executados, contagens e falhas no tasks.md; não marcar requisitos como concluídos apenas pela existência dos testes.

## Situação desta revisão

Fluxo aprovado e implementado exclusivamente na camada de serviço. Os 37 testes de ArchivingService e 12 de StorageCopyService passaram; a suíte completa teve 163 testes, sem falhas, erros ou ignorados. Cobertura inclui commit/ordem das exclusões, cópia parcial/fechamentos/tamanho, colisão, ZIP parcial após origem removida, IV novo, publicação sem recifra, falha SQLite real, falha ao registrar ERRO, concorrência e limite global de tentativas. Todos os arquivos/bancos utilizados eram sintéticos e temporários. Nenhum storage ou banco real foi usado.

Checklist: checkpoint registrado; regras e limites preservados; implementação concluída; testes executados; README/arquitetura/OpenSpec atualizados. Encerrado o recorte sem commit, push, merge ou deploy. A integração web e o desarquivamento continuam fora deste recorte. A aprovação de merge permanece pendente.
