package pt.flawless.freports.model;

import java.util.UUID;

/** Média e número de notas de um membro da staff. */
public record StaffRating(UUID staffUuid, String staffName, double average, int votes) {
}
