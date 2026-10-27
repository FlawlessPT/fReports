# TODO fReports

## Adiado (sugerido, fora da v1)

- Snapshot do chat do alvo no report (últimas N mensagens). Exige API nova no fChat (buffer de mensagens recentes); guardar em `freports_chat`, uma linha por mensagem, ligada ao report.
- Limites anti-spam (cooldown, limite diário, bloqueio de duplicados por alvo e categoria): o utilizador não os quer; rever se houver abuso.
- Penalização de falsos reports (aviso ao autor e bloqueio temporário de reportar acima de um limite). Hoje o veredito "falso" só fica registado.
- Histórico dos reports feitos CONTRA um jogador (`/report historico` hoje só mostra os que ele fez), para apanhar reincidentes.
- Guardar posição e mundo no report, para a staff ir ao local (útil em grief).
- Estatísticas de staff (reports resolvidos por pessoa).
- Webhook para o Discord.
- Ligação a um futuro plugin de punições (fPunish): o veredito "punido" passaria a aplicar o castigo.
- Dashboard externo sobre as tabelas `freports_*` (os dados já ficam todos na BD, em colunas simples).
