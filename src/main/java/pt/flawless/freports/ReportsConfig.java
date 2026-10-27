package pt.flawless.freports;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/** Configuração do fReports: prazo do prompt e mensagens (com {@code &} já traduzido). */
public record ReportsConfig(int promptSeconds, Map<String, String> messages) {

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("players-only", "&cApenas jogadores."),
            Map.entry("error", "&cOcorreu um erro. Tenta mais tarde."),
            Map.entry("coming-soon", "&eEm breve."),
            Map.entry("usage-report", "&eUso: /report <jogador>"),
            Map.entry("usage-historico", "&eUso: /report historico <jogador>"),
            Map.entry("usage-bugreport", "&eUso: /bugreport <descrição>"),
            Map.entry("help-report", "&e/report <jogador> &7- Denuncia um jogador"),
            Map.entry("help-historico", "&e/report historico <jogador> &7- Mostra os jogadores que um jogador denunciou"),
            Map.entry("help-bugreport", "&e/bugreport <descrição> &7- Reporta um bug"),
            Map.entry("help-reports", "&e/reports &7- Abre o menu de reports"),
            Map.entry("help-help", "&e/report help &7- Mostra esta lista"),
            Map.entry("player-not-found", "&cJogador não encontrado."),
            Map.entry("report-self", "&cNão te podes denunciar a ti próprio."),
            Map.entry("prompt-start", "&eEscreve o motivo no chat ({n}s), &fsaltar &epara não pôr motivo ou &ccancelar &epara sair."),
            Map.entry("prompt-cancelled", "&7Report cancelado."),
            Map.entry("prompt-expired", "&7O tempo para o report acabou."),
            Map.entry("report-sent", "&aReport enviado."),
            Map.entry("bug-sent", "&aBug reportado, obrigado."),
            Map.entry("menu-category-title", "Denunciar {jogador}"),
            Map.entry("menu-category-lore", "&7Clica para denunciar por esta categoria"),
            Map.entry("category-HACKS", "&cHacks"),
            Map.entry("category-CHAT", "&eChat"),
            Map.entry("category-BUG_ABUSE", "&6Abuso de bugs"),
            Map.entry("category-GRIEF", "&4Grief"),
            Map.entry("category-SPAM", "&bSpam"),
            Map.entry("category-OTHER", "&7Outro"),
            Map.entry("menu-confirm-title", "Confirmar report?"),
            Map.entry("menu-confirm-subject", "&e{jogador}"),
            Map.entry("menu-confirm-category", "&7Categoria: &f{categoria}"),
            Map.entry("menu-confirm-reason", "&7Motivo: &f{motivo}"),
            Map.entry("menu-confirm-no-reason", "sem motivo"),
            Map.entry("menu-confirm-yes", "&aEnviar report"),
            Map.entry("menu-confirm-no", "&cCancelar"),
            Map.entry("menu-member-title", "Reports"),
            Map.entry("menu-hub-title", "Reports (staff)"),
            Map.entry("menu-report-player", "&eReportar jogador"),
            Map.entry("menu-analyze", "&eAnalisar reports"),
            Map.entry("menu-bugs", "&eBug reports"),
            Map.entry("menu-mine", "&eOs meus reports"),
            Map.entry("menu-info", "&eComo reportar"),
            Map.entry("menu-info-1", "&7/report <jogador> &8- &7denuncia um jogador"),
            Map.entry("menu-info-2", "&7/bugreport <descrição> &8- &7reporta um bug"),
            Map.entry("menu-picker-title", "Reportar jogador"),
            Map.entry("menu-picker-lore", "&7Clica para denunciar"),
            Map.entry("menu-mine-title", "Os meus reports"),
            Map.entry("menu-mine-category", "&7Categoria: &f{categoria}"),
            Map.entry("menu-mine-reason", "&7Motivo: &f{motivo}"),
            Map.entry("menu-mine-status", "&7Estado: &f{estado}"),
            Map.entry("menu-mine-verdict", "&7Veredito: &f{veredito}"),
            Map.entry("menu-mine-date", "&7Data: &f{data}"),
            Map.entry("menu-back", "&cVoltar"),
            Map.entry("menu-prev", "&ePágina anterior"),
            Map.entry("menu-next", "&ePágina seguinte"),
            Map.entry("status-OPEN", "&eAberto"),
            Map.entry("status-IN_REVIEW", "&6Em análise"),
            Map.entry("status-RESOLVED", "&aResolvido"),
            Map.entry("status-REJECTED", "&cRejeitado"),
            Map.entry("verdict-PUNISHED", "&aPunido"),
            Map.entry("verdict-NO_EVIDENCE", "&7Sem provas"),
            Map.entry("verdict-FALSE", "&cReport falso"));

    public static ReportsConfig from(ConfigurationSection c) {
        Map<String, String> messages = new HashMap<>();
        DEFAULT_MESSAGES.forEach((k, v) ->
                messages.put(k, ChatColor.translateAlternateColorCodes('&', c.getString("messages." + k, v))));
        return new ReportsConfig(Math.max(1, c.getInt("prompt-seconds", 15)), Map.copyOf(messages));
    }

    /** Mensagem já com cores; uma chave desconhecida devolve a própria chave. */
    public String msg(String key) {
        return messages.getOrDefault(key, key);
    }
}
