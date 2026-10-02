package br.com.advancedswingcalendar;

import java.util.EventListener;

/**
 * Ouvinte de mudança de seleção de datas (aparece na aba "Events" do NetBeans).
 */
public interface AdvDateSelectionListener extends EventListener {

    void dateSelectionChanged(AdvDateSelectionEvent evt);
}
