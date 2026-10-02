package br.com.advancedswingcalendar;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.beans.BeanProperty;
import java.beans.JavaBean;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

/**
 * Campo de data: [ campo de texto ] [botão calendário] [botão limpar].
 * Permite digitar (máscara automática) ou escolher no calendário em popup.
 */
@JavaBean(description = "Campo de data com botão de calendário e botão limpar", defaultProperty = "advDatePattern")
public class AdvDatePicker extends JPanel {

    private final JTextField field = new JTextField(10);
    private final JButton btnCalendar = new SquareButton(new CalendarIcon());
    private final JButton btnClear = new SquareButton(new ClearIcon());
    private final JPopupMenu popup = new JPopupMenu();
    private final AdvCalendar calendar = new AdvCalendar();

    private LocalDate advDate;
    private String advDatePattern = "dd/MM/yyyy";
    private String advPlaceholder = "dd/mm/aaaa";
    private DateTimeFormatter formatter;
    private LocalDate advMinDate;
    private LocalDate advMaxDate;

    private boolean updatingText;
    private boolean syncingCalendar;
    private long popupHiddenAt;

    public AdvDatePicker() {
        setOpaque(false);
        setLayout(new BorderLayout(4, 0));

        JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
        buttons.setOpaque(false);
        buttons.add(btnCalendar);
        buttons.add(btnClear);
        add(field, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);

        btnCalendar.setToolTipText("Selecionar data");
        btnClear.setToolTipText("Limpar");

        // calendário em popup (1 mês, data única)
        calendar.setAdvSelectionMode(AdvSelectionMode.SINGLE);
        calendar.setAdvMonthsShown(1);
        popup.add(calendar);
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                popupHiddenAt = System.currentTimeMillis();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                popupHiddenAt = System.currentTimeMillis();
            }
        });
        calendar.addAdvDateSelectionListener(evt -> {
            if (syncingCalendar || evt.getStartDate() == null) {
                return;
            }
            applyDate(evt.getStartDate(), true);
            popup.setVisible(false);
            field.requestFocusInWindow();
        });

        btnCalendar.addActionListener(e -> togglePopup());
        btnClear.addActionListener(e -> {
            applyDate(null, true);
            field.requestFocusInWindow();
        });

        // máscara + leitura do texto digitado
        updateFormatter();
        installMask();
        field.putClientProperty("JTextField.placeholderText", advPlaceholder); // FlatLaf
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        field.addActionListener(e -> commitText());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitText();
            }
        });
    }

    // ------------------------------------------------------------------ popup

    private void togglePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        if (System.currentTimeMillis() - popupHiddenAt < 250) {
            return; // o clique que fechou o popup não deve reabri-lo
        }
        syncingCalendar = true;
        try {
            calendar.setAdvSelectedDate(advDate);
            calendar.showMonth(advDate != null ? YearMonth.from(advDate) : YearMonth.now());
        } finally {
            syncingCalendar = false;
        }
        popup.show(this, 0, getHeight() + 2);
    }

    // ------------------------------------------------------------------ texto e máscara

    private void updateFormatter() {
        formatter = DateTimeFormatter.ofPattern(advDatePattern.replace('y', 'u'))
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private String mask() {
        StringBuilder sb = new StringBuilder();
        for (char c : advDatePattern.toCharArray()) {
            sb.append(Character.isLetter(c) ? '#' : c);
        }
        return sb.toString();
    }

    private int digitSlots() {
        int n = 0;
        for (char c : mask().toCharArray()) {
            if (c == '#') {
                n++;
            }
        }
        return n;
    }

    private String applyMask(String digits) {
        String mask = mask();
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (k >= digits.length()) {
                break;
            }
            char m = mask.charAt(i);
            sb.append(m == '#' ? digits.charAt(k++) : m);
        }
        return sb.toString();
    }

    private int positionAfterDigits(int n) {
        if (n <= 0) {
            return 0;
        }
        String mask = mask();
        int count = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (mask.charAt(i) == '#' && ++count == n) {
                return i + 1;
            }
        }
        return mask.length();
    }

    private void installMask() {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException {
                replace(fb, off, 0, s, a);
            }

            @Override
            public void remove(FilterBypass fb, int off, int len) throws BadLocationException {
                replace(fb, off, len, "", null);
            }

            @Override
            public void replace(FilterBypass fb, int off, int len, String text, AttributeSet a)
                    throws BadLocationException {
                Document doc = fb.getDocument();
                String cur = doc.getText(0, doc.getLength());
                String ins = text == null ? "" : text;
                String beforeCaret = cur.substring(0, off) + ins;
                String raw = beforeCaret + cur.substring(off + len);

                int digitsBefore = beforeCaret.replaceAll("\\D", "").length();
                String digits = raw.replaceAll("\\D", "");
                if (digits.length() > digitSlots()) {
                    digits = digits.substring(0, digitSlots());
                }
                fb.replace(0, doc.getLength(), applyMask(digits), a);
                field.setCaretPosition(positionAfterDigits(Math.min(digitsBefore, digits.length())));
            }
        });
    }

    private void textEdited() {
        if (updatingText) {
            return;
        }
        String text = field.getText();
        if (text.isEmpty()) {
            setError(false);
            applyDate(null, false);
            return;
        }
        if (text.replaceAll("\\D", "").length() == digitSlots()) {
            LocalDate d = parse(text);
            if (d != null) {
                setError(false);
                applyDate(d, false);
            } else {
                setError(true);
            }
        } else {
            setError(false);
        }
    }

    private void commitText() {
        if (updatingText) {
            return;
        }
        String text = field.getText();
        if (text.isEmpty()) {
            applyDate(null, true);
            return;
        }
        LocalDate d = parse(text);
        if (d != null) {
            applyDate(d, true);
        } else {
            writeText(advDate); // volta para o último valor válido
            setError(false);
        }
    }

    private LocalDate parse(String text) {
        try {
            LocalDate d = LocalDate.parse(text, formatter);
            return isAllowed(d) ? d : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isAllowed(LocalDate d) {
        return (advMinDate == null || !d.isBefore(advMinDate)) && (advMaxDate == null || !d.isAfter(advMaxDate));
    }

    private void setError(boolean error) {
        field.putClientProperty("JComponent.outline", error ? "error" : null); // FlatLaf
        field.repaint();
    }

    private void writeText(LocalDate d) {
        updatingText = true;
        try {
            field.setText(d == null ? "" : formatter.format(d));
        } finally {
            updatingText = false;
        }
    }

    private void applyDate(LocalDate d, boolean updateText) {
        LocalDate old = advDate;
        advDate = d;
        if (updateText) {
            writeText(d);
            setError(false);
        }
        if (!Objects.equals(old, d)) {
            firePropertyChange("advDate", old, d);
            AdvDateSelectionEvent evt = new AdvDateSelectionEvent(this, d, null);
            for (AdvDateSelectionListener l : listenerList.getListeners(AdvDateSelectionListener.class)) {
                l.dateSelectionChanged(evt);
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    public void addAdvDateSelectionListener(AdvDateSelectionListener l) {
        listenerList.add(AdvDateSelectionListener.class, l);
    }

    public void removeAdvDateSelectionListener(AdvDateSelectionListener l) {
        listenerList.remove(AdvDateSelectionListener.class, l);
    }

    // ------------------------------------------------------------------ propriedades

    @BeanProperty(hidden = true)
    public LocalDate getAdvDate() {
        return advDate;
    }

    public void setAdvDate(LocalDate date) {
        if (date != null && !isAllowed(date)) {
            throw new IllegalArgumentException("Data fora do intervalo permitido: " + date);
        }
        applyDate(date, true);
    }

    /** Padrão da data. Use campos com zeros à esquerda, ex.: dd/MM/yyyy, yyyy-MM-dd, dd-MM-yyyy. */
    public String getAdvDatePattern() {
        return advDatePattern;
    }

    public void setAdvDatePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        String old = advDatePattern;
        advDatePattern = pattern;
        updateFormatter();
        writeText(advDate);
        firePropertyChange("advDatePattern", old, advDatePattern);
    }

    public String getAdvPlaceholder() {
        return advPlaceholder;
    }

    public void setAdvPlaceholder(String placeholder) {
        String old = advPlaceholder;
        advPlaceholder = placeholder;
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.repaint();
        firePropertyChange("advPlaceholder", old, placeholder);
    }

    public boolean isAdvShowClearButton() {
        return btnClear.isVisible();
    }

    public void setAdvShowClearButton(boolean show) {
        boolean old = btnClear.isVisible();
        btnClear.setVisible(show);
        revalidate();
        firePropertyChange("advShowClearButton", old, show);
    }

    public boolean isAdvEditable() {
        return field.isEditable();
    }

    public void setAdvEditable(boolean editable) {
        boolean old = field.isEditable();
        field.setEditable(editable);
        firePropertyChange("advEditable", old, editable);
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvMinDate() {
        return advMinDate;
    }

    public void setAdvMinDate(LocalDate d) {
        advMinDate = d;
        calendar.setAdvMinDate(d);
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvMaxDate() {
        return advMaxDate;
    }

    public void setAdvMaxDate(LocalDate d) {
        advMaxDate = d;
        calendar.setAdvMaxDate(d);
    }

    public DayOfWeek getAdvFirstDayOfWeek() {
        return calendar.getAdvFirstDayOfWeek();
    }

    public void setAdvFirstDayOfWeek(DayOfWeek d) {
        calendar.setAdvFirstDayOfWeek(d);
    }

    @BeanProperty(hidden = true)
    public Locale getAdvLocale() {
        return calendar.getAdvLocale();
    }

    public void setAdvLocale(Locale locale) {
        calendar.setAdvLocale(locale);
    }

    /** Acesso ao campo de texto interno (foco, tamanho de fonte etc.). */
    @BeanProperty(hidden = true)
    public JTextField getAdvTextField() {
        return field;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
        btnCalendar.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    @Override
    public boolean requestFocusInWindow() {
        return field.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ botões e ícones

    /** Botão quadrado com a altura do campo de texto. */
    private final class SquareButton extends JButton {

        SquareButton(Icon icon) {
            super(icon);
            setFocusable(false);
            setMargin(new Insets(0, 0, 0, 0));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            int s = Math.max(d.height, field.getPreferredSize().height);
            return new Dimension(s, s);
        }
    }

    private static final class CalendarIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.isEnabled() ? c.getForeground() : java.awt.Color.GRAY);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new RoundRectangle2D.Double(x + 1.5, y + 3, 13, 11.5, 3, 3));
            g.drawLine(x + 1, y + 7, x + 15, y + 7);
            g.drawLine(x + 5, y + 1, x + 5, y + 4);
            g.drawLine(x + 11, y + 1, x + 11, y + 4);
            g.fillOval(x + 4, y + 9, 2, 2);
            g.fillOval(x + 7, y + 9, 2, 2);
            g.fillOval(x + 10, y + 9, 2, 2);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    private static final class ClearIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.isEnabled() ? c.getForeground() : java.awt.Color.GRAY);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + 4, y + 4, x + 12, y + 12);
            g.drawLine(x + 12, y + 4, x + 4, y + 12);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
