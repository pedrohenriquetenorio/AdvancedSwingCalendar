package br.com.advancedswingcalendar;

import java.time.LocalDate;
import java.util.EventObject;

/**
 * Evento disparado quando a seleção de datas muda.
 */
public class AdvDateSelectionEvent extends EventObject {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public AdvDateSelectionEvent(Object source, LocalDate startDate, LocalDate endDate) {
        super(source);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Data selecionada (modo SINGLE) ou data inicial do período (modo RANGE). Pode ser null. */
    public LocalDate getStartDate() {
        return startDate;
    }

    /** Data final do período (modo RANGE). Null enquanto o período não foi concluído. */
    public LocalDate getEndDate() {
        return endDate;
    }

    /** true quando há uma data (SINGLE) ou um período completo (RANGE). */
    public boolean isComplete() {
        return startDate != null && (endDate != null || !(getSource() instanceof AdvCalendar c)
                || c.getAdvSelectionMode() == AdvSelectionMode.SINGLE);
    }
}
